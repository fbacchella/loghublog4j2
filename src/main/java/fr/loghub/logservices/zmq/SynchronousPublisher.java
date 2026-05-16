package fr.loghub.logservices.zmq;

import java.util.concurrent.atomic.AtomicReference;

import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;

import lombok.Getter;

class SynchronousPublisher implements Publisher {

    private final AtomicReference<ZMQ.Socket> socketHolder;
    private final ZContext ctx;
    private final ZMQConfiguration<?> config;
    @Getter
    private volatile boolean closed;
    private final Logger logger;

    SynchronousPublisher(Logger logger, ZMQConfiguration<?> config) {
        ctx = new ZContext(config.ioThreads);
        this.config = config;
        this.logger = logger;
        this.socketHolder = new AtomicReference<>();
    }

    synchronized Socket refreshSocket(Socket socket) {
        if (closed || ctx.isClosed()) {
            return null;
        } else if (socket == null) {
            socket = ctx.createSocket(config.type);
            config.configurator.configure(socket);
            String url = config.endpoint + ":" + config.type.toString() + ":" + config.method.getSymbol();
            socket.setIdentity(url.getBytes());
            config.configurator.configure(socket);
            config.method.act(socket, config.endpoint);
            return socket;
        } else {
            return socket;
        }
    }

    @Override
    public boolean send(byte[] log) {
        Socket socket = socketHolder.updateAndGet(this::refreshSocket);
        if (log != null && !closed) {
            try {
                socket.send(log, zmq.ZMQ.ZMQ_DONTWAIT);
            } catch (zmq.ZError.IOException | java.nio.channels.ClosedSelectorException | org.zeromq.ZMQException e) {
                // If it's not closed, drop the socket, to recreate a new one
                if (!closed) {
                    socket.close();
                    socket = null;
                }
                logger.warn(() -> String.format("Failed ZMQ connection %s: %s", config.endpoint, e.getMessage()), e);
            }
        }
        return true;
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        Socket socket = socketHolder.getAndSet(null);
        if (socket != null) {
            socket.close();
            ctx.destroy();
        }
    }

}
