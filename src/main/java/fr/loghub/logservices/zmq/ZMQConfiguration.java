package fr.loghub.logservices.zmq;

import org.zeromq.SocketType;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NonNull;
import zmq.ZMQ;

@Getter
@Data
@Builder
public class ZMQConfiguration<C> {

    public static final SocketType DEFAULT_TYPE = SocketType.PUB;
    public static final Method DEFAULT_METHOD = Method.CONNECT;

    @NonNull
    public final C context;
    @NonNull
    public final String endpoint;
    @Builder.Default public final SocketType type = DEFAULT_TYPE;
    @Builder.Default public final Method method = DEFAULT_METHOD;
    @Builder.Default public final int sendHwm = ZMQ.DEFAULT_SEND_HWM;
    @Builder.Default public final int recvHwm = ZMQ.DEFAULT_RECV_HWM;
    @Builder.Default public final long maxMsgSize = ZMQ.DEFAULT_MAX_MSG_SIZE;
    @Builder.Default public final int linger = ZMQ.DEFAULT_LINGER;
    @Builder.Default public final String peerPublicKey = null;
    @Builder.Default public final String privateKeyFile = null;
    @Builder.Default public final String publicKey = null;
    @Builder.Default public final boolean autoCreate = false;
    @Builder.Default public final int backlog = ZMQ.DEFAULT_BACKLOG;
    @Builder.Default public final long affinity = ZMQ.DEFAULT_AFFINITY;
    @Builder.Default public final byte[] identity = ZMQ.DEFAULT_IDENTITY;
    @Builder.Default public final boolean ipv6 = ZMQ.DEFAULT_IPV6;
    @Builder.Default public final long receiveBufferSize = ZMQ.DEFAULT_RCVBUF;
    @Builder.Default public final long sendBufferSize = ZMQ.DEFAULT_SNDBUF;
    @Builder.Default public final int receiveTimeOut = ZMQ.DEFAULT_RECV_TIMEOUT;
    @Builder.Default public final long reconnectIVL = ZMQ.DEFAULT_RECONNECT_IVL;
    @Builder.Default public final long reconnectIVLMax = ZMQ.DEFAULT_RECONNECT_IVL_MAX;
    @Builder.Default public final int sendTimeOut = ZMQ.DEFAULT_SEND_TIMEOUT;
    @Builder.Default public final int tcpKeepAlive = ZMQ.DEFAULT_TCP_KEEP_ALIVE;
    @Builder.Default public final long tcpKeepAliveCount = ZMQ.DEFAULT_TCP_KEEP_ALIVE_CNT;
    @Builder.Default public final long tcpKeepAliveIdle = ZMQ.DEFAULT_TCP_KEEP_ALIVE_IDLE;
    @Builder.Default public final long tcpKeepAliveInterval = ZMQ.DEFAULT_TCP_KEEP_ALIVE;
    @Builder.Default public final boolean xpubVerbose = false;
    @Builder.Default public final int tos = ZMQ.DEFAULT_TOS;

}
