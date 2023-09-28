package fr.loghub.log4j2.appender.zmq;

import java.util.function.Supplier;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractManager;
import org.apache.logging.log4j.core.appender.ManagerFactory;
import org.apache.logging.log4j.core.async.AsyncLoggerContext;

import fr.loghub.logservices.zmq.Logger;
import fr.loghub.logservices.zmq.Publisher;
import fr.loghub.logservices.zmq.ZMQConfiguration;

public class ZMQManager extends AbstractManager implements Logger {

    public static final ManagerFactory<ZMQManager, ZMQConfiguration<LoggerContext>> FACTORY = ZMQManager::new;

    private final Publisher publisher;

    private ZMQManager(String name, ZMQConfiguration<LoggerContext> configuration) {
        super(configuration.getContext(), name);
        if (configuration.getContext() instanceof AsyncLoggerContext) {
            publisher = Publisher.synchronous(this, configuration);
        } else {
            publisher = Publisher.asynchronous("Log4JZMQPublishingThread",this, configuration);
        }
    }

    public boolean send(byte[] formattedMessage) {
        return publisher.send(formattedMessage);
    }

    @Override
    public void close() {
        publisher.close();
        super.close();
    }

    @Override
    public void warn(Supplier<String> message, Throwable t) {
        AbstractManager.LOGGER.log(Level.WARN, message.get());
        AbstractManager.LOGGER.catching(Level.DEBUG, t);
    }

    @Override
    public void error(Supplier<String> message, Throwable t) {
        AbstractManager.LOGGER.log(Level.ERROR, message.get());
        AbstractManager.LOGGER.catching(Level.DEBUG, t);
    }

}
