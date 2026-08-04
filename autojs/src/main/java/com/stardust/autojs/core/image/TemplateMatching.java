package com.stardust.autojs.core.image;

import android.util.Log;
import android.util.TimingLogger;

import androidx.annotation.NonNull;

import com.stardust.util.Nath;

import org.opencv.core.Core;
import org.opencv.core.CvType;

import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;


/**
 * Created by Stardust on 2017/11/25.
 */

public class TemplateMatching {

    public static class Match {
        public final Point point;
        public final double similarity;
        public Integer templateIndex = 0;

        public Match(Point point, double similarity) {
            this.point = point;
            this.similarity = similarity;
        }

        @NonNull
        @Override
        public String toString() {
            return "Match{" +
                    "templateIndex=" + templateIndex +
                    ", point=" + point +
                    ", similarity=" + similarity +
                    '}';
        }
    }

    private static final String LOG_TAG = "TemplateMatching";

    public static final int MAX_LEVEL_AUTO = -1;
    public static final int MATCHING_METHOD_DEFAULT = Imgproc.TM_CCOEFF_NORMED;

    public static Point fastTemplateMatching(Mat img, Mat template, int matchMethod, float weakThreshold, float strictThreshold, int maxLevel, Boolean transparentMask) {
        List<Match> result = fastTemplateMatching(img, template, matchMethod, weakThreshold, strictThreshold, maxLevel, 1, transparentMask);
        if (result.isEmpty()) {
            return null;
        }
        return result.get(0).point;
    }

    /**
     * 采用图像金字塔算法快速找图
     *
     * @param img             图片
     * @param template        模板图片
     * @param matchMethod     匹配算法
     * @param weakThreshold   弱阈值。该值用于在每一轮模板匹配中检验是否继续匹配。如果相似度小于该值，则不再继续匹配。
     * @param strictThreshold 强阈值。该值用于检验最终匹配结果，以及在每一轮匹配中如果相似度大于该值则直接返回匹配结果。
     * @param maxLevel        图像金字塔的层数
     * @return
     */
    public static List<Match> fastTemplateMatching(Mat img, Mat template, int matchMethod, float weakThreshold, float strictThreshold, int maxLevel, int limit, Boolean transparentMask) {
        List<Mat> templates = Collections.singletonList(template);
        return fastMultiTemplateMatching(img, templates, matchMethod, weakThreshold,
                strictThreshold, maxLevel, limit, transparentMask);
    }

    /**
     * 多模板匹配（高效版）
     * 传入一张主图和多个模板，内部共享主图金字塔，一次完成所有模板的匹配。
     *
     * @param img             主图
     * @param templates       模板列表
     * @param matchMethod     匹配算法
     * @param weakThreshold   弱阈值
     * @param strictThreshold 强阈值
     * @param maxLevel        金字塔层数（设为 MAX_LEVEL_AUTO 自动计算）
     * @param limit           每层最多返回的候选点数量（控制计算量）
     * @param transparentMask 是否启用透明蒙版
     * @return 按模板顺序返回匹配结果列表；若某模板无匹配，则对应列表为空
     */
    public static List<Match> fastMultiTemplateMatching(Mat img,
                                                        List<Mat> templates,
                                                        int matchMethod,
                                                        float weakThreshold,
                                                        float strictThreshold,
                                                        int maxLevel,
                                                        int limit,
                                                        Boolean transparentMask) {
        TimingLogger logger = new TimingLogger(LOG_TAG, "fast_multi_tm");
        List<Mat> resourcesToRelease = new ArrayList<>();

        try {
            // 确定全局最大金字塔层数（取所有模板允许层数的最大值）
            int globalMaxLevel;
            if (maxLevel == MAX_LEVEL_AUTO) {
                globalMaxLevel = 0;
                for (Mat template : templates) {
                    int level = selectPyramidLevel(img, template);
                    if (level > globalMaxLevel) globalMaxLevel = level;
                }
            } else {
                globalMaxLevel = maxLevel;
            }

            // 构建主图金字塔（0 ~ globalMaxLevel）
            List<Mat> imgPyramid = new ArrayList<>();
            for (int level = 0; level <= globalMaxLevel; level++) {
                Mat src = getPyramidDownAtLevel(img, level);
                imgPyramid.add(src);
                if (src != img) {
                    resourcesToRelease.add(src);
                }
            }

            List<Match> allResults = new ArrayList<>();
            for (int i = 0; i < templates.size(); i++) {
                List<Match> result = matchTemplateWithPyramid(imgPyramid,
                        templates.get(i),
                        globalMaxLevel,
                        matchMethod,
                        weakThreshold,
                        strictThreshold,
                        limit,
                        transparentMask,
                        resourcesToRelease);
                for (Match r : result) {
                    r.templateIndex = i;
                }
                allResults.addAll(result);
            }

            logger.addSplit("multi_templates: " + templates.size());
            logger.dumpToLog();
            return allResults;
        } finally {
            for (Mat mat : resourcesToRelease) {
                try {
                    mat.release();
                } catch (Exception e) {
                    Log.e(LOG_TAG, "Error releasing Mat resource", e);
                }
            }
        }
    }

    /**
     * 利用已有的主图金字塔对单个模板进行匹配
     *
     * @param imgPyramid         主图金字塔（0 为原图）
     * @param template           单个模板原图
     * @param globalMaxLevel     全局最大层数（用于限制模板自身层数）
     * @param matchMethod        匹配算法
     * @param weakThreshold      弱阈值
     * @param strictThreshold    强阈值
     * @param limit              每层最大候选数
     * @param transparentMask    是否处理透明通道
     * @param resourcesToRelease 资源回收列表
     * @return 匹配结果（坐标已映射回原图）
     */
    private static List<Match> matchTemplateWithPyramid(List<Mat> imgPyramid,
                                                        Mat template,
                                                        int globalMaxLevel,
                                                        int matchMethod,
                                                        float weakThreshold,
                                                        float strictThreshold,
                                                        int limit,
                                                        Boolean transparentMask,
                                                        List<Mat> resourcesToRelease) {
        // 计算模板自身的最大可行层数
        int tMaxLevel = selectPyramidLevel(imgPyramid.get(0), template);
        tMaxLevel = Math.min(tMaxLevel, globalMaxLevel);

        // 构建模板金字塔
        List<Mat> templatePyramid = new ArrayList<>();
        for (int level = 0; level <= tMaxLevel; level++) {
            Mat t = getPyramidDownAtLevel(template, level);
            templatePyramid.add(t);
            if (t != template) {
                resourcesToRelease.add(t);
            }
        }

        List<Match> finalMatchResult = new ArrayList<>();
        List<Match> previousMatchResult = Collections.emptyList();
        boolean isFirstMatching = true;

        for (int level = tMaxLevel; level >= 0; level--) {
            List<Match> currentMatchResult = new ArrayList<>();
            Mat src = imgPyramid.get(level);
            Mat currentTemplate = templatePyramid.get(level);
            Mat currentMask = null;

            if (transparentMask) {
                currentMask = TemplateMatchingKt.INSTANCE.processingAlphaChannel(currentTemplate);
                resourcesToRelease.add(currentMask);
            }

            if (previousMatchResult.isEmpty()) {
                // 决定是否继续匹配（根据弱阈值跳过部分层）
                if (!isFirstMatching && !shouldContinueMatching(level, tMaxLevel)) {
                    break;
                }
                // 全图匹配
                Mat matchResult = matchTemplate(src, currentTemplate, matchMethod, currentMask);
                resourcesToRelease.add(matchResult);
                getBestMatched(matchResult, currentTemplate, matchMethod, weakThreshold,
                        currentMatchResult, limit, null);
            } else {
                // 在上一层的匹配点附近进行局部匹配
                for (Match match : previousMatchResult) {
                    Rect r = getROI(match.point, src, currentTemplate);
                    Mat m = new Mat(src, r);
                    Mat matchResult = matchTemplate(m, currentTemplate, matchMethod, currentMask);
                    resourcesToRelease.add(m);
                    resourcesToRelease.add(matchResult);
                    getBestMatched(matchResult, currentTemplate, matchMethod, weakThreshold,
                            currentMatchResult, limit, r);
                }
            }

            // 强阈值处理：达到 strictThreshold 的结果直接加入最终列表，并停止该模板后续匹配
            if (!currentMatchResult.isEmpty()) {
                Iterator<Match> iterator = currentMatchResult.iterator();
                while (iterator.hasNext()) {
                    Match match = iterator.next();
                    if (match.similarity >= strictThreshold) {
                        pyrUp(match.point, level);    // 坐标还原至原图尺寸
                        finalMatchResult.add(match);
                        iterator.remove();
                    }
                }
                if (currentMatchResult.isEmpty()) {
                    break;  // 所有候选都满足强阈值，提前终止
                }
            }

            isFirstMatching = false;
            previousMatchResult = currentMatchResult;
        }

        return finalMatchResult;
    }

    private static Mat getPyramidDownAtLevel(Mat m, int level) {
        if (level == 0) {
            return m;
        }
        int cols = m.cols();
        int rows = m.rows();
        for (int i = 0; i < level; i++) {
            cols = (cols + 1) / 2;
            rows = (rows + 1) / 2;
        }
        Mat r = new Mat(rows, cols, m.type());
        Imgproc.resize(m, r, new Size(cols, rows));
        return r;
    }

    private static void pyrUp(Point p, int level) {
        for (int i = 0; i < level; i++) {
            p.x *= 2;
            p.y *= 2;
        }
    }

    private static boolean shouldContinueMatching(int level, int maxLevel) {
        if (level == maxLevel && level != 0) {
            return true;
        }
        if (maxLevel <= 2) {
            return false;
        }
        return level == maxLevel - 1;
    }

    private static Rect getROI(Point p, Mat src, Mat currentTemplate) {
        int x = (int) (p.x * 2 - currentTemplate.cols() / 4);
        x = Math.max(0, x);
        int y = (int) (p.y * 2 - currentTemplate.rows() / 4);
        y = Math.max(0, y);
        int w = (int) (currentTemplate.cols() * 1.5);
        int h = (int) (currentTemplate.rows() * 1.5);
        if (x + w >= src.cols()) {
            w = src.cols() - x - 1;
        }
        if (y + h >= src.rows()) {
            h = src.rows() - y - 1;
        }
        return new Rect(x, y, w, h);
    }

    private static int selectPyramidLevel(Mat img, Mat template) {
        int minDim = Nath.min(img.rows(), img.cols(), template.rows(), template.cols());
        //这里选取16为图像缩小后的最小宽高，从而用log(2, minDim / 16)得到最多可以经过几次缩小。
        int maxLevel = (int) (Math.log(minDim / 16) / Math.log(2));
        if (maxLevel < 0) {
            return 0;
        }
        //上限为6
        return Math.min(6, maxLevel);
    }


    private static Mat matchTemplate(Mat img, Mat temp, int match_method, Mat mask) {
        int result_cols = img.cols() - temp.cols() + 1;
        int result_rows = img.rows() - temp.rows() + 1;
        Mat result = new Mat(result_rows, result_cols, CvType.CV_32FC1);
        if (mask == null) {
            Imgproc.matchTemplate(img, temp, result, match_method);
        } else {
            Imgproc.matchTemplate(img, temp, result, match_method, mask);
        }
        return result;
    }

    private static void getBestMatched(Mat tmResult, Mat template, int matchMethod, float weakThreshold, List<Match> outResult, int limit, Rect rect) {
        for (int i = 0; i < limit; i++) {
            Match bestMatched = getBestMatched(tmResult, matchMethod, weakThreshold, rect);
            if (bestMatched == null) {
                break;
            }
            outResult.add(bestMatched);
            Point start = new Point(Math.max(0, bestMatched.point.x - template.width() + 1),
                    Math.max(0, bestMatched.point.y - template.height() + 1));
            Point end = new Point(Math.min(tmResult.width(), bestMatched.point.x + template.width()),
                    Math.min(tmResult.height(), bestMatched.point.y + template.height()));
            Imgproc.rectangle(tmResult, start, end, new Scalar(0, 255, 0), -1);
        }
    }

    private static Match getBestMatched(Mat tmResult, int matchMethod, float weakThreshold, Rect rect) {
        TimingLogger logger = new TimingLogger(LOG_TAG, "best_matched_point");
        Core.MinMaxLocResult mmr = Core.minMaxLoc(tmResult);
        logger.addSplit("minMaxLoc");
        double value;
        Point pos;
        if (matchMethod == Imgproc.TM_SQDIFF || matchMethod == Imgproc.TM_SQDIFF_NORMED) {
            pos = mmr.minLoc;
            value = -mmr.minVal;
        } else {
            pos = mmr.maxLoc;
            value = mmr.maxVal;
        }
        if (value < weakThreshold) {
            return null;
        }
        if (rect != null) {
            pos.x += rect.x;
            pos.y += rect.y;
        }
        logger.addSplit("value:" + value);
        if (!Double.isFinite(value)) {
            return getBestMatched(replaceNoFinite(tmResult), matchMethod, weakThreshold, rect);
        }
        return new Match(pos, value);
    }


    private static Mat replaceNoFinite(Mat mat) {
        if (mat.empty()) return null;
        if (mat.type() == CvType.CV_32FC1) {
            Core.patchNaNs(mat, 0.0);
            Mat infMask = new Mat();
            Core.compare(mat, new Scalar(255), infMask, Core.CMP_GT); // mat > threshold -> 255
            Mat zeros = Mat.zeros(mat.size(), mat.type());
            zeros.copyTo(mat, infMask);
            infMask.release();
            zeros.release();
        }
        return mat;
    }

}
