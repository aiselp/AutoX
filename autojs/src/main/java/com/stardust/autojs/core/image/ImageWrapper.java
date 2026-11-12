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
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Created by Stardust on 2017/11/25.
 */
public class ImageWrapper {

    private static final List<WeakReference<ImageWrapper>> sAllImages = new ArrayList<>();
    private static final Object sLock = new Object();
    private static boolean sTrackingEnabled = false;

    private Mat mMat;
    private int mWidth;
    private int mHeight;
    private Bitmap mBitmap;
    private boolean mRecycled;

    public static void enableTracking() {
        Log.d("ImageWrapper", "enableTracking called, sAllImages size before clear: " + sAllImages.size());
        synchronized (sLock) {
            sTrackingEnabled = true;
            Log.d("ImageWrapper", "sTrackingEnabled set to true");
            int size = sAllImages.size();
            sAllImages.clear();
            Log.d("ImageWrapper", "sAllImages cleared, size before: " + size);
        }
        Log.d("ImageWrapper", "enableTracking done");
    }

    public static void disableTracking() {
        synchronized (sLock) {
            sTrackingEnabled = false;
        }
    }

    public static void recycleAllTrackedImages() {
        synchronized (sLock) {
            sAllImages.removeIf(new Predicate() { // from class: com.stardust.autojs.core.image.ImageWrapper$$ExternalSyntheticLambda0
                @Override // java.util.function.Predicate
                public final boolean test(Object obj) {
                    return ImageWrapper.lambda$recycleAllTrackedImages$0((WeakReference) obj);
                }
            });
            for (WeakReference<ImageWrapper> ref : sAllImages) {
                ImageWrapper img = ref.get();
                if (img != null && img.isAlive()) {
                    try {
                        img.recycle();
                    } catch (Exception e) {
                    }
                }
            }
            sAllImages.clear();
        }
    }

    static /* synthetic */ boolean lambda$recycleAllTrackedImages$0(WeakReference ref) {
        return ref.get() == null;
    }

    public static int getTrackedImageCount() {
        int size;
        synchronized (sLock) {
            sAllImages.removeIf(new Predicate() { // from class: com.stardust.autojs.core.image.ImageWrapper$$ExternalSyntheticLambda1
                @Override // java.util.function.Predicate
                public final boolean test(Object obj) {
                    return ImageWrapper.lambda$getTrackedImageCount$1((WeakReference) obj);
                }
            });
            size = sAllImages.size();
        }
        return size;
    }

    static /* synthetic */ boolean lambda$getTrackedImageCount$1(WeakReference ref) {
        return ref.get() == null;
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
        synchronized (sLock) {
            if (sTrackingEnabled) {
                sAllImages.removeIf(new Predicate() { // from class: com.stardust.autojs.core.image.ImageWrapper$$ExternalSyntheticLambda2
                    @Override // java.util.function.Predicate
                    public final boolean test(Object obj) {
                        return ImageWrapper.lambda$trackThis$2((WeakReference) obj);
                    }
                });
                // 先回收再登记：原先先 add 再判阈值，触发全量回收时会把
                // 刚创建、即将返回给脚本的这张图也回收掉。
                if (sAllImages.size() >= 10000) {
                    recycleAllTrackedImages();
                }
                sAllImages.add(new WeakReference<>(this));
            }
        }
    }

    static /* synthetic */ boolean lambda$trackThis$2(WeakReference ref) {
        return ref.get() == null;
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