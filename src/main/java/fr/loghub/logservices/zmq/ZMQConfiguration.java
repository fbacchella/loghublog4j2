package fr.loghub.logservices.zmq;

import fr.loghub.logservices.zmq.Method;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;

import org.zeromq.SocketType;

import lombok.AccessLevel;

@AllArgsConstructor(access = AccessLevel.PUBLIC)
public class ZMQConfiguration<C> {
    @Getter @NonNull
    C context;
    @Getter @NonNull
    public final String endpoint;
    @Getter
    public final SocketType type;
    @Getter
    public final Method method;
    @Getter
    public final int hwm;
    @Getter
    public final long maxMsgSize;
    @Getter
    public final int linger;
}
