#ifndef _LIBURING_SOCKET_PROVIDER_DEFINED
#define _LIBURING_SOCKET_PROVIDER_DEFINED

#include <jni.h>
#include <stdint.h>

JNIEXPORT jint JNICALL
Java_sh_blake_niouring_AbstractIoUringSocket_create(JNIEnv *env, jclass cls);

JNIEXPORT void JNICALL
Java_sh_blake_niouring_IoUringServerSocket_bind(JNIEnv *env, jclass cls, jlong server_socket_fd, jstring host, jint port, jint backlog);

int32_t throw_buffer_overflow_exception(JNIEnv *env);

#endif
