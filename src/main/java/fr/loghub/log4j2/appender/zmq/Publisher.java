package fr.loghub.log4j2.appender.zmq;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import zmq.socket.Sockets;

@Accessors(chain=true)
public class Publisher extends Thread {

    public static class Builder {
        private Builder() {
        }
        @Setter
        private Sockets type = Sockets.PUB;
        @Setter
        private Method method = Method.CONNECT;
        @Setter
        private String endpoint = null;
        @Setter
        private int hwm = 1000;

        public Publisher build() {
            return new Publisher(this);
        }
    }

    public static Builder getBuilder() {
        return new Builder();
    }

    private ZMQ.Socket socket;
    // If the appender uses it's own context, it must terminate it itself
    private final boolean localCtx;
    private final ZContext ctx;
    private final Sockets type;
    private final Method method;
    private final String endpoint;
    private final int hwm;
    @Getter
    private final BlockingQueue<byte[]> logQueue;
    private volatile boolean closed;

    private Publisher(Builder builder) {
        ctx = new ZContext(1);
        ctx.setLinger(0);
        localCtx = true;
        type = builder.type;
        method = builder.method;
        endpoint = builder.endpoint;
        hwm = builder.hwm;
        logQueue = new ArrayBlockingQueue<>(hwm);
        setName("Log4J2ZMQPublishingThread");
        setDaemon(true);
        // Workaround https://github.com/zeromq/jeromq/issues/545
        ctx.getContext();
        closed = false;
        start();
    }

    @Override
    public void run() {
        try {
            while (! isClosed()) {
                // First check if socket is null.
                // It might be the first iteration, or the previous socket badly failed and was dropped
                if (socket == null) {
                    socket = newSocket(method, type, endpoint, hwm, -1);
                    if (socket == null) {
                        // No socket returned, appender was closed
                        break;
                    }
                    socket.setLinger(100);
                }
                // Not a blocking wait, it allows to test if closed every 100 ms
                // Needed because interrupt deactivated for this thread
                byte[] log = logQueue.poll(100, TimeUnit.MILLISECONDS);
                if (log != null) {
                    try {
                        synchronized (ctx) {
                            if (!isClosed()) {
                                boolean sended = socket.send(log, zmq.ZMQ.ZMQ_DONTWAIT);
                                // An assert to failed during tests but not during run
                                assert sended : "failed sending";
                            }
                        }
                    } catch (zmq.ZError.IOException | java.nio.channels.ClosedSelectorException | org.zeromq.ZMQException e) {
                        // Using hash code if OnlyOnceErrorHandler is used
                        //errorHandler.error("Failed ZMQ socket", e, socket.hashCode());
                        synchronized (ctx) {
                            // If it's not closed, drop the socket, to recreate a new one
                            if (!isClosed()) {
                                ctx.destroySocket(socket);
                                socket = null;
                            }
                        }
                    } 
                }
            }
        } catch (InterruptedException e) {
            // Interrupt deactivated, so never happens
        }
        close();
    }

    /* Don't interrupt a ZMQ thread, just finished it
     * @see java.lang.Thread#interrupt()
     */
    @Override
    public void interrupt() {
        closed = true;
    }

    private boolean isClosed() {
        return closed;
    }

    public void close() {
        if (isClosed()) {
            return;
        }
        synchronized (ctx) {
            synchronized(this) {
                closed = true;
            }
            ctx.destroySocket(socket);
            socket = null;
            if(localCtx) {
                ctx.destroy();
            }
        }
    }

    private Socket newSocket(Method method, Sockets type, String endpoint, int hwm, int timeout) {
        synchronized (ctx) {
            if (isClosed() || ctx.isClosed()) {
                return null;
            } else {
                Socket newsocket = ctx.createSocket(type.ordinal());
                newsocket.setRcvHWM(hwm);
                newsocket.setSndHWM(hwm);
                newsocket.setSendTimeOut(timeout);
                newsocket.setReceiveTimeOut(timeout);

                method.act(newsocket, endpoint);
                String url = endpoint + ":" + type.toString() + ":" + method.getSymbol();
                newsocket.setIdentity(url.getBytes());
                return newsocket;
            }
        }
    }

}
