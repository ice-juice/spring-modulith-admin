package com.sma.admin.common.internal.cache;

import java.io.IOException;
import java.net.*;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Test-only TCP fault injection; never stops or reconfigures the shared task Redis process. */
final class RedisFaultProxy implements AutoCloseable {
    private final ServerSocket listener;
    private final int targetPort;
    private final Set<Socket> sockets = ConcurrentHashMap.newKeySet();
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicBoolean disconnected = new AtomicBoolean(), closed = new AtomicBoolean();

    RedisFaultProxy(int targetPort) throws IOException {
        this.targetPort = targetPort;
        listener = new ServerSocket(0, 20, InetAddress.getByName("127.0.0.1"));
        workers.submit(() -> {
            while (!closed.get()) {
                try {
                    Socket client = listener.accept();
                    if (disconnected.get()) { client.close(); continue; }
                    sockets.add(client); workers.submit(() -> connect(client));
                } catch (IOException stopped) { if (!closed.get()) throw new IllegalStateException(stopped); }
            }
        });
    }
    int port() { return listener.getLocalPort(); }
    void disconnect() { disconnected.set(true); sockets.forEach(RedisFaultProxy::closeSocket); }
    void reconnect() { disconnected.set(false); }
    private void connect(Socket client) {
        Socket server = new Socket(); sockets.add(server);
        try {
            server.connect(new InetSocketAddress("127.0.0.1", targetPort), 1000);
            if (disconnected.get() || closed.get()) return;
            workers.submit(() -> pipe(server, client));
            pipe(client, server);
        } catch (IOException ignored) { /* Faults are intentional; no request/response bytes are logged. */ }
        finally { closeSocket(client); closeSocket(server); sockets.remove(client); sockets.remove(server); }
    }
    private static void pipe(Socket source, Socket target) {
        try { source.getInputStream().transferTo(target.getOutputStream()); }
        catch (IOException ignored) { }
        finally { closeSocket(source); closeSocket(target); }
    }
    private static void closeSocket(Socket socket) { try { socket.close(); } catch (IOException ignored) { } }
    @Override public void close() throws IOException {
        closed.set(true); listener.close(); disconnect(); workers.close();
    }
}
