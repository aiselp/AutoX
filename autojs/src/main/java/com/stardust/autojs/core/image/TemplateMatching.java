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

        public Match(Point point, double similarity) {
            this.point = point;
            this.similarity = similarity;
        }

        @NonNull
        @Override
        public String toString() {
            return "Match{" +
                    "point=" + point +
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
        TimingLogger logger = new TimingLogger(LOG_TAG, "fast_tm");
        // 创建资源回收列表
        List<Mat> resourcesToRelease = new ArrayList<>();

        try {
            if (maxLevel == MAX_LEVEL_AUTO) {
                maxLevel = selectPyramidLevel(img, template);
                logger.addSplit("selectPyramidLevel:" + maxLevel);
            }
            List<Match> finalMatchResult = new ArrayList<>();
            List<Match> previousMatchResult = Collections.emptyList();
            boolean isFirstMatching = true;
            for (int level = maxLevel; level >= 0; level--) {
                List<Match> currentMatchResult = new ArrayList<>();
                Mat src = getPyramidDownAtLevel(img, level);
                Mat currentTemplate = getPyramidDownAtLevel(template, level);
                Mat currentMask = null;

                if (transparentMask) {
                    currentMask = TemplateMatchingKt.INSTANCE.processingAlphaChannel(currentTemplate);
                }

                // +++ 添加到释放列表 +++
                if (src != img) {
                    resourcesToRelease.add(src);
                }
                if (currentTemplate != template) {
                    resourcesToRelease.add(currentTemplate);
                }
                if (currentMask != null) {
                    resourcesToRelease.add(currentMask);
                }

                // 如果在上一轮中没有匹配到图片，则考虑是否退出匹配
                if (previousMatchResult.isEmpty()) {
                    // 如果不是第一次匹配，并且不满足shouldContinueMatching的条件，则直接退出匹配
                    if (!isFirstMatching && !shouldContinueMatching(level, maxLevel)) {
                        break;
                    }
                    Mat matchResult = matchTemplate(src, currentTemplate, matchMethod, currentMask);
                    resourcesToRelease.add(matchResult);
                    getBestMatched(matchResult, currentTemplate, matchMethod, weakThreshold, currentMatchResult, limit, null);
                } else {
                    for (Match match : previousMatchResult) {
                        Rect r = getROI(match.point, src, currentTemplate);
                        Mat m = new Mat(src, r);
                        Mat matchResult = matchTemplate(m, currentTemplate, matchMethod, currentMask);

                        // +++ 添加到释放列表 +++
                        resourcesToRelease.add(m);
                        resourcesToRelease.add(matchResult);

                        getBestMatched(matchResult, currentTemplate, matchMethod, weakThreshold, currentMatchResult, limit, r);
                    }
                }

                logger.addSplit("level:" + level + ", result:" + previousMatchResult);

                // 把满足强阈值的点找出来，加到最终结果列表
                if (!currentMatchResult.isEmpty()) {
                    Iterator<Match> iterator = currentMatchResult.iterator();
                    while (iterator.hasNext()) {
                        Match match = iterator.next();
                        if (match.similarity >= strictThreshold) {
                            pyrUp(match.point, level);
                            finalMatchResult.add(match);
                            iterator.remove();
                        }
                    }
                    // 如果所有结果都满足强阈值，则退出循环，返回最终结果
                    if (currentMatchResult.isEmpty()) {
                        break;
                    }
                }
                isFirstMatching = false;
                previousMatchResult = currentMatchResult;
            }
            logger.addSplit("result:" + finalMatchResult);
            logger.dumpToLog();
            return finalMatchResult;
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
        if(!Double.isFinite(value)){
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
