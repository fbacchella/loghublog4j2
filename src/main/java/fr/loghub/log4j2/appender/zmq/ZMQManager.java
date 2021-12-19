package fr.loghub.log4j2.appender.zmq;

import fr.loghub.logservices.zmq.Logger;
import fr.loghub.logservices.zmq.Publisher;
import fr.loghub.logservices.zmq.ZMQConfiguration;

import java.util.concurrent.BlockingQueue;
import java.util.function.Supplier;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractManager;
import org.apache.logging.log4j.core.appender.ManagerFactory;

public class ZMQManager extends AbstractManager implements Logger {

    public static final ManagerFactory<ZMQManager, ZMQConfiguration> FACTORY = ZMQManager::new;

    private final Publisher publisher;

    private ZMQManager(String name, ZMQConfiguration<LoggerContext> configuration) {
        super(configuration.getContext(), name);
        publisher = new Publisher("Log4JZMQPublishingThread",this, configuration);
    }

    public BlockingQueue<byte[]> getLogQueue() {
        return publisher.getLogQueue();
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
