#include "liburing_provider.h"
#include "liburing_socket_provider.h"

#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <netinet/in.h>
#include <liburing.h>
#include <string.h>
#include <errno.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <stdint.h>

#define EVENT_TYPE_ACCEPT   0
#define EVENT_TYPE_READ     1
#define EVENT_TYPE_WRITE    2
#define EVENT_TYPE_CONNECT  3
#define EVENT_TYPE_CLOSE    4

struct request {
    int32_t fd;
    int8_t event_type;
    int64_t buffer_addr;
};

struct accept_request {
    int32_t fd;
    int8_t event_type;
    struct sockaddr_in client_addr;
    socklen_t client_addr_len;
};

JNIEXPORT jlong JNICALL
Java_sh_blake_niouring_IoUring_createCqes(JNIEnv *env, jclass cls, jint count) {
    struct io_uring_cqe **cqes = malloc(sizeof(struct io_uring_cqe *) * count);
    if (!cqes) {
        throw_out_of_memory_error(env);
        return -1;
    }
    return (jlong) cqes;
}

JNIEXPORT jlong JNICALL
Java_sh_blake_niouring_IoUring_create(JNIEnv *env, jclass cls, jint maxEvents) {
    struct io_uring *ring = malloc(sizeof(struct io_uring));
    if (!ring) {
        throw_out_of_memory_error(env);
        return -1;
    }

    int32_t ret = io_uring_queue_init(maxEvents, ring, 0);
    if (ret < 0) {
        throw_exception(env, "io_uring_queue_init", ret);
        return -1;
    }

    return (uint64_t) ring;
}

JNIEXPORT void JNICALL
Java_sh_blake_niouring_IoUring_close(JNIEnv *env, jclass cls, jlong ring_address) {
    struct io_uring *ring = (struct io_uring *) ring_address;
    io_uring_queue_exit(ring);
}

JNIEXPORT jint JNICALL
Java_sh_blake_niouring_IoUring_submitAndGetCqes(JNIEnv *env, jclass cls, jlong ring_address, jobject byte_buffer, jlong cqes_address, jint cqes_size, jboolean should_wait) {
    struct io_uring *ring = (struct io_uring *) ring_address;

    int32_t ret = io_uring_submit(ring);
    if (ret < 0) {
        if (ret != -EBUSY) { // if busy, continue handling completions
            throw_exception(env, "io_uring_submit", ret);
            return -1;
        }
    }

    struct io_uring_cqe **cqes = (struct io_uring_cqe **) cqes_address;
    ret = io_uring_peek_batch_cqe(ring, cqes, cqes_size);
    if (ret == 0 && should_wait) {
        ret = io_uring_wait_cqe(ring, cqes);
        if (ret < 0) {
            throw_exception(env, "io_uring_wait_cqe", ret);
            return -1;
        }
        ret = 1;
    }

    char *buffer = (*env)->GetDirectBufferAddress(env, byte_buffer);
    if (buffer == NULL) {
        throw_exception(env, "invalid byte buffer (read)", -EINVAL);
        return -1;
    }

    long buf_capacity = (*env)->GetDirectBufferCapacity(env, byte_buffer);

    int32_t cqe_index = 0;
    int32_t buf_index = 0;
    while (cqe_index < ret) {
        struct io_uring_cqe *cqe = cqes[cqe_index];
        struct request *req = (struct request *) cqe->user_data;

        if (buf_index + 9 >= buf_capacity) {
            throw_buffer_overflow_exception(env);
            return -1;
        }

        buffer[buf_index++] = cqe->res >> 24;
        buffer[buf_index++] = cqe->res >> 16;
        buffer[buf_index++] = cqe->res >> 8;
        buffer[buf_index++] = cqe->res;

        buffer[buf_index++] = req->fd >> 24;
        buffer[buf_index++] = req->fd >> 16;
        buffer[buf_index++] = req->fd >> 8;
        buffer[buf_index++] = req->fd;

        buffer[buf_index++] = req->event_type;

        if (req->event_type == EVENT_TYPE_READ || req->event_type == EVENT_TYPE_WRITE) {
            if (buf_index + 8 >= buf_capacity) {
                throw_buffer_overflow_exception(env);
                return -1;
            }

            buffer[buf_index++] = req->buffer_addr >> 56;
            buffer[buf_index++] = req->buffer_addr >> 48;
            buffer[buf_index++] = req->buffer_addr >> 40;
            buffer[buf_index++] = req->buffer_addr >> 32;
            buffer[buf_index++] = req->buffer_addr >> 24;
            buffer[buf_index++] = req->buffer_addr >> 16;
            buffer[buf_index++] = req->buffer_addr >> 8;
            buffer[buf_index++] = req->buffer_addr;
        }

        cqe_index++;
    }

    return (int32_t) ret;
}

JNIEXPORT void JNICALL
Java_sh_blake_niouring_IoUring_freeCqes(JNIEnv *env, jclass cls, jlong cqes_address) {
    struct io_uring_cqe **cqes = (struct io_uring_cqe **) cqes_address;
    free(cqes);
}

JNIEXPORT jstring JNICALL
Java_sh_blake_niouring_IoUring_getCqeIpAddress(JNIEnv *env, jclass cls, jlong cqes_address, jint cqe_index) {
    struct io_uring_cqe **cqes = (struct io_uring_cqe **) cqes_address;
    struct io_uring_cqe *cqe = cqes[cqe_index];
    struct accept_request *req = (struct accept_request *) cqe->user_data;
    char *ip_address = inet_ntoa(req->client_addr.sin_addr);
    return (*env)->NewStringUTF(env, ip_address);
}

JNIEXPORT void JNICALL
Java_sh_blake_niouring_IoUring_markCqeSeen(JNIEnv *env, jclass cls, jlong ring_address, jlong cqes_address, jint cqe_index) {
    struct io_uring *ring = (struct io_uring *) ring_address;
    struct io_uring_cqe **cqes = (struct io_uring_cqe **) cqes_address;
    struct io_uring_cqe *cqe = cqes[cqe_index];
    struct request *req = (struct request *) cqe->user_data;

    free(req);
    io_uring_cqe_seen(ring, cqe);
}

JNIEXPORT void JNICALL
Java_sh_blake_niouring_IoUring_queueAccept(JNIEnv *env, jclass cls, jlong ring_address, jint server_socket_fd) {
    struct io_uring *ring = (struct io_uring *) ring_address;

    struct io_uring_sqe *sqe = io_uring_get_sqe(ring);
    if (sqe == NULL) {
        throw_exception(env, "io_uring_get_sqe", -EBUSY);
        return;
    }
    sqe->cancel_flags = IORING_ASYNC_CANCEL_FD;

    struct accept_request *req = malloc(sizeof(*req));
    if (!req) {
        throw_out_of_memory_error(env);
        return;
    }
    memset(&req->client_addr, 0, sizeof(req->client_addr));
    req->client_addr_len = sizeof(req->client_addr);
    req->event_type = EVENT_TYPE_ACCEPT;
    req->fd = server_socket_fd;

    io_uring_prep_accept(sqe, server_socket_fd, (struct sockaddr *) &req->client_addr, &req->client_addr_len, 0);
    io_uring_sqe_set_data(sqe, req);
}

JNIEXPORT void JNICALL
Java_sh_blake_niouring_IoUring_queueConnect(JNIEnv *env, jclass cls, jlong ring_address, jint socket_fd, jstring ip_address, jint port) {
    struct io_uring *ring = (struct io_uring *) ring_address;

    struct io_uring_sqe *sqe = io_uring_get_sqe(ring);
    if (sqe == NULL) {
        throw_exception(env, "io_uring_get_sqe", -EBUSY);
        return;
    }

    struct accept_request *req = malloc(sizeof(*req));
    if (!req) {
        throw_out_of_memory_error(env);
        return;
    }
    const char *ip = (*env)->GetStringUTFChars(env, ip_address, NULL);
    memset(&req->client_addr, 0, sizeof(req->client_addr));
    req->client_addr.sin_addr.s_addr = inet_addr(ip);
    req->client_addr.sin_port = htons(port);
    req->client_addr.sin_family = AF_INET;
    req->client_addr_len = sizeof(req->client_addr);
    req->event_type = EVENT_TYPE_CONNECT;
    req->fd = socket_fd;
    (*env)->ReleaseStringUTFChars(env, ip_address, ip);

    io_uring_prep_connect(sqe, socket_fd, (struct sockaddr *) &req->client_addr, req->client_addr_len);
    io_uring_sqe_set_data(sqe, req);
}

JNIEXPORT jlong JNICALL
Java_sh_blake_niouring_IoUring_queueRead(JNIEnv *env, jclass cls, jlong ring_address, jint fd, jobject byte_buffer, jint buffer_pos, jint buffer_len) {
    void *buffer = (*env)->GetDirectBufferAddress(env, byte_buffer);
    if (buffer == NULL) {
        throw_exception(env, "invalid byte buffer (read)", -EINVAL);
        return -1;
    }

    struct io_uring *ring = (struct io_uring *) ring_address;
    if (io_uring_sq_space_left(ring) <= 1) {
        throw_exception(env, "io_uring_sq_space_left", -EBUSY);
        return -1;
    }

    struct io_uring_sqe *sqe = io_uring_get_sqe(ring);
    if (sqe == NULL) {
        throw_exception(env, "io_uring_get_sqe", -EBUSY);
        return -1;
    }

    sqe->cancel_flags = IORING_ASYNC_CANCEL_FD;

    struct request *req = malloc(sizeof(*req));
    if (!req) {
        throw_out_of_memory_error(env);
        return -1;
    }
    req->event_type = EVENT_TYPE_READ;
    req->buffer_addr = (int64_t) buffer;
    req->fd = fd;

    io_uring_prep_read(sqe, fd, buffer + buffer_pos, buffer_len, 0);
    io_uring_sqe_set_data(sqe, req);

    return (uint64_t) buffer;
}

JNIEXPORT jlong JNICALL
Java_sh_blake_niouring_IoUring_queueWrite(JNIEnv *env, jclass cls, jlong ring_address, jint fd, jobject byte_buffer, jint buffer_pos, jint buffer_len) {
    void *buffer = (*env)->GetDirectBufferAddress(env, byte_buffer);
    if (buffer == NULL) {
        throw_exception(env, "invalid byte buffer (write)", -EINVAL);
        return -1;
    }

    struct io_uring *ring = (struct io_uring *) ring_address;
    if (io_uring_sq_space_left(ring) <= 1) {
        throw_exception(env, "io_uring_sq_space_left", -EBUSY);
        return -1;
    }

    struct io_uring_sqe *sqe = io_uring_get_sqe(ring);
    if (sqe == NULL) {
        throw_exception(env, "io_uring_get_sqe", -EBUSY);
        return -1;
    }
    sqe->cancel_flags = IORING_ASYNC_CANCEL_FD;

    struct request *req = malloc(sizeof(*req));
    if (!req) {
        throw_out_of_memory_error(env);
        return -1;
    }
    req->event_type = EVENT_TYPE_WRITE;
    req->buffer_addr = (int64_t) buffer;
    req->fd = fd;

    io_uring_prep_write(sqe, fd, buffer + buffer_pos, buffer_len, 0);
    io_uring_sqe_set_data(sqe, req);

    return (uint64_t) buffer;
}

JNIEXPORT void JNICALL
Java_sh_blake_niouring_IoUring_queueClose(JNIEnv *env, jclass cls, jlong ring_address, jint fd) {
    struct io_uring *ring = (struct io_uring *) ring_address;
    if (io_uring_sq_space_left(ring) <= 1) {
        throw_exception(env, "io_uring_sq_space_left", -EBUSY);
        return;
    }

    struct io_uring_sqe *sqe = io_uring_get_sqe(ring);
    if (sqe == NULL) {
        throw_exception(env, "io_uring_get_sqe", -EBUSY);
        return;
    }
    sqe->cancel_flags = IORING_ASYNC_CANCEL_FD;

    struct request *req = malloc(sizeof(*req));
    if (!req) {
        throw_out_of_memory_error(env);
        return;
    }
    req->event_type = EVENT_TYPE_CLOSE;
    req->fd = fd;

    io_uring_prep_close(sqe, fd);
    io_uring_sqe_set_data(sqe, req);
}

JNIEXPORT void JNICALL
Java_sh_blake_niouring_AbstractIoUringChannel_close(JNIEnv *env, jclass cls, jint fd) {
    shutdown(fd, SHUT_WR);
    close(fd);
}

int32_t throw_exception(JNIEnv *env, char *cause, int32_t ret) {
    char error_msg[1024];
    snprintf(error_msg, sizeof(error_msg), "%s - %s", cause, strerror(-ret));
    return (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/RuntimeException"), (const char *) &error_msg);
}

int32_t throw_out_of_memory_error(JNIEnv *env) {
    return (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/OutOfMemoryError"), "Out of heap space");
}

int32_t throw_buffer_overflow_exception(JNIEnv *env) {
    return (*env)->ThrowNew(env, (*env)->FindClass(env, "java/nio/BufferOverflowException"), "Buffer overflow");
}
