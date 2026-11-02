package org.autojs.autojs.ui.edit;

import android.content.Context;
import android.preference.PreferenceManager;
import androidx.annotation.NonNull;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.widget.CheckBox;
import android.widget.EditText;

import com.afollestad.materialdialogs.MaterialDialog;

import org.autojs.autoxjs.R;
import org.autojs.autojs.theme.dialog.ThemeColorMaterialDialogBuilder;
import org.autojs.autojs.ui.edit.editor.CodeEditor;
import org.autojs.autoxjs.databinding.DialogFindOrReplaceBinding;

/**
 * Created by Stardust on 2017/9/28.
 */

public class FindOrReplaceDialogBuilder extends ThemeColorMaterialDialogBuilder {

    private static final String KEY_KEYWORDS = "...";

    CheckBox mRegexCheckBox;
    CheckBox mReplaceCheckBox;
    CheckBox mReplaceAllCheckBox;
    EditText mKeywordsEditText;
    EditText mReplacementEditText;

    private EditorView mEditorView;

    public FindOrReplaceDialogBuilder(@NonNull Context context, EditorView editorView) {
        super(context);
        mEditorView = editorView;
        setupViews();
        restoreState();
        autoDismiss(false);
        onNegative((dialog, which) -> dialog.dismiss());
        onPositive((dialog, which) -> {
            storeState();
            findOrReplace(dialog);
        });
    }

    private void setupViews() {
        DialogFindOrReplaceBinding binding = DialogFindOrReplaceBinding.inflate(LayoutInflater.from(context));
        mRegexCheckBox = binding.checkboxRegex;
        mReplaceCheckBox = binding.checkboxReplace;
        mReplaceAllCheckBox = binding.checkboxReplaceAll;
        mKeywordsEditText = binding.keywords;
        mReplacementEditText = binding.replacement;
        customView(binding.getRoot(), true);
        positiveText(R.string.ok);
        negativeText(R.string.cancel);
        title(R.string.text_find_or_replace);
        binding.checkboxReplaceAll.setOnCheckedChangeListener((buttonView, isChecked) -> syncWithReplaceCheckBox());
        binding.replacement.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (mReplacementEditText.getText().length() > 0) {
                    mReplaceCheckBox.setChecked(true);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }


    private void storeState() {
        PreferenceManager.getDefaultSharedPreferences(getContext()).edit()
                .putString(KEY_KEYWORDS, mKeywordsEditText.getText().toString())
                .apply();
    }


    private void restoreState() {
        mKeywordsEditText.setText(PreferenceManager.getDefaultSharedPreferences(getContext())
                .getString(KEY_KEYWORDS, ""));
    }

    void syncWithReplaceCheckBox() {
        if (mReplaceAllCheckBox.isChecked() && !mReplaceCheckBox.isChecked()) {
            mReplaceCheckBox.setChecked(true);
        }
    }

    private void findOrReplace(MaterialDialog dialog) {
        String keywords = mKeywordsEditText.getText().toString();
        if (keywords.isEmpty()) {
            return;
        }
        try {
            boolean usingRegex = mRegexCheckBox.isChecked();
            if (!mReplaceCheckBox.isChecked()) {
                mEditorView.find(keywords, usingRegex);
            } else {
                String replacement = mReplacementEditText.getText().toString();
                if (mReplaceAllCheckBox.isChecked()) {
                    mEditorView.replaceAll(keywords, replacement, usingRegex);
                } else {
                    mEditorView.replace(keywords, replacement, usingRegex);
                }
            }
            dialog.dismiss();
        } catch (CodeEditor.CheckedPatternSyntaxException e) {
            e.printStackTrace();
            mKeywordsEditText.setError(getContext().getString(R.string.error_pattern_syntax));
        }

    }

    public FindOrReplaceDialogBuilder setQueryIfNotEmpty(String s) {
        if (!TextUtils.isEmpty(s))
            mKeywordsEditText.setText(s);
        return this;
    }
}
