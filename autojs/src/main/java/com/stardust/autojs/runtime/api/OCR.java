package com.stardust.autojs.runtime.api;

import android.graphics.Bitmap;
import android.os.Looper;
import android.util.Log;

import com.baidu.paddle.lite.ocr.OcrResult;
import com.baidu.paddle.lite.ocr.Predictor;
import com.stardust.app.GlobalAppContext;
import com.stardust.autojs.core.image.ImageWrapper;
import com.stardust.concurrent.VolatileDispose;

import java.util.Collections;
import java.util.List;

public class OCR {

    private final Predictor mPredictor = new Predictor();

    private boolean useCustomModel;
    private boolean useSpecificModels;
    private String customModelPath;
    private String customLabelPath;
    private String clsFileName;
    private String detFileName;
    private String recFileName;
    private float scoreThreshold = 0.1f;


    public synchronized boolean init(boolean useSlim) {
        if (!mPredictor.isLoaded || !useCustomModel && useSlim != mPredictor.isUseSlim()) {
            if (useSpecificModels) {
                mPredictor.clsModelFilename = clsFileName;
                mPredictor.recModelFilename = recFileName;
                mPredictor.detModelFilename = detFileName;
            }
            mPredictor.scoreThreshold = scoreThreshold;
            if (Looper.getMainLooper() == Looper.myLooper()) {
                VolatileDispose<Boolean> result = new VolatileDispose<>();
                new Thread(() -> {
                    if (useCustomModel) {
                        result.setAndNotify(mPredictor.init(GlobalAppContext.get(), customModelPath, customLabelPath));
                    } else {
                        result.setAndNotify(mPredictor.init(GlobalAppContext.get(), useSlim));
                    }
                }).start();
                return result.blockedGet();
            } else {
                if (useCustomModel) {
                    return mPredictor.init(GlobalAppContext.get(), customModelPath, customLabelPath);
                } else {
                    return mPredictor.init(GlobalAppContext.get(), useSlim);
                }
            }
        }
        return mPredictor.isLoaded;
    }

    public boolean initWithCustomModel(String modelPath, String labelPath) {
        mPredictor.releaseModel();
        useCustomModel = true;
        customModelPath = modelPath;
        customLabelPath = labelPath;
        mPredictor.checkModelLoaded = false;
        return this.init(false);
    }

    public boolean initWithSpecificModels(String modelPath, String labelPath, String detFileName, String recFileName, String clsFileName) {
        useSpecificModels = true;
        mPredictor.releaseModel();
        useCustomModel = true;
        customModelPath = modelPath;
        customLabelPath = labelPath;
        this.detFileName = detFileName;
        this.recFileName = recFileName;
        this.clsFileName = clsFileName;
        return this.init(false);
    }

    public void resetDefaultModel() {
        useCustomModel = false;
        useSpecificModels = false;
        mPredictor.releaseModel();
    }

    public void release() {
        mPredictor.releaseModel();
    }

    public List<OcrResult> detect(ImageWrapper image, int cpuThreadNum, boolean useSlim) {
        if (image == null) {
            return Collections.emptyList();
        }
        Bitmap bitmap = image.getBitmap();
        if (bitmap == null || bitmap.isRecycled()) {
            return Collections.emptyList();
        }
        if (mPredictor.cpuThreadNum != cpuThreadNum && !useCustomModel) {
            mPredictor.releaseModel();
            mPredictor.cpuThreadNum = cpuThreadNum;
        }
        init(useSlim);
        return mPredictor.runOcr(bitmap);
    }

    public List<OcrResult> detect(ImageWrapper image, int cpuThreadNum) {
        return detect(image, cpuThreadNum, true);
    }

    public List<OcrResult> detect(ImageWrapper image) {
        return detect(image, 4, true);
    }

    public String[] recognizeText(ImageWrapper image, int cpuThreadNum, boolean useSlim) {
        List<OcrResult> words_result = detect(image, cpuThreadNum, useSlim);
        Collections.sort(words_result);
        String[] outputResult = new String[words_result.size()];
        for (int i = 0; i < words_result.size(); i++) {
            outputResult[i] = words_result.get(i).getLabel();
            Log.i("outputResult", outputResult[i]); // show LOG in Logcat panel
        }
        return outputResult;
    }

    public String[] recognizeText(ImageWrapper image, int cpuThreadNum) {
        return recognizeText(image, cpuThreadNum, true);
    }

    public String[] recognizeText(ImageWrapper image) {
        return recognizeText(image, 4, true);
    }

    public float getScoreThreshold() {
        return scoreThreshold;
    }

    public void setScoreThreshold(float scoreThreshold) {
        this.scoreThreshold = scoreThreshold;
    }
}



