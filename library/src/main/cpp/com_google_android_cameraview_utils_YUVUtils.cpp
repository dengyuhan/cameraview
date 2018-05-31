#include "com_google_android_cameraview_utils_YUVUtils.h"
#include <string.h>

//nv21格式转为nv12格式
JNIEXPORT void JNICALL Java_com_google_android_cameraview_utils_YUVUtils_nativeNV21ToNV12
        (JNIEnv *env, jclass jcls, jbyteArray jnv21, jbyteArray jnv12, jint jwidth, jint jheight) {
    int iSize = jwidth * jheight;
    jsize nv21Len = env->GetArrayLength(jnv21);
    jsize nv12Len = env->GetArrayLength(jnv12);
    if (nv21Len <= 0 || nv12Len <= 0)
        return;
    jbyte *jnv21Data = env->GetByteArrayElements(jnv21, 0);
    jbyte *jnv12Data = env->GetByteArrayElements(jnv12, 0);

    unsigned char *nv21 = (unsigned char *) jnv21Data;
    unsigned char *nv12 = (unsigned char *) jnv12Data;

    //拷贝Y分量
    memcpy(nv12, nv21, iSize);

    for (int i = 0; i < iSize / 4; i++) {
        nv12[iSize + i * 2] = nv21[iSize + i * 2 + 1]; //U
        nv12[iSize + i * 2 + 1] = nv21[iSize + i * 2]; //V
    }

    env->ReleaseByteArrayElements(jnv21, jnv21Data, 0);
    env->ReleaseByteArrayElements(jnv12, jnv12Data, 0);
    return;
}

//YUV420P图像顺时针旋转90度
JNIEXPORT jbyteArray JNICALL
Java_com_google_android_cameraview_utils_YUVUtils_nativeRotateYUV420Degree90
        (JNIEnv *env, jclass jcls, jbyteArray jsrc, jint jwidth, jint jheight) {
    int w = jwidth;
    int h = jheight;
    jsize srcLen = env->GetArrayLength(jsrc);
    if (srcLen <= 0)
        return NULL;

    jbyteArray jdest = env->NewByteArray(w * h * 3 / 2);

    jbyte *jsrcData = env->GetByteArrayElements(jsrc, 0);
    jbyte *jdestData = env->GetByteArrayElements(jdest, 0);

    unsigned char *src = (unsigned char *) jsrcData;
    unsigned char *dest = (unsigned char *) jdestData;

    // Rotate the Y luma
    int i = 0;
    for (int x = 0; x < w; x++) {
        for (int y = h - 1; y >= 0; y--) {
            dest[i] = src[y * w + x];
            i++;
        }

    }
    // Rotate the U and V color components
    i = w * h * 3 / 2 - 1;
    for (int x = w - 1; x > 0; x = x - 2) {
        for (int y = 0; y < h / 2; y++) {
            dest[i] = src[(w * h) + (y * w) + x];
            i--;
            dest[i] = src[(w * h) + (y * w) + (x - 1)];
            i--;
        }
    }

    env->ReleaseByteArrayElements(jsrc, jsrcData, 0);
    env->ReleaseByteArrayElements(jdest, jdestData, 0);
    return jdest;
}
