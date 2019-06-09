package fr.loghub.log4j2.appender.zmq;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;

import org.apache.logging.log4j.core.LoggerContext;
import org.zeromq.SocketType;

import lombok.AccessLevel;

@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class ZMQConfiguration {
    @Getter
    private final LoggerContext ctxt;
    @Getter @NonNull
    private final String endpoint;
    @Getter
    private final SocketType type;
    @Getter
    private final Method method;
    @Getter
    private final int hwm;
    @Getter
    private final long maxMsgSize;
    @Getter
    private final int linger;
}
