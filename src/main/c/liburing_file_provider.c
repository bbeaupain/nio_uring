#include "liburing_file_provider.h"
#include "liburing_provider.h"

#include <jni.h>
#include <stdint.h>
#include <fcntl.h>
#include <stdio.h>
#include <string.h>

JNIEXPORT jlong JNICALL
Java_sh_blake_niouring_IoUringFile_open(JNIEnv *env, jclass cls, jstring path) {
    char *file_path = (*env)->GetStringUTFChars(env, path, NULL);
    int ret = open(file_path, O_RDWR);
    if (ret < 0) {
        ret = open(file_path, O_CREAT, 0666);
        if (ret < 0) {
            return throw_exception(env, "open", ret);
        }
    }
    return (uint64_t) ret;
}
