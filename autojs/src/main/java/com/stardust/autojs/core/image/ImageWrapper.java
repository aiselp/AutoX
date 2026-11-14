package com.stardust.autojs.core.image;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.Image;
import android.util.Log;

import androidx.annotation.NonNull;

import com.stardust.autojs.core.opencv.Mat;
import com.stardust.autojs.core.opencv.OpenCVHelper;
import com.stardust.pio.UncheckedIOException;

import org.opencv.android.Utils;
import org.opencv.imgcodecs.Imgcodecs;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by Stardust on 2017/11/25.
 */
public class ImageWrapper {

    private static final String TAG = "ImageWrapper";
    private static final int MAX_TRACKED_IMAGES = 10000;
    private static final Set<WeakReference<ImageWrapper>> sAllImages = new HashSet<>();
    private static final ReferenceQueue<ImageWrapper> sStaleImages = new ReferenceQueue<>();
    private static final Object sLock = new Object();
    private static volatile boolean sTrackingEnabled = false;

    private Mat mMat;
    private int mWidth;
    private int mHeight;
    private Bitmap mBitmap;
    private boolean mRecycled;

    public static void enableTracking() {
        synchronized (sLock) {
            sTrackingEnabled = true;
            drainStaleImages();
            sAllImages.clear();
        }
    }

    public static void disableTracking() {
        synchronized (sLock) {
            sTrackingEnabled = false;
        }
    }

    /**
     * 回收所有被追踪且仍存活的图像。仅由脚本显式调用（images.recycleAllImages()）；
     * 追踪数量达到上限时不会自动调用，避免回收脚本仍在使用的图像。
     */
    public static void recycleAllTrackedImages() {
        synchronized (sLock) {
            drainStaleImages();
            for (WeakReference<ImageWrapper> ref : sAllImages) {
                ImageWrapper img = ref.get();
                if (img != null && img.isAlive()) {
                    try {
                        img.recycle();
                    } catch (Exception e) {
                        Log.w(TAG, "Failed to recycle image", e);
                    }
                }
            }
            sAllImages.clear();
        }
    }

    public static int getTrackedImageCount() {
        synchronized (sLock) {
            drainStaleImages();
            return sAllImages.size();
        }
    }

    /**
     * 借助 ReferenceQueue 移除已被 GC 的弱引用，均摊 O(1)，
     * 不再需要每次创建图像时遍历整个集合做 removeIf。
     */
    private static void drainStaleImages() {
        Reference<? extends ImageWrapper> ref;
        while ((ref = sStaleImages.poll()) != null) {
            sAllImages.remove(ref);
        }
    }

    protected ImageWrapper(Mat mat) {
        mMat = mat;
        mWidth = mat.cols();
        mHeight = mat.rows();
        trackThis();
    }

    protected ImageWrapper(Bitmap bitmap) {
        mBitmap = bitmap;
        mWidth = bitmap.getWidth();
        mHeight = bitmap.getHeight();
        trackThis();
    }

    protected ImageWrapper(Bitmap bitmap, Mat mat) {
        mBitmap = bitmap;
        mMat = mat;
        mWidth = bitmap.getWidth();
        mHeight = bitmap.getHeight();
        trackThis();
    }

    public ImageWrapper(int width, int height) {
        this(Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888));
    }

    private void trackThis() {
        // 追踪默认关闭；锁外快速返回，避免每张图像都进入全局锁。
        if (!sTrackingEnabled) {
            return;
        }
        synchronized (sLock) {
            // 双重检查：等待锁期间可能已被 disableTracking()。
            if (!sTrackingEnabled) {
                return;
            }
            drainStaleImages();
            if (sAllImages.size() == MAX_TRACKED_IMAGES) {
                Log.w(TAG, "tracked images reached " + MAX_TRACKED_IMAGES
                        + "; images may not be released, call images.recycleAllImages()");
            }
            // 达到上限只告警、继续登记，绝不在内部回收脚本仍在使用的图像。
            sAllImages.add(new WeakReference<>(this, sStaleImages));
        }
    }

    public boolean isAlive() {
        return (mRecycled || (mBitmap == null && mMat == null)) ? false : true;
    }

    public static ImageWrapper ofImage(Image image) {
        if (image == null) {
            return null;
        }
        try (image) {
            return new ImageWrapper(toBitmap(image));
        }
    }

    public static ImageWrapper ofMat(Mat mat) {
        if (mat == null) {
            return null;
        }
        return new ImageWrapper(mat);
    }

    public static ImageWrapper ofBitmap(Bitmap bitmap) {
        if (bitmap == null) {
            return null;
        }
        return new ImageWrapper(bitmap);
    }

    public static Bitmap toBitmap(Image image) {
        Image.Plane plane = image.getPlanes()[0];
        ByteBuffer buffer = plane.getBuffer();
        buffer.position(0);
        int pixelStride = plane.getPixelStride();
        int rowPadding = plane.getRowStride() - pixelStride * image.getWidth();
        Bitmap bitmap = Bitmap.createBitmap(image.getWidth() + rowPadding / pixelStride, image.getHeight(), Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(buffer);
        if (rowPadding == 0) {
            return bitmap;
        }
        return Bitmap.createBitmap(bitmap, 0, 0, image.getWidth(), image.getHeight());
    }

    public int getWidth() {
        ensureNotRecycled();
        return mWidth;
    }

    public int getHeight() {
        ensureNotRecycled();
        return mHeight;
    }

    public Mat getMat() {
        ensureNotRecycled();
        if (mMat == null && mBitmap != null) {
            mMat = new Mat();
            Utils.bitmapToMat(mBitmap, mMat, true);
        }
        return mMat;
    }

    public void saveTo(String path) {
        ensureNotRecycled();
        if (mBitmap != null) {
            try {
                mBitmap.compress(Bitmap.CompressFormat.PNG, 100, new FileOutputStream(path));
            } catch (FileNotFoundException e) {
                throw new UncheckedIOException(e);
            }
        } else {
            Imgcodecs.imwrite(path, mMat);
        }
    }

    public int pixel(int x, int y) {
        ensureNotRecycled();
        if (mBitmap != null) {
            return mBitmap.getPixel(x, y);
        }
        double[] channels = mMat.get(x, y);
        return Color.argb((int) channels[3], (int) channels[0], (int) channels[1], (int) channels[2]);
    }

    public Bitmap getBitmap() {
        ensureNotRecycled();
        if (mBitmap == null && mMat != null) {
            mBitmap = Bitmap.createBitmap(mMat.width(), mMat.height(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(mMat, mBitmap);
        }
        return mBitmap;
    }

    public void recycle() {
        if (mRecycled) {
            return;
        }
        mRecycled = true;
        mBitmap = null;
        if (mMat != null) {
            OpenCVHelper.release(mMat);
            mMat = null;
        }
    }

    public void ensureNotRecycled() {
        if (mRecycled || (mBitmap == null && mMat == null)) {
            throw new IllegalStateException("image has been recycled");
        }
    }

    @Override
    @NonNull
    public ImageWrapper clone() {
        ensureNotRecycled();
        if (mBitmap == null) {
            return ImageWrapper.ofMat(mMat.clone());
        }
        if (mMat == null) {
            return ImageWrapper.ofBitmap(mBitmap.copy(mBitmap.getConfig(), true));
        }
        return new ImageWrapper(mBitmap.copy(mBitmap.getConfig(), true), mMat.clone());
    }
}