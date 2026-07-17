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

#include <android/asset_manager_jni.h>
#include <android/bitmap.h>

#include <android/log.h>

#include <jni.h>

#include <string>
#include <vector>

#include <platform.h>
#include <benchmark.h>

#include "ppocrv5.h"
#include "ppocrv5_dict.h"

#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp>

#if __ARM_NEON
#include <arm_neon.h>
#endif // __ARM_NEON

static PPOCRv5* g_ppocrv5 = 0;
static ncnn::Mutex lock;

extern "C" {

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved)
{
    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "JNI_OnLoad");

    ncnn::create_gpu_instance();

    return JNI_VERSION_1_4;
}

JNIEXPORT void JNI_OnUnload(JavaVM* vm, void* reserved)
{
    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "JNI_OnUnload");

    {
        ncnn::MutexLockGuard g(lock);

        delete g_ppocrv5;
        g_ppocrv5 = 0;
    }

    ncnn::destroy_gpu_instance();
}

// public native boolean loadModelByPath(String detParamPath, String detModelPath, String recParamPath, String recModelPath, int sizeid, int cpugpu, boolean useFp16);
JNIEXPORT jboolean JNICALL Java_com_equationl_ncnnandroidppocr_cpp_OCRNative_loadModelByPath(JNIEnv* env, jobject thiz, jstring detParamPath, jstring detModelPath, jstring recParamPath, jstring recModelPath, jint target_size, jint cpugpu, jboolean useFp16, jint numThreads)
{
    if (cpugpu < 0 || cpugpu > 2)
    {
        return JNI_FALSE;
    }

    if (detParamPath == nullptr || detModelPath == nullptr || recParamPath == nullptr || recModelPath == nullptr)
    {
        __android_log_print(ANDROID_LOG_ERROR, "ncnn", "loadModelByPath: one or more paths are null");
        return JNI_FALSE;
    }

    const char* det_parampath_cstr = env->GetStringUTFChars(detParamPath, nullptr);
    const char* det_modelpath_cstr = env->GetStringUTFChars(detModelPath, nullptr);
    const char* rec_parampath_cstr = env->GetStringUTFChars(recParamPath, nullptr);
    const char* rec_modelpath_cstr = env->GetStringUTFChars(recModelPath, nullptr);

    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "loadModelByPath det_param=%s det_model=%s rec_param=%s rec_model=%s useFp16=%d",
                        det_parampath_cstr, det_modelpath_cstr, rec_parampath_cstr, rec_modelpath_cstr, (int)useFp16);

    bool use_fp16 = (bool)useFp16;
    bool use_gpu = (int)cpugpu == 1;
    bool use_turnip = (int)cpugpu == 2;

    // reload
    {
        ncnn::MutexLockGuard g(lock);

        {
            // always reload when using path-based loading
            delete g_ppocrv5;
            g_ppocrv5 = 0;

            ncnn::destroy_gpu_instance();

            if (use_turnip)
            {
                ncnn::create_gpu_instance("libvulkan_freedreno.so");
            }
            else if (use_gpu)
            {
                ncnn::create_gpu_instance();
            }

            g_ppocrv5 = new PPOCRv5;

            int ret = g_ppocrv5->load(det_parampath_cstr, det_modelpath_cstr, rec_parampath_cstr, rec_modelpath_cstr, use_fp16, use_gpu || use_turnip);

            if (ret != 0)
            {
                __android_log_print(ANDROID_LOG_ERROR, "ncnn", "loadModelByPath: failed to load model");
                delete g_ppocrv5;
                g_ppocrv5 = 0;

                env->ReleaseStringUTFChars(detParamPath, det_parampath_cstr);
                env->ReleaseStringUTFChars(detModelPath, det_modelpath_cstr);
                env->ReleaseStringUTFChars(recParamPath, rec_parampath_cstr);
                env->ReleaseStringUTFChars(recModelPath, rec_modelpath_cstr);
                return JNI_FALSE;
            }

            if (numThreads > 0) {
                g_ppocrv5->setDetThreadNum(numThreads);
            }

            g_ppocrv5->set_target_size(target_size);
        }
    }

    env->ReleaseStringUTFChars(detParamPath, det_parampath_cstr);
    env->ReleaseStringUTFChars(detModelPath, det_modelpath_cstr);
    env->ReleaseStringUTFChars(recParamPath, rec_parampath_cstr);
    env->ReleaseStringUTFChars(recModelPath, rec_modelpath_cstr);

    return JNI_TRUE;
}

// public native void release();
JNIEXPORT void JNICALL Java_com_equationl_ncnnandroidppocr_cpp_OCRNative_release(JNIEnv* env, jobject thiz)
{
    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "release");

    {
        ncnn::MutexLockGuard g(lock);

        delete g_ppocrv5;
        g_ppocrv5 = 0;
    }

    ncnn::destroy_gpu_instance();
}

// Helper function to create OcrResult object from detection results
static jobject createOcrResult(JNIEnv* env, const std::vector<Object>& objects, jlong inferenceTime)
{
    // Find all required classes
    jclass ocrResultClass = env->FindClass("com/equationl/ncnnandroidppocr/bean/NcnnOcrResult");
    jclass ocrTextLineResultClass = env->FindClass("com/equationl/ncnnandroidppocr/bean/OcrTextLineResult");
    jclass ocrTextResultClass = env->FindClass("com/equationl/ncnnandroidppocr/bean/OcrTextResult");
    jclass pointClass = env->FindClass("android/graphics/Point");
    jclass arrayListClass = env->FindClass("java/util/ArrayList");

    // Get constructors - OcrResult has 4 parameters: (String, long, List, Bitmap)
    jmethodID ocrResultConstructor = env->GetMethodID(ocrResultClass, "<init>", "(Ljava/lang/String;JLjava/util/List;Landroid/graphics/Bitmap;)V");
    jmethodID ocrTextLineResultConstructor = env->GetMethodID(ocrTextLineResultClass, "<init>", "(Ljava/util/List;Ljava/lang/String;FILjava/util/List;)V");
    jmethodID ocrTextResultConstructor = env->GetMethodID(ocrTextResultClass, "<init>", "(Ljava/lang/String;IF)V");
    jmethodID pointConstructor = env->GetMethodID(pointClass, "<init>", "(II)V");
    jmethodID arrayListConstructor = env->GetMethodID(arrayListClass, "<init>", "()V");
    jmethodID arrayListAdd = env->GetMethodID(arrayListClass, "add", "(Ljava/lang/Object;)Z");

    // Collect line texts for reverse concatenation
    std::vector<std::string> lineTexts;

    // Create textLines ArrayList
    jobject textLinesList = env->NewObject(arrayListClass, arrayListConstructor);

    for (size_t i = 0; i < objects.size(); i++)
    {
        const Object& obj = objects[i];

        // Create points ArrayList for this text line
        jobject pointsList = env->NewObject(arrayListClass, arrayListConstructor);

        // Get 4 corner points from RotatedRect
        cv::Point2f corners[4];
        obj.rrect.points(corners);

        // Add points in order: 左下(0), 右下(1), 右上(2), 左上(3)
        for (int j = 0; j < 4; j++)
        {
            jobject point = env->NewObject(pointClass, pointConstructor, (jint)corners[j].x, (jint)corners[j].y);
            env->CallBooleanMethod(pointsList, arrayListAdd, point);
            env->DeleteLocalRef(point);
        }

        // Build text string for this line and create textList
        std::string lineText;
        jobject textList = env->NewObject(arrayListClass, arrayListConstructor);

        for (size_t j = 0; j < obj.text.size(); j++)
        {
            const Character& ch = obj.text[j];

            std::string charText;
            if (ch.id >= 0 && ch.id < character_dict_size)
            {
                charText = character_dict[ch.id];
                lineText += charText;
            }

            // Create OcrTextResult
            jstring jCharText = env->NewStringUTF(charText.c_str());
            jobject textResult = env->NewObject(ocrTextResultClass, ocrTextResultConstructor,
                                                jCharText, (jint)ch.id, (jfloat)ch.prob);
            env->CallBooleanMethod(textList, arrayListAdd, textResult);
            env->DeleteLocalRef(jCharText);
            env->DeleteLocalRef(textResult);
        }

        // Collect line text for reverse concatenation
        if (!lineText.empty())
        {
            lineTexts.push_back(lineText);
        }

        // Create OcrTextLineResult
        jstring jLineText = env->NewStringUTF(lineText.c_str());
        jobject textLineResult = env->NewObject(ocrTextLineResultClass, ocrTextLineResultConstructor,
                                                pointsList, jLineText, (jfloat)obj.prob,
                                                (jint)obj.orientation, textList);
        env->CallBooleanMethod(textLinesList, arrayListAdd, textLineResult);

        env->DeleteLocalRef(jLineText);
        env->DeleteLocalRef(textLineResult);
        env->DeleteLocalRef(pointsList);
        env->DeleteLocalRef(textList);
    }

    // Build fullText by reverse concatenation of line texts
    std::string fullText;
    for (auto it = lineTexts.rbegin(); it != lineTexts.rend(); ++it)
    {
        if (!fullText.empty())
        {
            fullText += "\n";
        }
        fullText += *it;
    }

    // Create final OcrResult (drawBitmap is null, will be set in Kotlin layer if needed)
    jstring jFullText = env->NewStringUTF(fullText.c_str());
    jobject ocrResult = env->NewObject(ocrResultClass, ocrResultConstructor,
                                       jFullText, inferenceTime, textLinesList, (jobject)nullptr);

    env->DeleteLocalRef(jFullText);
    env->DeleteLocalRef(textLinesList);

    return ocrResult;
}

// public native OcrResult detectBitmap(Bitmap bitmap);
JNIEXPORT jobject JNICALL Java_com_equationl_ncnnandroidppocr_cpp_OCRNative_detectBitmap(JNIEnv* env, jobject thiz, jobject bitmap)
{
    if (bitmap == nullptr)
    {
        __android_log_print(ANDROID_LOG_ERROR, "ncnn", "detectBitmap: bitmap is null");
        return nullptr;
    }

    ncnn::MutexLockGuard g(lock);

    if (!g_ppocrv5)
    {
        __android_log_print(ANDROID_LOG_ERROR, "ncnn", "detectBitmap: model not loaded");
        return nullptr;
    }

    // Get bitmap info
    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS)
    {
        __android_log_print(ANDROID_LOG_ERROR, "ncnn", "detectBitmap: AndroidBitmap_getInfo failed");
        return nullptr;
    }

    // Lock bitmap pixels
    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS)
    {
        __android_log_print(ANDROID_LOG_ERROR, "ncnn", "detectBitmap: AndroidBitmap_lockPixels failed");
        return nullptr;
    }

    // Convert bitmap to cv::Mat
    cv::Mat rgba(info.height, info.width, CV_8UC4, pixels);
    cv::Mat rgb;

    if (info.format == ANDROID_BITMAP_FORMAT_RGBA_8888)
    {
        cv::cvtColor(rgba, rgb, cv::COLOR_RGBA2RGB);
    }
    else if (info.format == ANDROID_BITMAP_FORMAT_RGB_565)
    {
        cv::Mat rgb565(info.height, info.width, CV_8UC2, pixels);
        cv::cvtColor(rgb565, rgb, cv::COLOR_BGR5652RGB);
    }
    else
    {
        AndroidBitmap_unlockPixels(env, bitmap);
        __android_log_print(ANDROID_LOG_ERROR, "ncnn", "detectBitmap: unsupported bitmap format");
        return nullptr;
    }

    AndroidBitmap_unlockPixels(env, bitmap);

    // Run OCR detection
    double startTime = ncnn::get_current_time();

    std::vector<Object> objects;
    g_ppocrv5->detect_and_recognize(rgb, objects);

    double endTime = ncnn::get_current_time();
    jlong inferenceTime = (jlong)(endTime - startTime);

    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "detectBitmap: detected %zu objects in %ld ms", objects.size(), inferenceTime);

    return createOcrResult(env, objects, inferenceTime);
}

// public native OcrResult detectImagePath(String imagePath);
JNIEXPORT jobject JNICALL Java_com_equationl_ncnnandroidppocr_cpp_OCRNative_detectImagePath(JNIEnv* env, jobject thiz, jstring imagePath)
{
    if (imagePath == nullptr)
    {
        __android_log_print(ANDROID_LOG_ERROR, "ncnn", "detectImagePath: imagePath is null");
        return nullptr;
    }

    // Use BitmapFactory to decode image file
    jclass bitmapFactoryClass = env->FindClass("android/graphics/BitmapFactory");
    jmethodID decodeFileMethod = env->GetStaticMethodID(bitmapFactoryClass, "decodeFile", "(Ljava/lang/String;)Landroid/graphics/Bitmap;");
    jobject bitmap = env->CallStaticObjectMethod(bitmapFactoryClass, decodeFileMethod, imagePath);

    if (bitmap == nullptr)
    {
        const char* pathCStr = env->GetStringUTFChars(imagePath, nullptr);
        __android_log_print(ANDROID_LOG_ERROR, "ncnn", "detectImagePath: failed to decode image from %s", pathCStr);
        env->ReleaseStringUTFChars(imagePath, pathCStr);
        return nullptr;
    }

    // Call detectBitmap with the loaded bitmap
    jobject result = Java_com_equationl_ncnnandroidppocr_cpp_OCRNative_detectBitmap(env, thiz, bitmap);

    // Clean up local reference
    env->DeleteLocalRef(bitmap);

    return result;
}

}
