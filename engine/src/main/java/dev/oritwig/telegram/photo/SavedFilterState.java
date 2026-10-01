/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package dev.oritwig.telegram.photo;

import android.graphics.PointF;

public class SavedFilterState {
    public float enhanceValue;
    /** Unsupported compatibility field; nonzero is rejected by PhotoEngine. */
    @Deprecated public float softenSkinValue;
    public float exposureValue;
    public float contrastValue;
    public float warmthValue;
    public float saturationValue;
    public float fadeValue;
    public int tintShadowsColor;
    public int tintHighlightsColor;
    public float highlightsValue;
    public float shadowsValue;
    public float vignetteValue;
    public float grainValue;
    public int blurType;
    public float sharpenValue;
    public CurvesToolValue curvesToolValue = new CurvesToolValue();
    public float blurExcludeSize;
    public PointF blurExcludePoint;
    public float blurExcludeBlurSize;
    public float blurAngle;





    public boolean isEmpty() {
        return (
            Math.abs(enhanceValue) < 0.1f &&
            Math.abs(softenSkinValue) < 0.1f &&
            Math.abs(exposureValue) < 0.1f &&
            Math.abs(contrastValue) < 0.1f &&
            Math.abs(warmthValue) < 0.1f &&
            Math.abs(saturationValue) < 0.1f &&
            Math.abs(fadeValue) < 0.1f &&
            tintShadowsColor == 0 &&
            tintHighlightsColor == 0 &&
            Math.abs(highlightsValue) < 0.1f &&
            Math.abs(shadowsValue) < 0.1f &&
            Math.abs(vignetteValue) < 0.1f &&
            Math.abs(grainValue) < 0.1f &&
            blurType == 0 &&
            Math.abs(sharpenValue) < 0.1f
        );
    }
}
