package com.starpointgarage.app;

import android.content.Context;
import android.widget.ImageView;

public final class BrandLogo {
    private BrandLogo() {}

    public static ImageView view(Context context, int maxWidthDp) {
        ImageView v = new ImageView(context);
        v.setImageResource(R.drawable.starpoint_logo);
        v.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        v.setAdjustViewBounds(true);
        int max = NativeUi.dp(context, maxWidthDp);
        v.setMaxWidth(max);
        v.setMaxHeight(Math.round(max * 0.34f));
        return v;
    }
}