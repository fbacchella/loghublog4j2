package fr.loghub.log4j1.zmq;

import java.util.Locale;
import java.util.function.Supplier;

import org.apache.log4j.spi.ErrorCode;
import org.zeromq.SocketType;

import fr.loghub.log4j1.serializer.SerializerAppender;
import fr.loghub.logservices.zmq.Logger;
import fr.loghub.logservices.zmq.Method;
import fr.loghub.logservices.zmq.Publisher;
import fr.loghub.logservices.zmq.ZMQConfiguration;
import lombok.Getter;
import lombok.Setter;

public class ZMQAppender extends SerializerAppender implements Logger {
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
    @Getter @Setter
    public int timeout;
    private Publisher publisher;

    @Override
    protected void subOptions() {
        if (endpoint == null) {
            errorHandler.error("Unconfigured endpoint, the ZMQ appender can't log");
            return;
        }
        ZMQConfiguration<ZMQAppender> config = new ZMQConfiguration<>(this, endpoint, type, method, hwm, maxMsgSize, linger,
                                                         peerPublicKey, privateKeyFile, publicKey, timeout);
        publisher = new Publisher("Log4J1ZMQPublishingThread", this, config);
    }

    @Override
    protected void send(byte[] content) {
        if (!publisher.getLogQueue().offer(content)) {
            errorHandler.error("Log event lost");
        }
    }

    @Override
    public void close() {
        publisher.close();
    }

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
            errorHandler.error(msg, e, ErrorCode.GENERIC_FAILURE);
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
            this.method = Method.valueOf(method.toUpperCase());
        } catch (Exception e) {
            String msg = "[" + type + "] should be one of [connect, bind]" + ", using default ZeroMQ socket type, connect by default.";
            errorHandler.error(msg, e, ErrorCode.GENERIC_FAILURE);
        }
    }

    @Override
    public void warn(Supplier<String> message, Throwable t) {
        errorHandler.error(message.get());
    }

    @Override
    public void error(Supplier<String> message, Throwable ex) {
        errorHandler.error(message.get());
    }
}
