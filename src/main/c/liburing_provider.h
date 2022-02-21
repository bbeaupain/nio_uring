#ifndef _LIBURING_PROVIDER_DEFINED
#define _LIBURING_PROVIDER_DEFINED

#include <jni.h>

JNIEXPORT jlong JNICALL
Java_sh_blake_niouring_IoUring_createCqes(JNIEnv *env, jclass cls, jint count);

JNIEXPORT void JNICALL
Java_sh_blake_niouring_IoUring_freeCqes(JNIEnv *env, jclass cls, jlong cqes_address);

JNIEXPORT jlong JNICALL
Java_sh_blake_niouring_IoUring_create(JNIEnv *env, jclass cls, jint maxEvents);

JNIEXPORT jint JNICALL
Java_sh_blake_niouring_IoUring_submitAndGetCqes(JNIEnv *env, jclass cls, jlong ring_address, jlong cqes_address, jint cqes_size, jboolean should_wait);

JNIEXPORT jint JNICALL
Java_sh_blake_niouring_IoUring_getCqeEventType(JNIEnv *env, jclass cls, jlong cqes_address, jint cqe_index);

JNIEXPORT jlong JNICALL
Java_sh_blake_niouring_IoUring_getCqeFd(JNIEnv *env, jclass cls, jlong cqes_address, jint cqe_index);

JNIEXPORT jint JNICALL
Java_sh_blake_niouring_IoUring_getCqeResult(JNIEnv *env, jclass cls, jlong cqes_address, jint cqe_index);

JNIEXPORT jlong JNICALL
Java_sh_blake_niouring_IoUring_getCqeBufferAddress(JNIEnv *env, jclass cls, jlong cqes_address, jint cqe_index);

JNIEXPORT void JNICALL
Java_sh_blake_niouring_IoUring_markCqeSeen(JNIEnv *env, jclass cls, jlong ring_address, jlong cqes_address, jint cqe_index);

JNIEXPORT jint JNICALL
Java_sh_blake_niouring_IoUring_queueAccept(JNIEnv *env, jclass cls, jlong ring_address, jlong server_socket_fd);

JNIEXPORT jlong JNICALL
Java_sh_blake_niouring_IoUring_queueRead(JNIEnv *env, jclass cls, jlong ring_address, jlong socket_fd, jobject byte_buffer, jint buffer_pos, jint buffer_len);

JNIEXPORT jlong JNICALL
Java_sh_blake_niouring_IoUring_queueWrite(JNIEnv *env, jclass cls, jlong ring_address, jlong socket_fd, jobject byte_buffer, jint buffer_pos, jint buffer_len);

int throw_io_exception(JNIEnv *env, char *cause, int ret);
int throw_out_of_memory_error(JNIEnv *env);

#endif
