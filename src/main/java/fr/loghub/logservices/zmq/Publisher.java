package fr.loghub.logservices.zmq;

public interface Publisher {

    String PROPERTY_PRIVATEKEYFILE = "fr.loghub.logging.zmq.curve.privateKeyFile";
    String PROPERTY_AUTOCREATE = "fr.loghub.logging.zmq.curve.autoCreate";

    public void close();

    public boolean send(byte[] content);

    public static Publisher synchronous(Logger logger, ZMQConfiguration<?> config) {
        return new SynchronousPublisher(logger, config);
    }

    public static Publisher asynchronous(String name, Logger logger, ZMQConfiguration<?> config) {
        return new AsynchronousPublisher(name, logger, config);
    }

}
