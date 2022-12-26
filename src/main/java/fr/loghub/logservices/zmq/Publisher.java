package fr.loghub.logservices.zmq;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.zeromq.SocketType;
import org.zeromq.ZConfig;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;

import com.neilalexander.jnacl.crypto.curve25519;
import com.neilalexander.jnacl.crypto.curve25519xsalsa20poly1305;

import fr.loghub.naclprovider.NaclPrivateKey;
import fr.loghub.naclprovider.NaclPrivateKeySpec;
import fr.loghub.naclprovider.NaclProvider;
import fr.loghub.naclprovider.NaclPublicKeySpec;
import lombok.Getter;

public class Publisher extends Thread {

    static class NaClServices {
        private static final Provider provider;
        private static final KeyPairGenerator generator;
        private static final KeyFactory kf;
        static {
            try {
                provider = new NaclProvider();
                generator = KeyPairGenerator.getInstance(NaclProvider.NAME, provider);
                kf = KeyFactory.getInstance(NaclProvider.NAME, provider);
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        }
        static byte[] readPrivateKey(String path) {
            try {
                byte[] key = Files.readAllBytes(Paths.get(path));
                PKCS8EncodedKeySpec encoded = new PKCS8EncodedKeySpec(key);
                PrivateKey pv = new NaclPrivateKey(encoded);
                NaclPrivateKeySpec naclspec = kf.getKeySpec(pv, NaclPrivateKeySpec.class);
                return naclspec.getBytes();
            } catch (IOException | InvalidKeyException | InvalidKeySpecException ex) {
                throw new IllegalArgumentException(ex);
            }
        }
        static byte[] writePair(String privatePath) {
            try {
                generator.initialize(256);
                KeyPair kp = generator.generateKeyPair();
                PrivateKey pv = kp.getPrivate();
                Files.write(Paths.get(privatePath), pv.getEncoded());

                PublicKey pb = kp.getPublic();
                NaclPublicKeySpec naclpubspec = kf.getKeySpec(pb, NaclPublicKeySpec.class);

                ZConfig zconf = new ZConfig("root", null);
                zconf.putValue("curve/public-key", ZMQ.Curve.z85Encode(naclpubspec.getBytes()));
                zconf.save(privatePath.replace(".p8", ".zpl"));
                return naclpubspec.getBytes();
            } catch (IOException | InvalidKeySpecException ex) {
                throw new IllegalArgumentException(ex);
            }
        }
    }

    private ZMQ.Socket socket;
    private final ZContext ctx;
    private final ZMQConfiguration config;
    @Getter
    private final BlockingQueue<byte[]> logQueue;
    private volatile boolean closed;
    private final Logger logger;

    public Publisher(String name, Logger logger, ZMQConfiguration config) {
        ctx = new ZContext(1);
        ctx.setLinger(0);
        this.config = config;
        this.logger = logger;
        logQueue = new ArrayBlockingQueue<>(config.getHwm());
        setName(name);
        setDaemon(true);
        setUncaughtExceptionHandler(this::exceptionHandler);
        closed = false;
        start();
    }

    @Override
    public void run() {
        try {
            while (! closed) {
                // First check if socket is null.
                // It might be the first iteration, or the previous socket badly failed and was dropped
                synchronized (this) {
                    if (socket == null) {
                        socket = newSocket(config.getMethod(), config.getType(), config.getEndpoint(), config.getHwm(), -1);
                        if (socket == null) {
                            // No socket returned, appender was closed
                            break;
                        }
                        if (config.peerPublicKey != null && ! config.peerPublicKey.isEmpty()) {
                            socket.setCurveServerKey(ZMQ.Curve.z85Decode(config.peerPublicKey));
                        }
                        byte[] publicKey = (config.publicKey != null && ! config.publicKey.isEmpty()) ? ZMQ.Curve.z85Decode(config.publicKey) : null;
                        if (config.privateKeyFile != null && ! config.privateKeyFile.isEmpty()) {
                            if (! Files.exists(Paths.get(config.privateKeyFile))) {
                                publicKey = NaClServices.writePair(config.privateKeyFile);
                            }
                            byte[] secretKey = NaClServices.readPrivateKey(config.privateKeyFile);
                            socket.setCurveSecretKey(secretKey);
                            if (publicKey == null) {
                                publicKey = new byte[curve25519xsalsa20poly1305.crypto_secretbox_PUBLICKEYBYTES];
                                curve25519.crypto_scalarmult_base(publicKey, secretKey);
                            }
                        }
                        if (publicKey != null) {
                            socket.setCurvePublicKey(publicKey);
                        }
                        Optional.of(config.getMaxMsgSize()).filter(i -> i > 0).ifPresent(socket::setMaxMsgSize);
                        Optional.of(config.getLinger()).filter(i -> i > 0).ifPresent(socket::setLinger);
                    }
                }
                // Not a blocking wait, it allows to test if closed every 100 ms
                // Needed because interrupt deactivated for this thread
                byte[] log = logQueue.poll(100, TimeUnit.MILLISECONDS);
                if (log != null) {
                    try {
                        synchronized (this) {
                            if (!closed) {
                                socket.send(log, zmq.ZMQ.ZMQ_DONTWAIT);
                            }
                        }
                    } catch (zmq.ZError.IOException | java.nio.channels.ClosedSelectorException | org.zeromq.ZMQException e) {
                        synchronized (this) {
                            // If it's not closed, drop the socket, to recreate a new one
                            if (!closed) {
                                socket.close();
                                socket = null;
                            }
                        }
                        logger.warn(() -> String.format("Failed ZMQ connection %s: %s", config.getEndpoint(), e.getMessage()), e);
                    }
                }
            }
        } catch (InterruptedException e) {
            // Interrupt deactivated, so never happens
        }
        close();
    }

    /* Don't interrupt a ZMQ thread, just finished it
     * @see java.lang.Thread#interrupt()
     */
    @Override
    public synchronized void interrupt() {
        closed = true;
    }

    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        socket.close();
        socket = null;
        ctx.destroy();
    }

    private synchronized Socket newSocket(Method method, SocketType type, String endpoint, int hwm, int timeout) {
        if (closed || ctx.isClosed()) {
            return null;
        } else {
            Socket newsocket = ctx.createSocket(type);
            newsocket.setRcvHWM(hwm);
            newsocket.setSndHWM(hwm);
            newsocket.setSendTimeOut(timeout);
            newsocket.setReceiveTimeOut(timeout);

            method.act(newsocket, endpoint);
            String url = endpoint + ":" + type.toString() + ":" + method.getSymbol();
            newsocket.setIdentity(url.getBytes());
            return newsocket;
        }
    }

    private void exceptionHandler(Thread t, Throwable ex) {
        logger.error(() -> "Critical exception: " + ex.getMessage(), ex);
    }
}
