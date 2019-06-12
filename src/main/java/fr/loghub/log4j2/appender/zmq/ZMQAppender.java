package fr.loghub.log4j2.appender.zmq;

import java.util.Locale;

import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Node;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderFactory;
import org.apache.logging.log4j.core.config.plugins.validation.constraints.Required;
import org.zeromq.SocketType;

@Plugin(name = "ZMQ", category = Node.CATEGORY, elementType = Appender.ELEMENT_TYPE, printObject = true)
public class ZMQAppender extends AbstractAppender {

    public static class ZMQBuilder extends AbstractAppender.Builder<ZMQBuilder>
    implements org.apache.logging.log4j.core.util.Builder<ZMQAppender> {

        @PluginBuilderAttribute("endpoint")
        @Required(message = "No URL provided for ZMQ endpoint")
        String endpoint;

        @PluginBuilderAttribute("type")
        String type = SocketType.PUB.name();

        @PluginBuilderAttribute("method")
        String method = Method.CONNECT.name();

        @PluginBuilderAttribute("hwm")
        int hwm = 1000;

        @PluginBuilderAttribute("maxMsgSize")
        long maxMsgSize = -1;

        @PluginBuilderAttribute("linger")
        int linger = -1;

        @Override
        public ZMQAppender build() {
            return new ZMQAppender(this);
        }

        public ZMQConfiguration configuration() {
            return new ZMQConfiguration(getConfiguration().getLoggerContext(),
                                        endpoint,
                                        SocketType.valueOf(type.toUpperCase(Locale.US)),
                                        Method.valueOf(method.toUpperCase(Locale.US)),
                                        hwm,
                                        maxMsgSize,
                                        linger); 
        }

    }

    @PluginBuilderFactory
    public static ZMQBuilder newBuilder() {
        return new ZMQBuilder();
    }

    private final ZMQManager manager;

    protected ZMQAppender(ZMQBuilder builder) {
        super(builder.getName(), builder.getFilter(), builder.getLayout(), builder.isIgnoreExceptions(), builder.getPropertyArray());
        manager = ZMQManager.getManager(builder.getName(), ZMQManager.FACTORY, builder.configuration());
    }

    @Override
    public void append(LogEvent event) {
        byte[] formattedMessage = getLayout().toByteArray(event);
        if (! manager.getLogQueue().offer(formattedMessage)) {
            LOGGER.error("Appender {} could not send message to ZMQ, send queue full", getName());
        }
    }

}
