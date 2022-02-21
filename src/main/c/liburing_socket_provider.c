#include "liburing_socket_provider.h"
#include "liburing_provider.h"

#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <netinet/in.h>
#include <liburing.h>

JNIEXPORT jlong JNICALL
Java_sh_blake_niouring_IoUringServerSocket_create(JNIEnv *env, jclass cls) {
    int sock;

    sock = socket(PF_INET, SOCK_STREAM, 0);
    if (sock == -1) {
        return throw_exception(env, "socket", sock);
    }

    int enable = 1;
    int ret = setsockopt(sock, SOL_SOCKET, SO_REUSEADDR, &enable, sizeof(int));
    if (ret < 0) {
        return throw_exception(env, "setsockopt", ret);
    }

    return (long) sock;
}

JNIEXPORT jint JNICALL
Java_sh_blake_niouring_IoUringServerSocket_bind(JNIEnv *env, jclass cls, jlong server_socket_fd, jstring host, jint port, jint backlog) {
    struct sockaddr_in srv_addr;
    memset(&srv_addr, 0, sizeof(srv_addr));
    srv_addr.sin_family = AF_INET;
    srv_addr.sin_port = htons(port);
    srv_addr.sin_addr.s_addr = htonl(INADDR_ANY);

    int ret = bind(server_socket_fd, (const struct sockaddr *) &srv_addr, sizeof(srv_addr));
    if (ret < 0) {
        return throw_exception(env, "bind", ret);
    }

    ret = listen(server_socket_fd, backlog);
    if (ret < 0) {
        return throw_exception(env, "io_uring_get_sqe", -16);
    }

    return 0;
}

JNIEXPORT void JNICALL
Java_sh_blake_niouring_IoUringSocket_close(JNIEnv *env, jclass cls, jlong socket_fd) {
    close(socket_fd);
}
