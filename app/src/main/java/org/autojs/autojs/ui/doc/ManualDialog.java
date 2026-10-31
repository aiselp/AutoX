package org.autojs.autojs.ui.doc;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.afollestad.materialdialogs.MaterialDialog;
import com.stardust.util.IntentUtil;

import org.autojs.autojs.ui.widget.EWebView;
import org.autojs.autoxjs.databinding.FloatingManualDialogBinding;

/**
 * Created by Stardust on 2017/10/24.
 */

public class ManualDialog {

    TextView mTitle;
    EWebView mEWebView;
    View mPinToLeft;

    Dialog mDialog;
    private Context mContext;

    public ManualDialog(Context context) {
        mContext = context;
        FloatingManualDialogBinding binding = FloatingManualDialogBinding.inflate(LayoutInflater.from(context));
        mTitle = binding.title;
        mEWebView = binding.ewebView;
        mPinToLeft = binding.pinToLeft;
        binding.close.setOnClickListener(v -> close());
        binding.fullscreen.setOnClickListener(v -> viewInNewActivity());
        mDialog = new MaterialDialog.Builder(context)
                .customView(binding.getRoot(), false)
                .build();
        mDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
    }


    public ManualDialog title(String title) {
        mTitle.setText(title);
        return this;
    }

    public ManualDialog url(String url) {
        mEWebView.getWebView().loadUrl(url);
        return this;
    }

    public ManualDialog pinToLeft(View.OnClickListener listener) {
        mPinToLeft.setOnClickListener(v -> {
            mDialog.dismiss();
            listener.onClick(v);
        });
        return this;
    }

    public ManualDialog show() {
        mDialog.show();
        return this;
    }

    void close() {
        mDialog.dismiss();
    }

    void viewInNewActivity() {
        mDialog.dismiss();
        IntentUtil.browse(mContext,mEWebView.getWebView().getUrl());
    }

}
