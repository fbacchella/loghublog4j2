package fr.loghub.logback.appender;

import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.Supplier;

import org.zeromq.Method;
import org.zeromq.SocketConfigurator;
import org.zeromq.SocketType;

import ch.qos.logback.core.OutputStreamAppender;
import fr.loghub.logservices.zmq.Logger;
import fr.loghub.logservices.zmq.Publisher;
import fr.loghub.logservices.zmq.ZMQConfiguration;
import lombok.Getter;
import lombok.Setter;

public class ZMQAppender<E> extends OutputStreamAppender<E> implements Logger {

    private class ZMQOutputStream extends OutputStream {
        @Override
        public void write(byte[] content) {
            if (!publisher.send(content)) {
                addWarn("Log event lost");
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
            if (! publisher.send(new byte[]{(byte)b})) {
                addError("Log event lost");
            }
        }
    }

    private final SocketConfigurator.Builder scb = SocketConfigurator.builder();
    private SocketType type = ZMQConfiguration.DEFAULT_TYPE;
    private Method method = ZMQConfiguration.DEFAULT_METHOD;
    @Getter @Setter
    private String endpoint = null;
    @Getter @Setter
    public Path privateKeyFile = null;
    @Getter @Setter
    private boolean autoCreate = false;
    @Getter @Setter
    private int ioThreads = 1;

    private Publisher publisher;

    /**
     * @return the ØMQ socket type.
     */
    public String getType() {
        return type.toString();
    }

    public String getMethod() {
        return method.toString();
    }

    public void setMethod(String method) {
        this.method = Method.valueOf(method.toUpperCase(Locale.ENGLISH));
    }

    public void setType(String type) {
        this.type = SocketType.valueOf(type.toUpperCase(Locale.ENGLISH));
    }

    public void setHwm(int hwm) {
        scb.recvHwm(hwm);
        scb.sendHwm(hwm);
    }

    public void setRcvHwm(int rcvHwm) {
        scb.recvHwm(rcvHwm);
    }

    public void setSndHwm(int sndHwm) {
        scb.sendHwm(sndHwm);
    }

    public void setMaxMsgSize(long maxMsgSize) {
        scb.maxMsgSize(maxMsgSize);
    }

    public void setLinger(int linger) {
        scb.linger(linger);
    }

    public void setBacklog(int backlog) {
        scb.backlog(backlog);
    }

    public void setIpv6(boolean ipv6) {
        scb.ipv6(ipv6);
    }

    public void addZmqOption(ZMQOption entry) {
        if (entry.getName() == null || entry.getName().isEmpty()) {
            addWarn("A <property> element is missing its 'name' attribute, skipping.");
            return;
        }
        scb.setOption(entry.getName(), entry.getValue());
    }

    @Override
    public void start() {
        if (endpoint == null) {
            addError("Unconfigured endpoint, the ZMQ appender can't log");
            return;
        }
        ZMQConfiguration<ZMQAppender<E>> config = ZMQConfiguration.<ZMQAppender<E>>builder().context(this)
                                                         .endpoint(endpoint)
                                                         .type(type)
                                                         .method(method)
                                                         .privateKeyFile(privateKeyFile)
                                                         .autoCreate(autoCreate)
                                                         .configurator(scb)
                                                         .build();

        publisher = Publisher.asynchronous("Log4JZMQPublishingThread", this, config);
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
