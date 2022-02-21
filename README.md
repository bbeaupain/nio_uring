# nio_uring

`nio_uring` is an I/O library for Java that uses [io_uring](https://en.wikipedia.org/wiki/Io_uring) under the hood, which aims to be:

* A simple and flexible API
* Super fast and efficient
* Truly zero-copy (the kernel addresses direct `ByteBuffer`s for I/O operations)
* Slightly opinionated

## Requirements
* Linux >= 5.1
* Java >= 8

For both of these, the higher the version the better - free performance!

## Example

Here's a super basic HTTP server from `sh.blake.niouring.examples.HttpHelloWorldTest`. There are a few other examples in the same package too.

```java
public static void main(String[] args) {
    IoUringServerSocket serverSocket = IoUringServerSocket.bind(8080).onAccept(socket -> {
        socket.onRead(in -> {
            String response = "HTTP/1.1 200 OK\r\n\r\nHello, world!";
            ByteBuffer buffer = ByteBufferUtil.wrapDirect(response);
            socket.queueWrite(buffer);
        });
        socket.queueRead(ByteBuffer.allocateDirect(1024));
        socket.onWrite(out -> socket.close());
        socket.onException(ex -> socket.close());
    });
    new IoUring()
        .onException(Exception::printStackTrace)
        .queueAccept(serverSocket)
        .loop();
}
```

## Maven Usage

Is on the way!

## Performance Tuning

Pretty much all performance tuning is done with one knob - the `ringSize` argument to the `IoUring` constructor, which has a default value of 512 if not provided. This value controls the number of outstanding I/O events (accepts, reads, and writes) at any given time. It is constrained by `memlock` limits (`ulimit -l`) which can be increased as necessary. Don't forget about file descriptor limits (`ulimit -n`) too!

Beyond this, you will have to run multiple rings across multiple threads. See `sh.blake.niouring.examples.ParallelHttpEchoTest` for a simple starter using `java.util.concurrent` APIs.

## File Support

Is on the way and will provide zero-copy operations with sockets!

## Caveats / Warnings

### Thread safety

As of now, you should only call read/write/close operations from an `IoUring` handler (`onAccept`, `onRead`, `onWrite`, etc). This is because `nio_uring` uses `liburing` under the hood and its internal submission/completion system is shared and not thread safe. We understand this is an important feature and are working on an efficient way to wake up the main ring loop and have it wait for external threads to perform modifications before resuming.

### Runtime Exceptions

`nio_uring` holds the opinion that checked exceptions, as a concept, were probably a mistake. Therefore, all exceptions produced by `nio_uring` will extend `java.lang.RuntimeException` and will always be forwarded to the appropriate exception handler if generated intentionally (see `socket.onException`).

### Direct buffers

All `ByteBuffer` instances used with this library must be direct (e.g. allocated with `allocateDirect`). This is a hard requirement and is what enables zero-copy functionality.

### Multiple reads/writes
Queuing multiple operations is fine, but try not to queue an read/write operation for _the same_ buffer to the _same_ socket more than once per ring execution, because the internal mapping system is not designed to handle this. The data will be read/written, but order is not guaranteed, and an `java.lang.IllegalStateException` exception with message "Buffer already removed" will be thrown.

## Building

Set the `LIBURING_PATH` environment variable to the root of a fully compiled liburing directory.

Then `./gradlew build` and you're off!

## License

MIT. Have fun and make cool things!
