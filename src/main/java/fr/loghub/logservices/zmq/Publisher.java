package fr.loghub.logservices.zmq;

import org.zeromq.SocketType;

public interface Publisher {

    String PROPERTY_PRIVATEKEYFILE = "fr.loghub.logging.zmq.curve.privateKeyFile";
    String PROPERTY_AUTOCREATE = "fr.loghub.logging.zmq.curve.autoCreate";

    void close();

    boolean send(byte[] content);

    static Publisher synchronous(Logger logger, ZMQConfiguration<?> config) {
        return new SynchronousPublisher(logger, config);
    }

    static Publisher asynchronous(String name, Logger logger, ZMQConfiguration<?> config) {
        return new AsynchronousPublisher(name, logger, config);
    }

}
