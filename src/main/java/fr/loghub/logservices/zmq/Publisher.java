package fr.loghub.logservices.zmq;

import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;

import lombok.Getter;

public class Publisher extends Thread {

    private ZMQ.Socket socket;
    private final ZContext ctx;
    private final ZMQConfiguration config;
    @Getter
    private final BlockingQueue<byte[]> logQueue;
    private volatile boolean closed;
    private final Logger logger;

    public Publisher(String name, Logger logger, ZMQConfiguration config) {
        ctx = new ZContext(1);
        ctx.setLinger(0);
        this.config = config;
        this.logger = logger;
        logQueue = new ArrayBlockingQueue<>(config.getHwm());
        setName(name);
        setDaemon(true);
        setUncaughtExceptionHandler(this::exceptionHandler);
        closed = false;
        start();
    }

    @Override
    public void run() {
        try {
            while (! closed) {
                // First check if socket is null.
                // It might be the first iteration, or the previous socket badly failed and was dropped
                synchronized (this) {
                    if (socket == null) {
                        socket = newSocket(config.getMethod(), config.getType(), config.getEndpoint(), config.getHwm(), -1);
                        if (socket == null) {
                            // No socket returned, appender was closed
                            break;
                        }
                        Optional.of(config.getMaxMsgSize()).filter(i -> i > 0).ifPresent(socket::setMaxMsgSize);
                        Optional.of(config.getLinger()).filter(i -> i > 0).ifPresent(socket::setLinger);
                    }
                }
                // Not a blocking wait, it allows to test if closed every 100 ms
                // Needed because interrupt deactivated for this thread
                byte[] log = logQueue.poll(100, TimeUnit.MILLISECONDS);
                if (log != null) {
                    try {
                        synchronized (this) {
                            if (!closed) {
                                socket.send(log, zmq.ZMQ.ZMQ_DONTWAIT);
                            }
                        }
                    } catch (zmq.ZError.IOException | java.nio.channels.ClosedSelectorException | org.zeromq.ZMQException e) {
                        synchronized (this) {
                            // If it's not closed, drop the socket, to recreate a new one
                            if (!closed) {
                                socket.close();
                                socket = null;
                            }
                        }
                        logger.warn(() -> String.format("Failed ZMQ connection %s: %s", config.getEndpoint(), e.getMessage()), e);
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
    public synchronized void interrupt() {
        closed = true;
    }

    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        socket.close();
        socket = null;
        ctx.destroy();
    }

    private synchronized Socket newSocket(Method method, SocketType type, String endpoint, int hwm, int timeout) {
        if (closed || ctx.isClosed()) {
            return null;
        } else {
            Socket newsocket = ctx.createSocket(type);
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

    private void exceptionHandler(Thread t, Throwable ex) {
        logger.error(() -> "Critical exception: " + ex.getMessage(), ex);
    }
}
