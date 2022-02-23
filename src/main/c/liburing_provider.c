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

struct request {
    int fd;
    int event_type;
    long buffer_addr;
};

struct accept_request {
    int fd;
    int event_type;
    struct sockaddr_in client_addr;
    socklen_t client_addr_len;
};

JNIEXPORT jlong JNICALL
Java_sh_blake_niouring_IoUring_createCqes(JNIEnv *env, jclass cls, jint count) {
    struct io_uring_cqe **cqes = malloc(sizeof(struct io_uring_cqe *) * count);
    if (!cqes) {
        return throw_out_of_memory_error(env);
    }
    return cqes;
}

JNIEXPORT jlong JNICALL
Java_sh_blake_niouring_IoUring_create(JNIEnv *env, jclass cls, jint maxEvents) {
    struct io_uring *ring = malloc(sizeof(struct io_uring));
    if (!ring) {
        return throw_out_of_memory_error(env);
    }

    int ret = io_uring_queue_init(maxEvents, ring, 0);
    if (ret < 0) {
        return throw_exception(env, "io_uring_queue_init", ret);
    }

    return (uint64_t) ring;
}

JNIEXPORT jint JNICALL
Java_sh_blake_niouring_IoUring_submitAndGetCqes(JNIEnv *env, jclass cls, jlong ring_address, jlong cqes_address, jint cqes_size, jboolean should_wait) {
    struct io_uring *ring = (struct io_uring *) ring_address;

    int ret = io_uring_submit(ring);
    if (ret < 0) {
        if (ret != -EBUSY) {
            return throw_exception(env, "io_uring_submit", ret);
        }
    }

    struct io_uring_cqe **cqes = (struct io_uring_cqe **) cqes_address;
    ret = io_uring_peek_batch_cqe(ring, cqes, cqes_size);
    if (ret == 0 && should_wait) {
        ret = io_uring_wait_cqe(ring, cqes);
        if (ret < 0) {
            return throw_exception(env, "io_uring_wait_cqe", ret);
        }
        ret = 1;
    }

    return (int32_t) ret;
}

JNIEXPORT void JNICALL
Java_sh_blake_niouring_IoUring_freeCqes(JNIEnv *env, jclass cls, jlong cqes_address) {
    struct io_uring_cqe **cqes = (struct io_uring_cqe **) cqes_address;
    free(cqes);
}

JNIEXPORT jint JNICALL
Java_sh_blake_niouring_IoUring_getCqeEventType(JNIEnv *env, jclass cls, jlong cqes_address, jint cqe_index) {
    struct io_uring_cqe **cqes = (struct io_uring_cqe **) cqes_address;
    struct io_uring_cqe *cqe = cqes[cqe_index];
    struct request *req = (struct request *) cqe->user_data;
    return (int32_t) req->event_type;
}

JNIEXPORT jlong JNICALL
Java_sh_blake_niouring_IoUring_getCqeFd(JNIEnv *env, jclass cls, jlong cqes_address, jint cqe_index) {
    struct io_uring_cqe **cqes = (struct io_uring_cqe **) cqes_address;
    struct io_uring_cqe *cqe = cqes[cqe_index];
    struct request *req = (struct request *) cqe->user_data;
    return (uint64_t) req->fd;
}

JNIEXPORT jint JNICALL
Java_sh_blake_niouring_IoUring_getCqeResult(JNIEnv *env, jclass cls, jlong cqes_address, jint cqe_index) {
    struct io_uring_cqe **cqes = (struct io_uring_cqe **) cqes_address;
    struct io_uring_cqe *cqe = cqes[cqe_index];
    return (int32_t) cqe->res;
}

JNIEXPORT jlong JNICALL
Java_sh_blake_niouring_IoUring_getCqeBufferAddress(JNIEnv *env, jclass cls, jlong cqes_address, jint cqe_index) {
    struct io_uring_cqe **cqes = (struct io_uring_cqe **) cqes_address;
    struct io_uring_cqe *cqe = cqes[cqe_index];
    struct request *req = (struct request *) cqe->user_data;
    return (uint64_t) req->buffer_addr;
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
    struct request *req = cqe->user_data;

    free(req);
    io_uring_cqe_seen(ring, cqe);
}

JNIEXPORT jint JNICALL
Java_sh_blake_niouring_IoUring_queueAccept(JNIEnv *env, jclass cls, jlong ring_address, jlong server_socket_fd) {
    struct io_uring *ring = (struct io_uring *) ring_address;

    struct io_uring_sqe *sqe = io_uring_get_sqe(ring);
    if (sqe == NULL) {
        return throw_exception(env, "io_uring_get_sqe", -16);
    }

    struct accept_request *req = malloc(sizeof(*req));
    if (!req) {
        return throw_out_of_memory_error(env);
    }
    memset(&req->client_addr, 0, sizeof(req->client_addr));
    req->client_addr_len = sizeof(req->client_addr);
    req->event_type = EVENT_TYPE_ACCEPT;
    req->fd = server_socket_fd;

    io_uring_prep_accept(sqe, server_socket_fd, (struct sockaddr *) &req->client_addr, &req->client_addr_len, 0);
    io_uring_sqe_set_data(sqe, req);

    return 0;
}

JNIEXPORT jint JNICALL
Java_sh_blake_niouring_IoUring_queueConnect(JNIEnv *env, jclass cls, jlong ring_address, jlong socket_fd, jstring ip_address, jint port) {
    struct io_uring *ring = (struct io_uring *) ring_address;

    struct io_uring_sqe *sqe = io_uring_get_sqe(ring);
    if (sqe == NULL) {
        return throw_exception(env, "io_uring_get_sqe", -16);
    }

    struct accept_request *req = malloc(sizeof(*req));
    if (!req) {
        return throw_out_of_memory_error(env);
    }
    char *ip = (*env)->GetStringUTFChars(env, ip_address, NULL);
    memset(&req->client_addr, 0, sizeof(req->client_addr));
    req->client_addr.sin_addr.s_addr = inet_addr(ip);
    req->client_addr.sin_port = htons(port);
    req->client_addr.sin_family = AF_INET;
    req->client_addr_len = sizeof(req->client_addr);
    req->event_type = EVENT_TYPE_CONNECT;
    req->fd = socket_fd;

    io_uring_prep_connect(sqe, socket_fd, (struct sockaddr *) &req->client_addr, req->client_addr_len);
    io_uring_sqe_set_data(sqe, req);

    return 0;
}

JNIEXPORT jlong JNICALL
Java_sh_blake_niouring_IoUring_queueRead(JNIEnv *env, jclass cls, jlong ring_address, jlong fd, jobject byte_buffer, jint buffer_pos, jint buffer_len) {
    void *buffer = (*env)->GetDirectBufferAddress(env, byte_buffer);
    if (buffer == NULL) {
        return throw_exception(env, "invalid byte buffer (read)", -16);
    }

    struct io_uring *ring = (struct io_uring *) ring_address;
    if (io_uring_sq_space_left(ring) <= 1) {
        return throw_exception(env, "io_uring_sq_space_left", -16);
    }

    struct io_uring_sqe *sqe = io_uring_get_sqe(ring);
    if (sqe == NULL) {
        return throw_exception(env, "io_uring_get_sqe", -16);
    }

    struct request *req = malloc(sizeof(*req));
    if (!req) {
        return throw_out_of_memory_error(env);
    }
    req->event_type = EVENT_TYPE_READ;
    req->buffer_addr = buffer;
    req->fd = fd;

    io_uring_prep_read(sqe, fd, buffer, buffer_len, 0);
    io_uring_sqe_set_data(sqe, req);

    return (uint64_t) buffer;
}

JNIEXPORT jlong JNICALL
Java_sh_blake_niouring_IoUring_queueWrite(JNIEnv *env, jclass cls, jlong ring_address, jlong fd, jobject byte_buffer, jint buffer_pos, jint buffer_len) {
    void *buffer = (*env)->GetDirectBufferAddress(env, byte_buffer);
    if (buffer == NULL) {
        return throw_exception(env, "invalid byte buffer (write)", -16);
    }

    struct io_uring *ring = (struct io_uring *) ring_address;
    if (io_uring_sq_space_left(ring) <= 1) {
        return throw_exception(env, "io_uring_sq_space_left", -16);
    }

    struct io_uring_sqe *sqe = io_uring_get_sqe(ring);
    if (sqe == NULL) {
        return throw_exception(env, "io_uring_get_sqe", -16);
    }

    struct request *req = malloc(sizeof(*req));
    if (!req) {
        return throw_out_of_memory_error(env);
    }
    req->event_type = EVENT_TYPE_WRITE;
    req->buffer_addr = buffer;
    req->fd = fd;

    io_uring_prep_write(sqe, fd, buffer, buffer_len, 0);
    io_uring_sqe_set_data(sqe, req);

    return (uint64_t) buffer;
}

int throw_exception(JNIEnv *env, char *cause, int ret) {
    char error_msg[1024];
    snprintf(error_msg, sizeof(error_msg), "%s - %s", cause, strerror(-ret));
    return (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/RuntimeException"), &error_msg);
}

int throw_out_of_memory_error(JNIEnv *env) {
    return (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/OutOfMemoryError"), "Out of heap space");
}
