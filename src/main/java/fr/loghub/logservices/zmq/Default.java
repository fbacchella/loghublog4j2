package fr.loghub.logservices.zmq;

import org.zeromq.SocketType;

import zmq.Options;

/**
 * An intermediate class to resolve default options values
 */
final class Default {

    static final int DEFAULT_BACKLOG;
    static final int DEFAULT_RCV_HWM;
    static final int DEFAULT_SND_HWM;
    static final int DEFAULT_LINGER;
    static final long DEFAULT_MAX_MSGSIZE;
    static final  SocketType DEFAULT_TYPE = SocketType.PUB;
    static final  Method DEFAULT_METHOD = Method.CONNECT;

    static {
        Options options = new Options();
        DEFAULT_BACKLOG = options.backlog;
        DEFAULT_LINGER = options.linger;
        DEFAULT_MAX_MSGSIZE = options.maxMsgSize;
        DEFAULT_RCV_HWM = options.recvHwm;
        DEFAULT_SND_HWM = options.sendHwm;
    }

    private Default() {

    }

}
