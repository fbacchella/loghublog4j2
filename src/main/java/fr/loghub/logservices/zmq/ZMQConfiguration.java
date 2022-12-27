package fr.loghub.logservices.zmq;

import org.zeromq.SocketType;

import lombok.Data;
import lombok.Getter;
import lombok.NonNull;

@Data
public class ZMQConfiguration<C> {

    @Getter @NonNull
    final C context;
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
    @Getter
    public final String peerPublicKey;
    @Getter
    public final String privateKeyFile;
    @Getter
    public final String publicKey;

}
