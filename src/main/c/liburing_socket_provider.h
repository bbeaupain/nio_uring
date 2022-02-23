#ifndef _LIBURING_SOCKET_PROVIDER_DEFINED
#define _LIBURING_SOCKET_PROVIDER_DEFINED

#include <jni.h>

JNIEXPORT jlong JNICALL
Java_sh_blake_niouring_AbstractIoUringSocket_create(JNIEnv *env, jclass cls);

JNIEXPORT jint JNICALL
Java_sh_blake_niouring_IoUringServerSocket_bind(JNIEnv *env, jclass cls, jlong server_socket_fd, jstring host, jint port, jint backlog);

#endif
