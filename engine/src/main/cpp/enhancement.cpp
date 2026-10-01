// Extracted from Telegram Android image.cpp at f2908b14133bbffbf7ab04f641ecb5bfaf533242.
// GPL-2.0-or-later; see LICENSE and NOTICE.
#include <jni.h>
#include <stdint.h>
#include <string.h>
#include <math.h>
#ifndef MAX
#define MAX(x, y) ((x) > (y)) ? (x) : (y)
#endif
#ifndef MIN
#define MIN(x, y) ((x) < (y)) ? (x) : (y)
#endif

extern "C" {
const uint32_t PGPhotoEnhanceHistogramBins = 256;
const uint32_t PGPhotoEnhanceSegments = 4;

JNIEXPORT void Java_dev_oritwig_telegram_photo_NativeEnhancement_calculate(JNIEnv *env, jclass clazz, jobject hsvBuffer, jint width, jint height, jobject buffer, jobject calcBuffer) {

    // Extraction safety boundary; the original kernel below remains unchanged.
    const jlong pixels = static_cast<jlong>(width) * height;
    if (!hsvBuffer || !buffer || !calcBuffer || width < 4 || height < 4 || width > 16384 || height > 16384 ||
        pixels > 64LL * 1024 * 1024 ||
        env->GetDirectBufferCapacity(hsvBuffer) < pixels * 4 ||
        env->GetDirectBufferCapacity(buffer) < 16 * 256 * 4 ||
        env->GetDirectBufferCapacity(calcBuffer) < 16 * 2 * 4 * 257 ||
        !env->GetDirectBufferAddress(hsvBuffer) || !env->GetDirectBufferAddress(buffer) ||
        !env->GetDirectBufferAddress(calcBuffer) ||
        (reinterpret_cast<uintptr_t>(env->GetDirectBufferAddress(calcBuffer)) % alignof(uint32_t)) != 0) {
        jclass error = env->FindClass("java/lang/IllegalArgumentException");
        env->ThrowNew(error, "calcCDT requires valid dimensions and sufficiently sized direct buffers");
        return;
    }
    float imageWidth = width;
    float imageHeight = height;
    float _clipLimit = 1.25f;

    uint32_t totalSegments = PGPhotoEnhanceSegments * PGPhotoEnhanceSegments;
    uint32_t tileArea = (uint32_t) (floorf(imageWidth / PGPhotoEnhanceSegments) * floorf(imageHeight / PGPhotoEnhanceSegments));
    uint32_t clipLimit = (uint32_t) MAX(1, _clipLimit * tileArea / (float) PGPhotoEnhanceHistogramBins);
    float scale = 255.0f / (float) tileArea;

    unsigned char *bytes = (unsigned char *) env->GetDirectBufferAddress(hsvBuffer);
    uint32_t *calcBytes = (uint32_t *) env->GetDirectBufferAddress(calcBuffer);
    unsigned char *result = (unsigned char *) env->GetDirectBufferAddress(buffer);

    uint32_t *cdfsMin = calcBytes;
    calcBytes += totalSegments;
    uint32_t *cdfsMax = calcBytes;
    calcBytes += totalSegments;
    uint32_t *cdfs = calcBytes;
    calcBytes += totalSegments * PGPhotoEnhanceHistogramBins;
    uint32_t *hist = calcBytes;
    memset(hist, 0, sizeof(uint32_t) * totalSegments * PGPhotoEnhanceHistogramBins);

    float xMul = PGPhotoEnhanceSegments / imageWidth;
    float yMul = PGPhotoEnhanceSegments / imageHeight;

    uint32_t i, j;

    for (i = 0; i < imageHeight; i++) {
        uint32_t yOffset = i * width * 4;
        for (j = 0; j < imageWidth; j++) {
            uint32_t index = j * 4 + yOffset;

            uint32_t tx = (uint32_t)(j * xMul);
            uint32_t ty = (uint32_t)(i * yMul);
            uint32_t t = ty * PGPhotoEnhanceSegments + tx;

            hist[t * PGPhotoEnhanceHistogramBins + bytes[index + 2]]++;
        }
    }

    for (i = 0; i < totalSegments; i++) {
        if (clipLimit > 0) {
            uint32_t clipped = 0;
            for (j = 0; j < PGPhotoEnhanceHistogramBins; j++) {
                if (hist[i * PGPhotoEnhanceHistogramBins + j] > clipLimit) {
                    clipped += hist[i * PGPhotoEnhanceHistogramBins + j] - clipLimit;
                    hist[i * PGPhotoEnhanceHistogramBins + j] = clipLimit;
                }
            }

            uint32_t redistBatch = clipped / PGPhotoEnhanceHistogramBins;
            uint32_t residual = clipped - redistBatch * PGPhotoEnhanceHistogramBins;

            for (j = 0; j < PGPhotoEnhanceHistogramBins; j++) {
                hist[i * PGPhotoEnhanceHistogramBins + j] += redistBatch;
                if (j < residual) {
                    hist[i * PGPhotoEnhanceHistogramBins + j]++;
                }
            }
        }
        memcpy(cdfs + i * PGPhotoEnhanceHistogramBins, hist + i * PGPhotoEnhanceHistogramBins, PGPhotoEnhanceHistogramBins * sizeof(uint32_t));

        uint32_t hMin = PGPhotoEnhanceHistogramBins - 1;
        for (j = 0; j < hMin; ++j) {
            if (cdfs[i * PGPhotoEnhanceHistogramBins + j] != 0) {
                hMin = j;
            }
        }

        uint32_t cdf = 0;
        for (j = hMin; j < PGPhotoEnhanceHistogramBins; j++) {
            cdf += cdfs[i * PGPhotoEnhanceHistogramBins + j];
            cdfs[i * PGPhotoEnhanceHistogramBins + j] = (uint8_t) MIN(255, cdf * scale);
        }

        cdfsMin[i] = cdfs[i * PGPhotoEnhanceHistogramBins + hMin];
        cdfsMax[i] = cdfs[i * PGPhotoEnhanceHistogramBins + PGPhotoEnhanceHistogramBins - 1];
    }

    for (j = 0; j < totalSegments; j++) {
        uint32_t yOffset = j * PGPhotoEnhanceHistogramBins * 4;
        for (i = 0; i < PGPhotoEnhanceHistogramBins; i++) {
            uint32_t index = i * 4 + yOffset;
            result[index] = (uint8_t) cdfs[j * PGPhotoEnhanceHistogramBins + i];
            result[index + 1] = (uint8_t) cdfsMin[j];
            result[index + 2] = (uint8_t) cdfsMax[j];
            result[index + 3] = 255;
        }
    }
}
}
