/*
 * This is the source code of Telegram for Android v. 5.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package dev.oritwig.telegram.photo;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

public class CurvesValue {
    private final static int curveGranularity = 100;
    private final static int curveDataStep = 2;

    public float blacksLevel = 0.0f;
    public float shadowsLevel = 25.0f;
    public float midtonesLevel = 50.0f;
    public float highlightsLevel = 75.0f;
    public float whitesLevel = 100.0f;

    public float previousBlacksLevel = 0.0f;
    public float previousShadowsLevel = 25.0f;
    public float previousMidtonesLevel = 50.0f;
    public float previousHighlightsLevel = 75.0f;
    public float previousWhitesLevel = 100.0f;

    public float[] cachedDataPoints;

    public float[] getDataPoints() {
        if (cachedDataPoints == null) {
            interpolateCurve();
        }
        return cachedDataPoints;
    }

    public void saveValues() {
        previousBlacksLevel = blacksLevel;
        previousShadowsLevel = shadowsLevel;
        previousMidtonesLevel = midtonesLevel;
        previousHighlightsLevel = highlightsLevel;
        previousWhitesLevel = whitesLevel;
    }

    public void restoreValues() {
        blacksLevel = previousBlacksLevel;
        shadowsLevel = previousShadowsLevel;
        midtonesLevel = previousMidtonesLevel;
        highlightsLevel = previousHighlightsLevel;
        whitesLevel = previousWhitesLevel;
        interpolateCurve();
    }

    public float[] interpolateCurve() {
        float[] points = new float[] {
                -0.001f, blacksLevel / 100.0f,
                0.0f, blacksLevel / 100.0f,
                0.25f, shadowsLevel / 100.0f,
                0.5f, midtonesLevel / 100.0f,
                0.75f, highlightsLevel / 100.0f,
                1f, whitesLevel / 100.0f,
                1.001f, whitesLevel / 100.0f
        };

        ArrayList<Float> dataPoints = new ArrayList<>(100);
        ArrayList<Float> interpolatedPoints = new ArrayList<>(100);

        interpolatedPoints.add(points[0]);
        interpolatedPoints.add(points[1]);

        for (int index = 1; index < points.length / 2 - 2; index++) {
            float point0x = points[(index - 1) * 2];
            float point0y = points[(index - 1) * 2 + 1];
            float point1x = points[(index) * 2];
            float point1y = points[(index) * 2 + 1];
            float point2x = points[(index + 1) * 2];
            float point2y = points[(index + 1) * 2 + 1];
            float point3x = points[(index + 2) * 2];
            float point3y = points[(index + 2) * 2 + 1];


            for (int i = 1; i < curveGranularity; i++) {
                float t = (float) i * (1.0f / (float) curveGranularity);
                float tt = t * t;
                float ttt = tt * t;

                float pix = 0.5f * (2 * point1x + (point2x - point0x) * t + (2 * point0x - 5 * point1x + 4 * point2x - point3x) * tt + (3 * point1x - point0x - 3 * point2x + point3x) * ttt);
                float piy = 0.5f * (2 * point1y + (point2y - point0y) * t + (2 * point0y - 5 * point1y + 4 * point2y - point3y) * tt + (3 * point1y - point0y - 3 * point2y + point3y) * ttt);

                piy = Math.max(0, Math.min(1, piy));

                if (pix > point0x) {
                    interpolatedPoints.add(pix);
                    interpolatedPoints.add(piy);
                }

                if ((i - 1) % curveDataStep == 0) {
                    dataPoints.add(piy);
                }
            }
            interpolatedPoints.add(point2x);
            interpolatedPoints.add(point2y);
        }
        interpolatedPoints.add(points[12]);
        interpolatedPoints.add(points[13]);

        cachedDataPoints = new float[dataPoints.size()];
        for (int a = 0; a < cachedDataPoints.length; a++) {
            cachedDataPoints[a] = dataPoints.get(a);
        }
        float[] retValue = new float[interpolatedPoints.size()];
        for (int a = 0; a < retValue.length; a++) {
            retValue[a] = interpolatedPoints.get(a);
        }
        return retValue;
    }

    public boolean isDefault() {
        return Math.abs(blacksLevel - 0) < 0.00001 && Math.abs(shadowsLevel - 25) < 0.00001 && Math.abs(midtonesLevel - 50) < 0.00001 && Math.abs(highlightsLevel - 75) < 0.00001 && Math.abs(whitesLevel - 100) < 0.00001;
    }




}
