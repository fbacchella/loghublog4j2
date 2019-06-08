package fr.loghub.log4j2.appender.zmq;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Node;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderFactory;
import org.apache.logging.log4j.core.config.plugins.validation.constraints.Required;

import zmq.socket.Sockets;

@Plugin(name = "ZMQ", category = Node.CATEGORY, elementType = Appender.ELEMENT_TYPE, printObject = true)
//@Accessors(chain=true)
public class ZMQAppender extends AbstractAppender {

    public static class ZMQBuilder extends AbstractAppender.Builder<ZMQBuilder>
    implements org.apache.logging.log4j.core.util.Builder<ZMQAppender> {

        @PluginBuilderAttribute("endpoint")
        @Required(message = "No URL provided for ZMQ endpoint")
        private String endpoint;

        @PluginBuilderAttribute("type")
        private String type = Sockets.PUB.name();

        @PluginBuilderAttribute("method")
        private String method = Method.CONNECT.name();

        @PluginBuilderAttribute("hwm")
        private int hwm = 1000;

        @Override
        public ZMQAppender build() {
            System.out.println("build " + endpoint);
            return new ZMQAppender(this);
        }

    }

    @PluginBuilderFactory
    public static ZMQBuilder newBuilder() {
        return new ZMQBuilder();
    }

    private final Publisher publisher;

    protected ZMQAppender(ZMQBuilder builder) {
        super(builder.getName(), builder.getFilter(), builder.getLayout(), builder.isIgnoreExceptions(), builder.getPropertyArray());
        publisher = Publisher.getBuilder()
                        .setEndpoint(builder.endpoint)
                        .setMethod(Method.valueOf(builder.method.toUpperCase(Locale.ENGLISH)))
                        .setType(Sockets.valueOf(builder.type.toUpperCase(Locale.ENGLISH)))
                        .setHwm(builder.hwm)
                        .build();
    }

    @Override
    public boolean stop(long timeout, TimeUnit timeUnit) {
        setStopping();
        publisher.close();
        setStopped();
        return true;
    }

    @Override
    public void append(LogEvent event) {
        byte[] formattedMessage = getLayout().toByteArray(event);
        if (! publisher.getLogQueue().offer(getLayout().toByteArray(event))) {
            LOGGER.error("Appender {} could not send message {} to JeroMQ", getName(), formattedMessage);
        }
    }

}
