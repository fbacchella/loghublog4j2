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
        int rcvHwm = ZMQConfiguration.DEFAULT_RCV_HWM;

        @PluginBuilderAttribute(value = "sndHwm")
        int sndHwm = ZMQConfiguration.DEFAULT_SND_HWM;

        @PluginBuilderAttribute("maxMsgSize")
        long maxMsgSize = ZMQConfiguration.DEFAULT_MAX_MSGSIZE;

        @PluginBuilderAttribute("linger")
        int linger = ZMQConfiguration.DEFAULT_LINGER;

        @PluginBuilderAttribute("peerPublicKey")
        public String peerPublicKey;

        @PluginBuilderAttribute("privateKeyFile")
        public String privateKeyFile;

        @PluginBuilderAttribute("publicKey")
        public String publicKey;

        @PluginBuilderAttribute("autoCreate")
        public boolean autoCreate;

        @PluginBuilderAttribute("backlog")
        public int backlog = ZMQConfiguration.DEFAULT_BACKLOG;

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
