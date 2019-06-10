package fr.loghub.log4j2.appender.zmq;

import java.util.concurrent.BlockingQueue;

import org.apache.logging.log4j.core.appender.AbstractManager;
import org.apache.logging.log4j.core.appender.ManagerFactory;

public class ZMQManager extends AbstractManager {

    public static final ManagerFactory<ZMQManager, ZMQConfiguration> FACTORY = ZMQManager::new;

    private final Publisher publisher;

    private ZMQManager(String name, ZMQConfiguration configuration) {
        super(configuration.getCtxt(), name);
        publisher = new Publisher(configuration);
    }

    public BlockingQueue<byte[]> getLogQueue() {
        return publisher.getLogQueue();
    }

    @Override
    public void close() {
        publisher.close();
        super.close();
    }

}
