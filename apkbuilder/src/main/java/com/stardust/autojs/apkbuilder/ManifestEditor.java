package com.stardust.autojs.apkbuilder;

import java.io.IOException;
import java.io.OutputStream;

import pxb.android.StringItem;
import pxb.android.axml.AxmlReader;
import pxb.android.axml.AxmlWriter;
import pxb.android.axml.NodeVisitor;

/**
 * Created by Stardust on 2017/10/23.
 */

public class ManifestEditor {


    private static final String NS_ANDROID = "http://schemas.android.com/apk/res/android";
    private final byte[] mSourceManifestData;
    private int mVersionCode = -1;
    private String mVersionName;
    private String mAppName;
    private String mPackageName;
    private byte[] mManifestData;


    public ManifestEditor(byte[] sourceManifestData) {
        mSourceManifestData = sourceManifestData;
    }

    public ManifestEditor setVersionCode(int versionCode) {
        mVersionCode = versionCode;
        return this;
    }

    public ManifestEditor setVersionName(String versionName) {
        mVersionName = versionName;
        return this;
    }

    public ManifestEditor setAppName(String appName) {
        mAppName = appName;
        return this;
    }

    public ManifestEditor setPackageName(String packageName) {
        mPackageName = packageName;
        return this;
    }

    public ManifestEditor commit() throws IOException {
        AxmlWriter writer = new MutableAxmlWriter();
        AxmlReader reader = new AxmlReader(mSourceManifestData);
        reader.accept(writer);
        mManifestData = writer.toByteArray();
        return this;
    }


    public void writeTo(OutputStream manifestOutputStream) throws IOException {
        try {
            manifestOutputStream.write(mManifestData);
        } finally {
            manifestOutputStream.close();
        }
    }

    public void onAttr(AxmlWriter.Attr attr) {
        if ("package".equals(attr.name.data) && mPackageName != null && attr.value instanceof StringItem) {
            ((StringItem) attr.value).data = mPackageName;
            return;
        }
        if (attr.ns == null || !NS_ANDROID.equals(attr.ns.data)) {
            return;
        }
        if ("versionCode".equals(attr.name.data) && mVersionCode != -1) {
            attr.value = mVersionCode;
            return;
        }
        if ("versionName".equals(attr.name.data) && mVersionName != null && attr.value instanceof StringItem) {
            attr.value = new StringItem(mVersionName);
            ((StringItem) attr.value).data = mVersionName;
            return;
        }
        if ("label".equals(attr.name.data) && mAppName != null && attr.value instanceof StringItem) {
            ((StringItem) attr.value).data = mAppName;
            return;
        }
    }


    private class MutableAxmlWriter extends AxmlWriter {
        private class MutableNodeImpl extends AxmlWriter.NodeImpl {

            MutableNodeImpl(String ns, String name) {
                super(ns, name);
            }

            @Override
            protected void onAttr(AxmlWriter.Attr a) {
                // 模板 APK 若由 Android Studio 的 Run 生成，manifest 会带
                // android:testOnly="true"（IDE 注入 android.injected.testOnly）。
                // 打包产物会原样继承，导致系统拒绝普通安装并报
                // "Failed to install test-only apk"。分发用的脚本 APK 不应保留
                // 该标记，这里直接丢弃此属性；AxmlWriter.NodeImpl.onAttr 只做
                // attrs.add(a)，跳过不会留下任何副作用。
                if (a.name != null && a.ns != null
                        && NS_ANDROID.equals(a.ns.data)
                        && "testOnly".equals(a.name.data)) {
                    return;
                }
                ManifestEditor.this.onAttr(a);
                super.onAttr(a);
            }


            @Override
            public NodeVisitor child(String ns, String name) {
                NodeImpl child = new MutableNodeImpl(ns, name);
                this.children.add(child);
                return child;
            }

        }

        @Override
        public NodeVisitor child(String ns, String name) {
            NodeImpl first = new MutableNodeImpl(ns, name);
            this.firsts.add(first);
            return first;
        }
    }


}
