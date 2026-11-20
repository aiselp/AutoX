package com.stardust.concurrent;

/**
 * Created by Stardust on 2017/10/28.
 */

public class VolatileDispose<T> {

    private volatile T mValue;
    /** 与 mValue 分开记录「是否已 set」，使 setAndNotify(null) 也能正确唤醒等待方。 */
    private volatile boolean mSet;

    public T blockedGet() {
        synchronized (this) {
            while (!mSet) {
                try {
                    this.wait();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            return mValue;
        }
    }

    public T blockedGetOrThrow(Class<? extends RuntimeException> exception) {
        synchronized (this) {
            while (!mSet) {
                try {
                    this.wait();
                } catch (InterruptedException e) {
                    try {
                        throw exception.newInstance();
                    } catch (InstantiationException | IllegalAccessException e1) {
                        throw new RuntimeException(e1);
                    }
                }
            }
            return mValue;
        }
    }

    public T blockedGetOrThrow(Class<? extends RuntimeException> exception, long timeout, T defaultValue) {
        synchronized (this) {
            if (!mSet) {
                try {
                    this.wait(timeout);
                } catch (InterruptedException e) {
                    try {
                        throw exception.newInstance();
                    } catch (InstantiationException | IllegalAccessException e1) {
                        throw new RuntimeException(e1);
                    }
                }
            }
            return mSet ? mValue : defaultValue;
        }
    }

    public void setAndNotify(T value) {
        synchronized (this) {
            mValue = value;
            mSet = true;
            notifyAll();
        }
    }
}
