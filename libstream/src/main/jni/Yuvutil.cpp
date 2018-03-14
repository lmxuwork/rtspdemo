#include <jni.h>
#include <string.h>

extern "C"
JNIEXPORT void JNICALL
Java_cn_broadsense_newrecorder_streaming_util_JNIUtil_reverseUV(JNIEnv *env, jobject instance,
                                                                jbyteArray data_, jint width,
                                                                jint height) {
    jbyte *data = env->GetByteArrayElements(data_, NULL);

    int frameSize = width * height;
    int vFrameSize = frameSize >> 2; //u v 长度

    for (int i = 0; i < vFrameSize; i++) {
        jbyte tmpU = data[frameSize + i];//之前的U位置赋值为V
        data[frameSize + i] = data[frameSize + vFrameSize + i];//之前的V位置赋值为U
        data[frameSize + vFrameSize + i] = tmpU;//之前的U位置赋值为V
    }
    env->ReleaseByteArrayElements(data_, data, 0);
}