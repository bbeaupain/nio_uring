#ifndef _LIBURING_FILE_PROVIDER_DEFINED
#define _LIBURING_FILE_PROVIDER_DEFINED

#include <jni.h>

JNIEXPORT jint JNICALL
Java_sh_blake_niouring_IoUringFile_open(JNIEnv *env, jclass cls, jstring path);

#endif
