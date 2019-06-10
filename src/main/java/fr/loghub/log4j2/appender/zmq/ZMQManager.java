package fr.loghub.log4j2.appender.zmq;

import java.util.concurrent.BlockingQueue;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.appender.AbstractManager;
import org.apache.logging.log4j.core.appender.ManagerFactory;
import org.apache.logging.log4j.message.Message;

public class ZMQManager extends AbstractManager {

    public static final ManagerFactory<ZMQManager, ZMQConfiguration> FACTORY = ZMQManager::new;

    private final Publisher publisher;

    private ZMQManager(String name, ZMQConfiguration configuration) {
        super(configuration.getCtxt(), name);
        publisher = new Publisher(this, configuration);
    }

    public BlockingQueue<byte[]> getLogQueue() {
        return publisher.getLogQueue();
    }

    @Override
    public void close() {
        publisher.close();
        super.close();
    }
    
    public void log(Level level, Message msg, Throwable t) {
        AbstractManager.LOGGER.log(level, msg, t);
        AbstractManager.LOGGER.catching(Level.DEBUG, t);
    }

}
