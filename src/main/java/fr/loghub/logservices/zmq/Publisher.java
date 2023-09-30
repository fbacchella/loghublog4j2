package fr.loghub.logservices.zmq;

import org.zeromq.SocketType;

public interface Publisher {

    int DEFAULT_BACKLOG = Default.DEFAULT_BACKLOG;
    int DEFAULT_LINGER = Default.DEFAULT_LINGER;
    long DEFAULT_MAX_MSGSIZE = Default.DEFAULT_MAX_MSGSIZE;
    int DEFAULT_RCV_HWM = Default.DEFAULT_RCV_HWM;
    int DEFAULT_SND_HWM = Default.DEFAULT_SND_HWM;
    SocketType DEFAULT_TYPE = Default.DEFAULT_TYPE;
    Method DEFAULT_METHOD = Default.DEFAULT_METHOD;

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
