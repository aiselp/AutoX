package com.stardust.autojs.core.ui.widget;

import android.content.Context;
import android.content.res.Resources;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.stardust.autojs.core.ui.ViewExtras;

public class JsSpinner extends androidx.appcompat.widget.AppCompatSpinner implements AttrSupport {

    private float mTextSize = -1;
    private int mTextStyle = -1;
    private int mTextColor = 0;

    private float mEntryTextSize = -1;
    private int mEntryTextStyle = -1;
    private int mEntryTextColor = 0;

    private int mEntryHeight = -1;

    private int mDropdownTextColorNormal;
    private int mDropdownTextColorSelected;
    private int mDropdownBgColorNormal;
    private int mDropdownBgColorSelected;

    public JsSpinner(Context context) {
        super(context);
    }

    public JsSpinner(Context context, int mode) {
        super(context, mode);
    }

    public JsSpinner(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public JsSpinner(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public JsSpinner(Context context, AttributeSet attrs, int defStyleAttr, int mode) {
        super(context, attrs, defStyleAttr, mode);
    }

    public JsSpinner(Context context, AttributeSet attrs, int defStyleAttr, int mode, Resources.Theme popupTheme) {
        super(context, attrs, defStyleAttr, mode, popupTheme);
    }

    public float getTextSize() {
        return mTextSize;
    }

    public float getEntryTextSize() {
        return mEntryTextSize;
    }

    public void setEntryTextSize(float entryTextSize) {
        mEntryTextSize = entryTextSize;
    }

    public int getEntryTextStyle() {
        return mEntryTextStyle;
    }

    public void setEntryTextStyle(int entryTextStyle) {
        mEntryTextStyle = entryTextStyle;
    }

    public int getEntryTextColor() {
        return mEntryTextColor;
    }

    public void setEntryTextColor(int entryTextColor) {
        mEntryTextColor = entryTextColor;
    }

    public void setTextSize(float textSize) {
        mTextSize = textSize;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child instanceof TextView) {
                ((TextView) child).setTextSize(TypedValue.COMPLEX_UNIT_PX, mTextSize);
            }
        }
    }

    public int getTextStyle() {
        return mTextStyle;
    }

    public void setTextStyle(int textStyle) {
        mTextStyle = textStyle;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child instanceof TextView) {
                ((TextView) child).setTypeface(((TextView) child).getTypeface(), mTextStyle);
            }
        }
    }

    public int getTextColor() {
        return mTextColor;
    }

    public void setTextColor(int textColor) {
        mTextColor = textColor;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child instanceof TextView) {
                ((TextView) child).setTextColor(mTextColor);
            }
        }
    }

    public void setDropDownMaxHeight(int maxHeightPx) {
        try {
            java.lang.reflect.Method method = androidx.appcompat.widget.AppCompatSpinner.class
                    .getDeclaredMethod("getInternalPopup");
            method.setAccessible(true);
            Object popup = method.invoke(this);
            if (popup != null) {
                java.lang.reflect.Method setHeight = popup.getClass()
                        .getMethod("setHeight", int.class);
                setHeight.invoke(popup, maxHeightPx);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setDropDownItemHeight(int heightPx) {
        mEntryHeight = heightPx;
    }

    public void setDropdownTextColors(int normalColor, int selectedColor) {
        mDropdownTextColorNormal = normalColor;
        mDropdownTextColorSelected = selectedColor;
    }

    public void setDropdownBackgroundColors(int normalColor, int selectedColor) {
        mDropdownBgColorNormal = normalColor;
        mDropdownBgColorSelected = selectedColor;
    }

    public void setDropdownItems(String[] items) {
        int defaultHeight = (int) (48 * getContext().getResources().getDisplayMetrics().density);
        setDropdownItems(items, defaultHeight, null, null);
    }

    public void setDropdownItems(String[] items, Integer itemHeight) {
        setDropdownItems(items, itemHeight, null, null);
    }

    public void setDropdownItems(String[] items, Integer itemHeight, Integer itemLayout, Integer dropDownLayout) {
        if (items == null || items.length == 0) return;

        if (itemHeight != null) {
            mEntryHeight = itemHeight;
        }

        int layoutRes = itemLayout != null ? itemLayout : android.R.layout.simple_spinner_item;
        int dropDownRes = dropDownLayout != null ? dropDownLayout : android.R.layout.simple_spinner_dropdown_item;

        Adapter adapter = new Adapter(getContext(), layoutRes, items);
        adapter.setDropDownViewResource(dropDownRes);
        setAdapter(adapter);
    }

    public class Adapter extends ArrayAdapter<String> {

        public Adapter(@NonNull Context context, int resource, @NonNull String[] objects) {
            super(context, resource, objects);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            View view = super.getView(position, convertView, parent);
            if (!(view instanceof TextView)) {
                return view;
            }
            TextView textView = (TextView) view;
            if (mTextColor != 0) {
                textView.setTextColor(mTextColor);
            }
            if (mTextSize != -1) {
                textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, mTextSize);
            }
            if (mTextStyle != -1) {
                textView.setTypeface(textView.getTypeface(), mTextStyle);
            }
            return textView;
        }

        @Override
        public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            View view = super.getDropDownView(position, convertView, parent);
            if (!(view instanceof TextView)) {
                return view;
            }
            TextView textView = (TextView) view;

            if (mEntryTextColor != 0) {
                textView.setTextColor(mEntryTextColor);
            }
            if (mEntryTextSize != -1) {
                textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, mEntryTextSize);
            }
            if (mEntryTextStyle != -1) {
                textView.setTypeface(textView.getTypeface(), mEntryTextStyle);
            }

            if (position == getSelectedItemPosition()) {
                if (mDropdownTextColorSelected != 0) {
                    textView.setTextColor(mDropdownTextColorSelected);
                }
                if (mDropdownBgColorSelected != 0) {
                    textView.setBackgroundColor(mDropdownBgColorSelected);
                }
            } else {
                if (mDropdownTextColorNormal != 0) {
                    textView.setTextColor(mDropdownTextColorNormal);
                }
                if (mDropdownBgColorNormal != 0) {
                    textView.setBackgroundColor(mDropdownBgColorNormal);
                }
            }

            if (mEntryHeight > 0) {
                AbsListView.LayoutParams lp = new AbsListView.LayoutParams(
                        AbsListView.LayoutParams.MATCH_PARENT,
                        mEntryHeight
                );
                textView.setLayoutParams(lp);
            }

            return textView;
        }
    }
}