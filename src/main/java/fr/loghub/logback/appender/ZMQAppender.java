package fr.loghub.logback.appender;

import java.io.OutputStream;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.Supplier;

import org.zeromq.SocketType;

import ch.qos.logback.core.OutputStreamAppender;
import fr.loghub.logservices.zmq.Logger;
import fr.loghub.logservices.zmq.Method;
import fr.loghub.logservices.zmq.Publisher;
import fr.loghub.logservices.zmq.ZMQConfiguration;
import lombok.Getter;
import lombok.Setter;

public class ZMQAppender<E> extends OutputStreamAppender<E> implements Logger {

    private class ZMQOutputStream extends OutputStream {
        @Override
        public void write(byte[] content) {
            if (!publisher.getLogQueue().offer(content)) {
                addError("Log event lost");
            }
        }

        @Override
        public void write(byte[] content, int off, int len) {
            write(Arrays.copyOfRange(content, off, Math.min(content.length, off + len)));
        }

        @Override
        public void flush() {
            // noop
        }

        @Override
        public void close() {
            publisher.close();
        }

        @Override
        public void write(int b) {
            if (publisher.getLogQueue().offer(new byte[]{(byte)b})) {
                addError("Log event lost");
            }
        }
    }

    private SocketType type = SocketType.PUB;
    private Method method = Method.CONNECT;
    @Getter @Setter
    private String endpoint = null;
    @Getter @Setter
    private int hwm = 1000;
    @Getter @Setter
    private long maxMsgSize = -1;
    @Getter @Setter
    private int linger;
    @Getter @Setter
    public String peerPublicKey;
    @Getter @Setter
    public String privateKeyFile;
    @Getter @Setter
    public String publicKey;
    private Publisher publisher;

    /**
     * Define the ØMQ socket type. Current allowed value are PUB or PUSH.
     *
     * @param type
     */
    public void setType(String type) {
        try {
            this.type = SocketType.valueOf(type.toUpperCase(Locale.ENGLISH));
        } catch (Exception e) {
            String msg = "[" + type + "] should be one of [PUSH, PUB]" + ", using default ZeroMQ socket type, PUSH by default.";
            addError(msg, e);
        }
    }

    /**
     * @return the ØMQ socket type.
     */
    public String getType() {
        return type.toString();
    }

    /**
     * The <b>method</b> define the connection method for the ØMQ socket. It can take the value
     * connect or bind, it's case-insensitive.
     * @param method
     */
    public void setMethod(String method) {
        try {
            this.method = Method.valueOf(method.toUpperCase(Locale.ENGLISH));
        } catch (Exception e) {
            String msg = "[" + type + "] should be one of [connect, bind]" + ", using default ZeroMQ socket type, connect by default.";
            addError(msg, e);
        }
    }

    @Override
    public void start() {
        if (endpoint == null) {
            addError("Unconfigured endpoint, the ZMQ appender can't log");
            return;
        }

        ZMQConfiguration<ZMQAppender<E>> config = new ZMQConfiguration<>(this, endpoint, type, method, hwm, maxMsgSize, linger,
                                                                         peerPublicKey, privateKeyFile, publicKey);
        publisher = new Publisher("Log4JZMQPublishingThread", this, config);
        setOutputStream(new ZMQOutputStream());
        super.start();
    }

    @Override
    public void warn(Supplier<String> message, Throwable t) {
        addWarn(message.toString(), t);
    }

    @Override
    public void error(Supplier<String> message, Throwable t) {
        addError(message.toString(), t);
    }

}
