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

public class CurvesToolValue {

    public CurvesValue luminanceCurve = new CurvesValue();
    public CurvesValue redCurve = new CurvesValue();
    public CurvesValue greenCurve = new CurvesValue();
    public CurvesValue blueCurve = new CurvesValue();
    public ByteBuffer curveBuffer;

    public int activeType;

    public final static int CurvesTypeLuminance = 0;
    public final static int CurvesTypeRed = 1;
    public final static int CurvesTypeGreen = 2;
    public final static int CurvesTypeBlue = 3;

    public CurvesToolValue() {
        curveBuffer = ByteBuffer.allocateDirect(200 * 4);
        curveBuffer.order(ByteOrder.LITTLE_ENDIAN);
    }

    public void fillBuffer() {
        curveBuffer.position(0);
        float[] luminanceCurveData = luminanceCurve.getDataPoints();
        float[] redCurveData = redCurve.getDataPoints();
        float[] greenCurveData = greenCurve.getDataPoints();
        float[] blueCurveData = blueCurve.getDataPoints();
        for (int a = 0; a < 200; a++) {
            curveBuffer.put((byte) (redCurveData[a] * 255));
            curveBuffer.put((byte) (greenCurveData[a] * 255));
            curveBuffer.put((byte) (blueCurveData[a] * 255));
            curveBuffer.put((byte) (luminanceCurveData[a] * 255));
        }
        curveBuffer.position(0);
    }

    public boolean shouldBeSkipped() {
        return luminanceCurve.isDefault() && redCurve.isDefault() && greenCurve.isDefault() && blueCurve.isDefault();
    }




}
