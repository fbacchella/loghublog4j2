package fr.loghub.logservices.zmq;

import org.zeromq.SocketType;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NonNull;

@Getter
@Data
@Builder
public class ZMQConfiguration<C> {

    @NonNull
    public final C context;
    @NonNull
    public final String endpoint;
    public final SocketType type;
    public final Method method;
    public final int sendHwm;
    public final int recvHwm;
    public final long maxMsgSize;
    public final int linger;
    public final String peerPublicKey;
    public final String privateKeyFile;
    public final String publicKey;
    public final boolean autoCreate;
    public final long backlog;

}
