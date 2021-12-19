package fr.loghub.logservices.zmq;

import java.util.function.Supplier;

public interface Logger {

    void warn(Supplier<String> message, Throwable t);

    void error(Supplier<String> message, Throwable ex);

}
