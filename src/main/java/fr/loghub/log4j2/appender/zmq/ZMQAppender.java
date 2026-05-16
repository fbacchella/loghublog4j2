package fr.loghub.log4j2.appender.zmq;

import java.nio.file.Path;

import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.appender.AbstractManager;
import org.apache.logging.log4j.core.config.Node;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderFactory;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.validation.constraints.Required;
import org.zeromq.Method;
import org.zeromq.SocketConfigurator;
import org.zeromq.SocketType;

import fr.loghub.logservices.zmq.ZMQConfiguration;
import zmq.ZMQ;
import zmq.io.mechanism.curve.CurveMechanismSettings;

@Plugin(name = "ZMQ", category = Node.CATEGORY, elementType = Appender.ELEMENT_TYPE, printObject = true)
public class ZMQAppender extends AbstractAppender {

    public static class ZMQBuilder extends AbstractAppender.Builder<ZMQBuilder>
    implements org.apache.logging.log4j.core.util.Builder<ZMQAppender> {

        private final SocketConfigurator.Builder scb = SocketConfigurator.builder();

        @PluginBuilderAttribute("endpoint")
        @Required(message = "No URL provided for ZMQ endpoint")
        public String endpoint;

        @PluginBuilderAttribute("type")
        SocketType type = ZMQConfiguration.DEFAULT_TYPE;

        @PluginBuilderAttribute("method")
        Method method = ZMQConfiguration.DEFAULT_METHOD;

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
        public Path privateKeyFile = null;

        @PluginBuilderAttribute("publicKey")
        public String publicKey = null;

        @PluginBuilderAttribute("autoCreate")
        public boolean autoCreate = false;

        @PluginElement("ZMQSocketProperty")
        private ZMQSocketProperty[] entries;

        public ZMQBuilder setEndpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        public ZMQBuilder setType(SocketType type) {
            this.type = type;
            return this;
        }

        public ZMQBuilder setMethod(Method method) {
            this.method = method;
            return this;
        }

        public ZMQBuilder setPeerPublicKey(String peerPublicKey) {
            this.peerPublicKey = peerPublicKey;
            scb.curvePeerPublicKey(CurveMechanismSettings.curveKey(peerPublicKey));
            return this;
        }

        public ZMQBuilder setPrivateKeyFile(Path privateKeyFile) {
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

        public ZMQBuilder setHwm(int hwm) {
            this.hwm = hwm;
            scb.recvHwm(hwm);
            scb.sendHwm(hwm);
            return this;
        }

        public ZMQBuilder setRcvHwm(int rcvHwm) {
            this.rcvHwm = rcvHwm;
            scb.recvHwm(rcvHwm);
            return this;
        }

        public ZMQBuilder setSndHwm(int sndHwm) {
            this.sndHwm = sndHwm;
            scb.sendHwm(sndHwm);
            return this;
        }

        public ZMQBuilder setMaxMsgSize(long maxMsgSize) {
            this.maxMsgSize = maxMsgSize;
            scb.maxMsgSize(maxMsgSize);
            return this;
        }

        public ZMQBuilder setLinger(int linger) {
            this.linger = linger;
            scb.linger(linger);
            return this;
        }

        @Override
        public ZMQAppender build() {
            return new ZMQAppender(this);
        }
    }

    @PluginBuilderFactory
    public static ZMQBuilder newBuilder() {
        return new ZMQBuilder();
    }

    private final ZMQManager manager;

    protected ZMQAppender(ZMQBuilder builder) {
        super(builder.getName(), builder.getFilter(), builder.getLayout(), builder.isIgnoreExceptions(), builder.getPropertyArray());
        for (ZMQSocketProperty zsp: builder.entries != null ? builder.entries : new ZMQSocketProperty[0]) {
            builder.scb.setOption(zsp.getName(), zsp.getValue());
        }
        builder.scb.endpoint(builder.endpoint);
        builder.scb.type(builder.type);
        builder.scb.method(builder.method);
        ZMQConfiguration.Builder<LoggerContext> confBuilder = ZMQConfiguration.<LoggerContext>builder();
        ZMQConfiguration<LoggerContext> zconf = confBuilder.context(builder.getConfiguration().getLoggerContext())
                       .configurator(builder.scb)
                       .endpoint(builder.endpoint)
                       .type(builder.type)
                       .method(builder.method)
                       .privateKeyFile(builder.privateKeyFile)
                       .autoCreate(builder.autoCreate)
                       .build();
        manager = AbstractManager.getManager(builder.getName(), ZMQManager.FACTORY, zconf);
    }

    @Override
    public void append(LogEvent event) {
        byte[] formattedMessage = getLayout().toByteArray(event);
        if (! manager.send(formattedMessage)) {
            LOGGER.error("Appender {} could not send message to ZMQ, send queue full", getName());
        }
    }

}
