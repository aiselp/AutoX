// Tencent is pleased to support the open source community by making ncnn available.
//
// Copyright (C) 2025 THL A29 Limited, a Tencent company. All rights reserved.
//
// Licensed under the BSD 3-Clause License (the "License"); you may not use this file except
// in compliance with the License. You may obtain a copy of the License at
//
// https://opensource.org/licenses/BSD-3-Clause
//
// Unless required by applicable law or agreed to in writing, software distributed
// under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
// CONDITIONS OF ANY KIND, either express or implied. See the License for the
// specific language governing permissions and limitations under the License.

#include "ppocrv5.h"

#include "cpu.h"
#include "net.h"

#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp>

#include "ppocrv5_dict.h"


static double contour_score(const cv::Mat& binary, const std::vector<cv::Point>& contour)
{
    cv::Rect rect = cv::boundingRect(contour);
    if (rect.x < 0)
        rect.x = 0;
    if (rect.y < 0)
        rect.y = 0;
    if (rect.x + rect.width > binary.cols)
        rect.width = binary.cols - rect.x;
    if (rect.y + rect.height > binary.rows)
        rect.height = binary.rows - rect.y;

    cv::Mat binROI = binary(rect);

    cv::Mat mask = cv::Mat::zeros(rect.height, rect.width, CV_8U);
    std::vector<cv::Point> roiContour;
    for (size_t i = 0; i < contour.size(); i++)
    {
        cv::Point pt = cv::Point(contour[i].x - rect.x, contour[i].y - rect.y);
        roiContour.push_back(pt);
    }

    std::vector<std::vector<cv::Point> > roiContours = {roiContour};
    cv::fillPoly(mask, roiContours, cv::Scalar(255));

    double score = cv::mean(binROI, mask).val[0];
    return score / 255.f;
}

static cv::Mat get_rotate_crop_image(const cv::Mat& rgb, const Object& object)
{
    const int orientation = object.orientation;
    const float rw = object.rrect.size.width;
    const float rh = object.rrect.size.height;

    const int target_height = 48;
    const float target_width = rh * target_height / rw;

    // warpperspective shall be used to rotate the image
    // but actually they are all rectangles, so warpaffine is almost enough  :P

    cv::Mat dst;

    cv::Point2f corners[4];
    object.rrect.points(corners);

    if (orientation == 0)
    {
        // horizontal text
        // corner points order
        //  0--------1
        //  |        |rw  -> as angle=90
        //  3--------2
        //      rh

        std::vector<cv::Point2f> src_pts(3);
        src_pts[0] = corners[0];
        src_pts[1] = corners[1];
        src_pts[2] = corners[3];

        std::vector<cv::Point2f> dst_pts(3);
        dst_pts[0] = cv::Point2f(0, 0);
        dst_pts[1] = cv::Point2f(target_width, 0);
        dst_pts[2] = cv::Point2f(0, target_height);

        cv::Mat tm = cv::getAffineTransform(src_pts, dst_pts);

        cv::warpAffine(rgb, dst, tm, cv::Size(target_width, target_height), cv::INTER_LINEAR, cv::BORDER_REPLICATE);
    }
    else
    {
        // vertial text
        // corner points order
        //  1----2
        //  |    |
        //  |    |
        //  |    |rh  -> as angle=0
        //  |    |
        //  |    |
        //  0----3
        //    rw

        std::vector<cv::Point2f> src_pts(3);
        src_pts[0] = corners[2];
        src_pts[1] = corners[3];
        src_pts[2] = corners[1];

        std::vector<cv::Point2f> dst_pts(3);
        dst_pts[0] = cv::Point2f(0, 0);
        dst_pts[1] = cv::Point2f(target_width, 0);
        dst_pts[2] = cv::Point2f(0, target_height);

        cv::Mat tm = cv::getAffineTransform(src_pts, dst_pts);

        cv::warpAffine(rgb, dst, tm, cv::Size(target_width, target_height), cv::INTER_LINEAR, cv::BORDER_REPLICATE);
    }

    return dst;
}

PPOCRv5::PPOCRv5()
{
    target_size = 640;
}

PPOCRv5::~PPOCRv5()
{
}

int PPOCRv5::load(const char* det_parampath, const char* det_modelpath, const char* rec_parampath, const char* rec_modelpath, bool use_fp16, bool use_gpu)
{
    ppocrv5_det.clear();
    ppocrv5_rec.clear();

    ppocrv5_det.opt.use_fp16_packed = use_fp16;
    ppocrv5_det.opt.use_fp16_storage = use_fp16;
    ppocrv5_det.opt.use_fp16_arithmetic = use_fp16;

#if NCNN_VULKAN
    ppocrv5_det.opt.use_vulkan_compute = use_gpu;
#endif

    ppocrv5_det.load_param(det_parampath);
    ppocrv5_det.load_model(det_modelpath);

    // default to 1 thread, as we rec multiple lines in parallel
    ppocrv5_rec.opt.num_threads = 1;

    ppocrv5_rec.opt.use_fp16_packed = use_fp16;
    ppocrv5_rec.opt.use_fp16_storage = use_fp16;
    ppocrv5_rec.opt.use_fp16_arithmetic = use_fp16;

#if NCNN_VULKAN
    ppocrv5_rec.opt.use_vulkan_compute = use_gpu;
#endif

    ppocrv5_rec.load_param(rec_parampath);
    ppocrv5_rec.load_model(rec_modelpath);

    return 0;
}

int PPOCRv5::load(AAssetManager* mgr, const char* det_parampath, const char* det_modelpath, const char* rec_parampath, const char* rec_modelpath, bool use_fp16, bool use_gpu)
{
    ppocrv5_det.clear();
    ppocrv5_rec.clear();

    ppocrv5_det.opt.use_fp16_packed = use_fp16;
    ppocrv5_det.opt.use_fp16_storage = use_fp16;
    ppocrv5_det.opt.use_fp16_arithmetic = use_fp16;

#if NCNN_VULKAN
    ppocrv5_det.opt.use_vulkan_compute = use_gpu;
#endif

    ppocrv5_det.load_param(mgr, det_parampath);
    ppocrv5_det.load_model(mgr, det_modelpath);

    // default to 1 thread, as we rec multiple lines in parallel
    ppocrv5_rec.opt.num_threads = 1;

    ppocrv5_rec.opt.use_fp16_packed = use_fp16;
    ppocrv5_rec.opt.use_fp16_storage = use_fp16;
    ppocrv5_rec.opt.use_fp16_arithmetic = use_fp16;

#if NCNN_VULKAN
    ppocrv5_rec.opt.use_vulkan_compute = use_gpu;
#endif

    ppocrv5_rec.load_param(mgr, rec_parampath);
    ppocrv5_rec.load_model(mgr, rec_modelpath);

    return 0;
}

void PPOCRv5::set_target_size(int _target_size)
{
    target_size = _target_size;
}

int PPOCRv5::detect(const cv::Mat& rgb, std::vector<Object>& objects)
{
    cv::setNumThreads(ncnn::get_big_cpu_count());

    int img_w = rgb.cols;
    int img_h = rgb.rows;

    const int target_stride = 32;

    // letterbox pad to multiple of target_stride
    int w = img_w;
    int h = img_h;
    float scale = 1.f;
    if (std::max(w, h) > target_size)
    {
        if (w > h)
        {
            scale = (float)target_size / w;
            w = target_size;
            h = h * scale;
        }
        else
        {
            scale = (float)target_size / h;
            h = target_size;
            w = w * scale;
        }
    }

    ncnn::Mat in = ncnn::Mat::from_pixels_resize(rgb.data, ncnn::Mat::PIXEL_RGB2BGR, img_w, img_h, w, h);

    int wpad = (w + target_stride - 1) / target_stride * target_stride - w;
    int hpad = (h + target_stride - 1) / target_stride * target_stride - h;
    ncnn::Mat in_pad;
    ncnn::copy_make_border(in, in_pad, hpad / 2, hpad - hpad / 2, wpad / 2, wpad - wpad / 2, ncnn::BORDER_CONSTANT, 114.f);

    const float mean_vals[3] = {0.485f * 255.f, 0.456f * 255.f, 0.406f * 255.f};
    const float norm_vals[3] = {1 / 0.229f / 255.f, 1 / 0.224f / 255.f, 1 / 0.225f / 255.f};
    in_pad.substract_mean_normalize(mean_vals, norm_vals);

    ncnn::Extractor ex = ppocrv5_det.create_extractor();

    ex.input("in0", in_pad);

    ncnn::Mat out;
    ex.extract("out0", out);

    const float denorm_vals[1] = {255.f};
    out.substract_mean_normalize(0, denorm_vals);

    cv::Mat pred(out.h, out.w, CV_8UC1);
    out.to_pixels(pred.data, ncnn::Mat::PIXEL_GRAY);

    // threshold binary
    cv::Mat bitmap;
    const float threshold = 0.3f;
    cv::threshold(pred, bitmap, threshold * 255, 255, cv::THRESH_BINARY);

    // boxes from bitmap
    {
        // should use dbnet post process, but I think unclip process is difficult to write
        // so simply implement expansion. This may lose detection accuracy
        // original implementation can be referenced
        // https://github.com/MhLiao/DB/blob/master/structure/representers/seg_detector_representer.py

        const float box_thresh = 0.6f;
        const float enlarge_ratio = 1.95f;

        const float min_size = 3 * scale;
        const int max_candidates = 1000;

        std::vector<std::vector<cv::Point> > contours;
        std::vector<cv::Vec4i> hierarchy;

        cv::findContours(bitmap, contours, hierarchy, cv::RETR_LIST, cv::CHAIN_APPROX_SIMPLE);

        contours.resize(std::min(contours.size(), (size_t)max_candidates));

        for (size_t i = 0; i < contours.size(); i++)
        {
            const std::vector<cv::Point>& contour = contours[i];
            if (contour.size() <= 2)
                continue;

            double score = contour_score(pred, contour);
            if (score < box_thresh)
                continue;

            cv::RotatedRect rrect = cv::minAreaRect(contour);

            float rrect_maxwh = std::max(rrect.size.width, rrect.size.height);
            if (rrect_maxwh < min_size)
                continue;

            int orientation = 0;
            if (rrect.angle >= -30 && rrect.angle <= 30 && rrect.size.height > rrect.size.width * 2.7)
            {
                // vertical text
                orientation = 1;
            }
            if ((rrect.angle <= -60 || rrect.angle >= 60) && rrect.size.width > rrect.size.height * 2.7)
            {
                // vertical text
                orientation = 1;
            }

            if (rrect.angle < -30)
            {
                // make orientation from -90 ~ -30 to 90 ~ 150
                rrect.angle += 180;
            }
            if (orientation == 0 && rrect.angle < 30)
            {
                // make it horizontal
                rrect.angle += 90;
                std::swap(rrect.size.width, rrect.size.height);
            }
            if (orientation == 1 && rrect.angle >= 60)
            {
                // make it vertical
                rrect.angle -= 90;
                std::swap(rrect.size.width, rrect.size.height);
            }

            // enlarge
            rrect.size.height += rrect.size.width * (enlarge_ratio - 1);
            rrect.size.width *= enlarge_ratio;

            // adjust offset to original unpadded
            rrect.center.x = (rrect.center.x - (wpad / 2)) / scale;
            rrect.center.y = (rrect.center.y - (hpad / 2)) / scale;
            rrect.size.width = (rrect.size.width) / scale;
            rrect.size.height = (rrect.size.height) / scale;

            Object obj;
            obj.rrect = rrect;
            obj.orientation = orientation;
            obj.prob = score;
            objects.push_back(obj);
        }
    }

    return 0;
}

int PPOCRv5::recognize(const cv::Mat& rgb, Object& object)
{
    cv::setNumThreads(1);

    cv::Mat roi = get_rotate_crop_image(rgb, object);

    ncnn::Mat in = ncnn::Mat::from_pixels(roi.data, ncnn::Mat::PIXEL_RGB2BGR, roi.cols, roi.rows);

    // ~/.paddlex/official_models/PP-OCRv5_mobile_rec/inference.yml
    const float mean_vals[3] = {127.5, 127.5, 127.5};
    const float norm_vals[3] = {1.0 / 127.5, 1.0 / 127.5, 1.0 / 127.5};
    in.substract_mean_normalize(mean_vals, norm_vals);

    ncnn::Extractor ex = ppocrv5_rec.create_extractor();

    ex.input("in0", in);

    ncnn::Mat out;
    ex.extract("out0", out);

    // 18385 x len
    int last_token = 0;

    for (int i = 0; i < out.h; i++)
    {
        const float* p = out.row(i);

        int index = 0;
        float max_score = -9999.f;
        for (int j = 0; j < out.w; j++)
        {
            float score = *p++;
            if (score > max_score)
            {
                max_score = score;
                index = j;
            }
        }

        if (last_token == index) // CTC rule, if index is same as last one, they will be merged into one token
                continue;

        last_token = index;

        if (index <= 0)
            continue;

        Character ch;
        ch.id = index - 1;
        ch.prob = max_score;

        object.text.push_back(ch);
    }

    return 0;
}

int PPOCRv5::detect_and_recognize(const cv::Mat& rgb, std::vector<Object>& objects)
{
    detect(rgb, objects);

    #pragma omp parallel for num_threads(ncnn::get_big_cpu_count()) schedule(dyanmic)
    for (size_t i = 0; i < objects.size(); i++)
    {
        recognize(rgb, objects[i]);
    }

    return 0;
}

