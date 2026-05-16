package fr.loghub.log4j1.zmq;

import java.nio.file.Path;
import java.util.Locale;
import java.util.function.Supplier;

import org.apache.log4j.spi.ErrorCode;
import org.zeromq.SocketConfigurator;
import org.zeromq.SocketType;
import org.zeromq.Method;

import fr.loghub.log4j1.serializer.SerializerAppender;
import fr.loghub.logservices.zmq.Logger;
import fr.loghub.logservices.zmq.Publisher;
import fr.loghub.logservices.zmq.ZMQConfiguration;
import lombok.Getter;
import lombok.Setter;
import zmq.io.mechanism.curve.CurveMechanismSettings;

public class ZMQAppender extends SerializerAppender implements Logger {

    private final SocketConfigurator.Builder scb = SocketConfigurator.builder();
    private SocketType type = ZMQConfiguration.DEFAULT_TYPE;
    private Method method = ZMQConfiguration.DEFAULT_METHOD;
    @Getter @Setter
    private String endpoint = null;
    @Getter @Setter
    private Path privateKeyFile = null;
    @Getter @Setter
    private boolean autoCreate = false;
    @Getter @Setter
    private int ioThreads = 1;

    private Publisher publisher;

    public void setHwm(int hwm) {
        scb.recvHwm(hwm);
        scb.sendHwm(hwm);
    }

    public void setRcvHwm(int rcvHwm) {
        scb.recvHwm(rcvHwm);
    }

    public void setSndHwm(int sndHwm) {
        scb.sendHwm(sndHwm);
    }

    public void setMaxMsgSize(long maxMsgSize) {
        scb.maxMsgSize(maxMsgSize);
    }

    public void setLinger(int linger) {
        scb.linger(linger);
    }

    public void setBacklog(int backlog) {
        scb.backlog(backlog);
    }

    public void setIpv6(boolean ipv6) {
        scb.ipv6(ipv6);
    }

    public void setAffinity(long affinity) {
        scb.affinity(affinity);
    }

    public void setReceiveBufferSize(int receiveBufferSize) {
        scb.receiveBufferSize(receiveBufferSize);
    }

    public void setSendBufferSize(int sendBufferSize) {
        scb.sendBufferSize(sendBufferSize);
    }

    public void setReceiveTimeOut(int receiveTimeOut) {
        scb.receiveTimeOut(receiveTimeOut);
    }

    public void setReconnectIVL(int reconnectIVL) {
        scb.reconnectIVL(reconnectIVL);
    }

    public void setReconnectIVLMax(int reconnectIVLMax) {
        scb.reconnectIVLMax(reconnectIVLMax);
    }

    public void setSendTimeOut(int sendTimeOut) {
        scb.sendTimeOut(sendTimeOut);
    }

    public void setTcpKeepAlive(int tcpKeepAlive) {
        scb.tcpKeepAlive(tcpKeepAlive);
    }

    public void setTcpKeepAliveCount(int tcpKeepAliveCount) {
        scb.tcpKeepAliveCount(tcpKeepAliveCount);
    }

    public void setTcpKeepAliveIdle(int tcpKeepAliveIdle) {
        scb.tcpKeepAliveIdle(tcpKeepAliveIdle);
    }

    public void setTcpKeepAliveInterval(int tcpKeepAliveInterval) {
        scb.tcpKeepAliveInterval(tcpKeepAliveInterval);
    }

    public void setXpubVerbose(boolean xpubVerbose) {
        scb.xpubVerbose(xpubVerbose);
    }

    public void setTos(int tos) {
        scb.tos(tos);
    }

    public void setHeartbeatIvl(int heartbeatIvl) {
        scb.heartbeatIvl(heartbeatIvl);
    }

    public void setHeartbeatTimeout(int heartbeatTimeout) {
        scb.heartbeatTimeout(heartbeatTimeout);
    }

    public void setHeartbeatTtl(int heartbeatTtl) {
        scb.heartbeatTtl(heartbeatTtl);
    }

    public void setHandshakeIvl(int handshakeIvl) {
        scb.handshakeIvl(handshakeIvl);
    }

    public void setSocksProxyPort(int socksProxyPort) {
        scb.socksProxyPort(socksProxyPort);
    }

    public void setSocksProxyHost(String socksProxyHost) {
        scb.socksProxyHost(socksProxyHost);
    }

    public void setXpubNoDrop(boolean xpubNoDrop) {
        scb.xpubNoDrop(xpubNoDrop);
    }

    public void setXpubManual(boolean xpubManual) {
        scb.xpubManual(xpubManual);
    }

    public void setXpubVerboser(boolean xpubVerboser) {
        scb.xpubVerboser(xpubVerboser);
    }

    public void setPlainUsername(String plainUsername) {
        scb.plainUsername(plainUsername);
    }

    public void setPlainPassword(String plainPassword) {
        scb.plainPassword(plainPassword);
    }

    public void setCurvePublicKey(String curvePublicKey) {
        scb.curvePublicKey(CurveMechanismSettings.curveKey(curvePublicKey));
    }

    public void setCurveSecretKey(String curveSecretKey) {
        scb.curveSecretKey(CurveMechanismSettings.curveKey(curveSecretKey));
    }

    public void setCurvePeerPublicKey(String curvePeerPublicKey) {
        scb.curvePeerPublicKey(CurveMechanismSettings.curveKey(curvePeerPublicKey));
    }

    @Override
    protected void subOptions() {
        if (endpoint == null) {
            errorHandler.error("Unconfigured endpoint, the ZMQ appender can't log");
            return;
        }
        scb.endpoint(endpoint);
        scb.type(type);
        scb.method(method);
        ZMQConfiguration<ZMQAppender> config = ZMQConfiguration.<ZMQAppender>builder().context(this)
                       .endpoint(endpoint)
                       .type(type)
                       .method(method)
                       .ioThreads(ioThreads)
                       .privateKeyFile(privateKeyFile)
                       .autoCreate(autoCreate)
                       .configurator(scb)
                       .build();
        publisher = Publisher.asynchronous("Log4J1ZMQPublishingThread", this, config);
    }

    @Override
    protected void send(byte[] content) {
        if (!publisher.send(content)) {
            errorHandler.error("Log event lost");
        }
    }

    @Override
    public void close() {
        publisher.close();
    }

    /**
     * Define the ØMQ socket type. Current allowed value are PUB or PUSH.
     *
     * @param type
     */
    public void setType(String type) {
        try {
            this.type = SocketType.valueOf(type.toUpperCase(Locale.ENGLISH));
        } catch (Exception e) {
            String msg = "[" + type + "] should be one of [PUSH, PUB]" + ", using default ZeroMQ socket type, PUSH by default.";
            errorHandler.error(msg, e, ErrorCode.GENERIC_FAILURE);
        }
    }

    /**
     * @return the ØMQ socket type.
     */
    public String getType() {
        return type.toString();
    }

    /**
     * The <b>method</b> define the connection method for the ØMQ socket. It can take the value
     * connect or bind, it's case-insensitive.
     * @param method
     */
    public void setMethod(String method) {
        try {
            this.method = Method.valueOf(method.toUpperCase());
        } catch (Exception e) {
            String msg = "[" + type + "] should be one of [connect, bind]" + ", using default ZeroMQ socket type, connect by default.";
            errorHandler.error(msg, e, ErrorCode.GENERIC_FAILURE);
        }
    }

    @Override
    public void warn(Supplier<String> message, Throwable t) {
        errorHandler.error(message.get());
    }

    @Override
    public void error(Supplier<String> message, Throwable ex) {
        errorHandler.error(message.get());
    }

}
