package fr.loghub.log4j2.appender.zmq;

import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.message.FormattedMessage;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;

import lombok.Getter;

class Publisher extends Thread {

    private ZMQ.Socket socket;
    // If the appender uses it's own context, it must terminate it itself
    private final boolean localCtx;
    private final ZContext ctx;
    private final ZMQConfiguration config;
    @Getter
    private final BlockingQueue<byte[]> logQueue;
    private boolean closed;
    private ZMQManager manager;

    Publisher(ZMQManager manager, ZMQConfiguration config) {
        ctx = new ZContext(1);
        ctx.setLinger(0);
        localCtx = true;
        this.config = config;
        logQueue = new ArrayBlockingQueue<>(config.getHwm());
        setName("Log4J2ZMQPublishingThread");
        setDaemon(true);
        closed = false;
        this.manager = manager;
        start();
    }

    @Override
    public void run() {
        try {
            while (! closed) {
                // First check if socket is null.
                // It might be the first iteration, or the previous socket badly failed and was dropped
                if (socket == null) {
                    socket = newSocket(config.getMethod(), config.getType(), config.getEndpoint(), config.getHwm(), -1);
                    if (socket == null) {
                        // No socket returned, appender was closed
                        break;
                    }
                    Optional.of(config.getMaxMsgSize()).filter(i -> i > 0).ifPresent(socket::setMaxMsgSize);
                    Optional.of(config.getLinger()).filter(i -> i > 0).ifPresent(socket::setLinger);
                }
                // Not a blocking wait, it allows to test if closed every 100 ms
                // Needed because interrupt deactivated for this thread
                byte[] log = logQueue.poll(100, TimeUnit.MILLISECONDS);
                if (log != null) {
                    try {
                        synchronized (ctx) {
                            if (!closed) {
                                socket.send(log, zmq.ZMQ.ZMQ_DONTWAIT);
                            }
                        }
                    } catch (zmq.ZError.IOException | java.nio.channels.ClosedSelectorException | org.zeromq.ZMQException e) {
                        // Using hash code if OnlyOnceErrorHandler is used
                        //errorHandler.error("Failed ZMQ socket", e, socket.hashCode());
                        synchronized (ctx) {
                            // If it's not closed, drop the socket, to recreate a new one
                            if (!closed) {
                                ctx.destroySocket(socket);
                                socket = null;
                            }
                        }
                        manager.log(Level.WARN, new FormattedMessage("Failed ZMQ connection {}: {}", config.getEndpoint(), e.getMessage()), e);
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

    private Socket newSocket(Method method, SocketType type, String endpoint, int hwm, int timeout) {
        synchronized (ctx) {
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
    }

}
