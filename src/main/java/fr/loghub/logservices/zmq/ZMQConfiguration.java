package fr.loghub.logservices.zmq;

import org.zeromq.SocketType;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NonNull;
import zmq.Options;

@Getter
@Data
@Builder
public class ZMQConfiguration<C> {

    public static final int DEFAULT_BACKLOG;
    public static final int DEFAULT_RCV_HWM;
    public static final int DEFAULT_SND_HWM;
    public static final int DEFAULT_LINGER;
    public static final long DEFAULT_MAX_MSGSIZE;
    public static final SocketType DEFAULT_TYPE = SocketType.PUB;
    public static final Method DEFAULT_METHOD = Method.CONNECT;

    static {
        Options options = new Options();
        DEFAULT_BACKLOG = options.backlog;
        DEFAULT_LINGER = options.linger;
        DEFAULT_MAX_MSGSIZE = options.maxMsgSize;
        DEFAULT_RCV_HWM = options.recvHwm;
        DEFAULT_SND_HWM = options.sendHwm;
    }

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
    public final int backlog;

}
