#include "liburing_socket_provider.h"
#include "liburing_provider.h"

#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <liburing.h>
#include <unistd.h>
#include <string.h>
#include <stdint.h>

JNIEXPORT jint JNICALL
Java_sh_blake_niouring_AbstractIoUringSocket_create(JNIEnv *env, jclass cls) {
    int32_t val = 1;

    int32_t fd = socket(PF_INET, SOCK_STREAM, 0);
    if (fd == -1) {
        return throw_exception(env, "socket", fd);
    }

    int32_t ret = setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, &val, sizeof(int));
    if (ret < 0) {
        return throw_exception(env, "setsockopt", ret);
    }

    ret = setsockopt(fd, SOL_SOCKET, SO_REUSEPORT, &val, sizeof(val));
    if (ret == -1) {
        return throw_exception(env, "setsockopt", ret);
    }

    return (uint32_t) fd;
}

JNIEXPORT void JNICALL
Java_sh_blake_niouring_IoUringServerSocket_bind(JNIEnv *env, jclass cls, jlong server_socket_fd, jstring ip_address, jint port, jint backlog) {
    char *ip = (*env)->GetStringUTFChars(env, ip_address, NULL);

    struct sockaddr_in srv_addr;
    memset(&srv_addr, 0, sizeof(srv_addr));
    srv_addr.sin_family = AF_INET;
    srv_addr.sin_port = htons(port);
    srv_addr.sin_addr.s_addr = inet_addr(ip);

    (*env)->ReleaseStringUTFChars(env, ip_address, ip);

    int32_t ret = bind(server_socket_fd, (const struct sockaddr *) &srv_addr, sizeof(srv_addr));
    if (ret < 0) {
        return throw_exception(env, "bind", ret);
    }

    ret = listen(server_socket_fd, backlog);
    if (ret < 0) {
        return throw_exception(env, "io_uring_get_sqe", -16);
    }
}
