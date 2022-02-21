#ifndef _LIBURING_SOCKET_PROVIDER_DEFINED
#define _LIBURING_SOCKET_PROVIDER_DEFINED

#include <jni.h>

JNIEXPORT jlong JNICALL
Java_sh_niouring_core_IoUringServerSocket_create(JNIEnv *env, jclass cls);

JNIEXPORT jint JNICALL
Java_sh_niouring_core_IoUringServerSocket_bind(JNIEnv *env, jclass cls, jlong server_socket_fd, jstring host, jint port, jint backlog);

JNIEXPORT void JNICALL
Java_sh_niouring_core_IoUringSocket_close(JNIEnv *env, jclass cls, jlong socket_fd);

#endif
