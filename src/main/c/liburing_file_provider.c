#include "liburing_file_provider.h"
#include "liburing_provider.h"

#include <jni.h>
#include <stdint.h>
#include <fcntl.h>
#include <stdio.h>
#include <string.h>
#include <stdint.h>

JNIEXPORT jint JNICALL
Java_sh_blake_niouring_IoUringFile_open(JNIEnv *env, jclass cls, jstring path) {
    char *file_path = (*env)->GetStringUTFChars(env, path, NULL);
    int32_t ret = open(file_path, O_RDWR);
    if (ret < 0) {
        ret = open(file_path, O_CREAT, 0666);
        if (ret < 0) {
            return throw_exception(env, "open", ret);
        }
    }
    (*env)->ReleaseStringUTFChars(env, path, file_path);
    return (int32_t) ret;
}
