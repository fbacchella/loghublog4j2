package fr.loghub.logservices.zmq;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Optional;

import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import com.neilalexander.jnacl.crypto.curve25519;
import com.neilalexander.jnacl.crypto.curve25519xsalsa20poly1305;

import lombok.Getter;

class SynchronousPublisher implements Publisher {

    private ZMQ.Socket socket;
    private final ZContext ctx;
    private final ZMQConfiguration<?> config;
    @Getter
    private volatile boolean closed;
    private final Logger logger;
    private final Runnable curveConfigurator;

    SynchronousPublisher(Logger logger, ZMQConfiguration<?> config) {
        ctx = new ZContext(1);
        ctx.setLinger(0);
        this.config = config;
        this.curveConfigurator = getCurveConfigurator();
        this.logger = logger;
    }

    private Runnable getCurveConfigurator() {
        boolean autoCreate = Optional.ofNullable(System.getProperty(PROPERTY_AUTOCREATE))
                                     .map(Boolean::valueOf)
                                     .orElse(config.autoCreate);
        String privateKeyFile = Optional.ofNullable(System.getProperty(PROPERTY_PRIVATEKEYFILE))
                                        .orElse(config.privateKeyFile);
        if (privateKeyFile != null && ! privateKeyFile.isEmpty()) {
            NaClServices nacl = new NaClServices();
            byte[] publicKey;
            Path privateKeyPath = Paths.get(privateKeyFile);
            if (! Files.exists(privateKeyPath) && autoCreate) {
                publicKey = nacl.writePair(privateKeyFile);
            } else if (config.publicKey != null && ! config.publicKey.isEmpty()) {
                publicKey = Base64.getDecoder().decode(config.publicKey);
            } else {
                publicKey = null;
            }
            if (! Files.exists(privateKeyPath)) {
                throw new IllegalStateException(String.format("ZMQ private key %s file missing", privateKeyFile));
            }
            byte[] secretKey = nacl.readPrivateKey(privateKeyFile);
            if (publicKey == null) {
                publicKey = new byte[curve25519xsalsa20poly1305.crypto_secretbox_PUBLICKEYBYTES];
                curve25519.crypto_scalarmult_base(publicKey, secretKey);
            }
            byte[] publicKeyFinal = publicKey;
            byte[] peerKey = config.peerPublicKey != null && ! config.peerPublicKey.isEmpty() ? Base64.getDecoder().decode(config.peerPublicKey) : null;
            return () -> {
                socket.setCurveSecretKey(secretKey);
                socket.setCurvePublicKey(publicKeyFinal);
                if (peerKey != null) {
                    socket.setCurveServerKey(peerKey);
                }
            };
        } else {
            return () -> {};
        }
    }

    synchronized void refreshSocket() throws InterruptedException {
        if (closed || ctx.isClosed()) {
            throw new InterruptedException();
        } else if (socket == null) {
            socket = ctx.createSocket(config.type);
            socket.setRcvHWM(config.hwm);
            socket.setSndHWM(config.hwm);
            String url = config.endpoint + ":" + config.type.toString() + ":" + config.method.getSymbol();
            socket.setIdentity(url.getBytes());
            curveConfigurator.run();
            Optional.of(config.getMaxMsgSize()).filter(i -> i > 0).ifPresent(socket::setMaxMsgSize);
            Optional.of(config.getLinger()).filter(i -> i > 0).ifPresent(socket::setLinger);
            config.method.act(socket, config.endpoint);
        }
    }

    @Override
    public synchronized boolean send(byte[] log) {
        if (log != null && !closed) {
            try {
                socket.send(log, zmq.ZMQ.ZMQ_DONTWAIT);
            } catch (zmq.ZError.IOException | java.nio.channels.ClosedSelectorException | org.zeromq.ZMQException e) {
                // If it's not closed, drop the socket, to recreate a new one
                if (!closed) {
                    socket.close();
                    socket = null;
                }
                logger.warn(() -> String.format("Failed ZMQ connection %s: %s", config.getEndpoint(), e.getMessage()), e);
            }
        }
        return true;
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        socket.close();
        socket = null;
        ctx.destroy();
    }

}
