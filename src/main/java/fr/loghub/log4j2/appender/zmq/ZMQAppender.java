package fr.loghub.log4j2.appender.zmq;

import java.util.Locale;

import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.appender.AbstractManager;
import org.apache.logging.log4j.core.config.Node;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderFactory;
import org.apache.logging.log4j.core.config.plugins.validation.constraints.Required;
import org.zeromq.SocketType;

import fr.loghub.logservices.zmq.Method;
import fr.loghub.logservices.zmq.ZMQConfiguration;
import zmq.ZMQ;

@Plugin(name = "ZMQ", category = Node.CATEGORY, elementType = Appender.ELEMENT_TYPE, printObject = true)
public class ZMQAppender extends AbstractAppender {

    public static class ZMQBuilder extends AbstractAppender.Builder<ZMQBuilder>
    implements org.apache.logging.log4j.core.util.Builder<ZMQAppender> {

        @PluginBuilderAttribute("endpoint")
        @Required(message = "No URL provided for ZMQ endpoint")
        String endpoint;

        @PluginBuilderAttribute("type")
        String type = ZMQConfiguration.DEFAULT_TYPE.name();

        @PluginBuilderAttribute("method")
        String method = ZMQConfiguration.DEFAULT_METHOD.name();

        @PluginBuilderAttribute("hwm")
        int hwm = -1;

        @PluginBuilderAttribute(value = "rcvHwm")
        int rcvHwm = ZMQ.DEFAULT_RECV_HWM;

        @PluginBuilderAttribute(value = "sndHwm")
        int sndHwm = ZMQ.DEFAULT_SEND_HWM;

        @PluginBuilderAttribute("maxMsgSize")
        long maxMsgSize = ZMQ.DEFAULT_MAX_MSG_SIZE;

        @PluginBuilderAttribute("linger")
        int linger = ZMQ.DEFAULT_LINGER;

        @PluginBuilderAttribute("peerPublicKey")
        public String peerPublicKey = null;

        @PluginBuilderAttribute("privateKeyFile")
        public String privateKeyFile = null;

        @PluginBuilderAttribute("publicKey")
        public String publicKey = null;

        @PluginBuilderAttribute("autoCreate")
        public boolean autoCreate = false;

        @PluginBuilderAttribute("backlog")
        public int backlog = ZMQ.DEFAULT_BACKLOG;

        @PluginBuilderAttribute("ipv6")
        boolean ipv6 = ZMQ.DEFAULT_IPV6;

        public ZMQBuilder setEndpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        public ZMQBuilder setType(String type) {
            this.type = type;
            return this;
        }

        public ZMQBuilder setMethod(String method) {
            this.method = method;
            return this;
        }

        public ZMQBuilder setHwm(int hwm) {
            this.hwm = hwm;
            return this;
        }

        public ZMQBuilder setRcvHwm(int rcvHwm) {
            this.rcvHwm = rcvHwm;
            return this;
        }

        public ZMQBuilder setSndHwm(int sndHwm) {
            this.sndHwm = sndHwm;
            return this;
        }

        public ZMQBuilder setMaxMsgSize(long maxMsgSize) {
            this.maxMsgSize = maxMsgSize;
            return this;
        }

        public ZMQBuilder setLinger(int linger) {
            this.linger = linger;
            return this;
        }

        public ZMQBuilder setPeerPublicKey(String peerPublicKey) {
            this.peerPublicKey = peerPublicKey;
            return this;
        }

        public ZMQBuilder setPrivateKeyFile(String privateKeyFile) {
            this.privateKeyFile = privateKeyFile;
            return this;
        }

        public ZMQBuilder setPublicKey(String publicKey) {
            this.publicKey = publicKey;
            return this;
        }

        public ZMQBuilder setAutoCreate(boolean autoCreate) {
            this.autoCreate = autoCreate;
            return this;
        }

        public ZMQBuilder setBacklog(int backlog) {
            this.backlog = backlog;
            return this;
        }

        public ZMQBuilder setIpv6(boolean ipv6) {
            this.ipv6 = ipv6;
            return this;
        }

        @Override
        public ZMQAppender build() {
            return new ZMQAppender(this);
        }

        public ZMQConfiguration<LoggerContext> configuration() {
            ZMQConfiguration.ZMQConfigurationBuilder<LoggerContext> builder = ZMQConfiguration.builder();
            return builder.context(getConfiguration().getLoggerContext())
                           .endpoint(endpoint)
                           .type(SocketType.valueOf(type.toUpperCase(Locale.US)))
                           .method(Method.valueOf(method.toUpperCase(Locale.US)))
                           .maxMsgSize(maxMsgSize)
                           .sendHwm(hwm != -1 ? hwm : sndHwm)
                           .recvHwm(hwm != -1 ? hwm : rcvHwm)
                           .linger(linger)
                           .peerPublicKey(peerPublicKey)
                           .privateKeyFile(privateKeyFile)
                           .publicKey(publicKey)
                           .autoCreate(autoCreate)
                           .backlog(backlog)
                           .ipv6(ipv6)
                           .build();
        }

    }

    @PluginBuilderFactory
    public static ZMQBuilder newBuilder() {
        return new ZMQBuilder();
    }

    private final ZMQManager manager;

    protected ZMQAppender(ZMQBuilder builder) {
        super(builder.getName(), builder.getFilter(), builder.getLayout(), builder.isIgnoreExceptions(), builder.getPropertyArray());
        manager = AbstractManager.getManager(builder.getName(), ZMQManager.FACTORY, builder.configuration());
    }

    @Override
    public void append(LogEvent event) {
        byte[] formattedMessage = getLayout().toByteArray(event);
        if (! manager.send(formattedMessage)) {
            LOGGER.error("Appender {} could not send message to ZMQ, send queue full", getName());
        }
    }

}
