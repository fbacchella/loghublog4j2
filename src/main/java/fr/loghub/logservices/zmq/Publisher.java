package fr.loghub.logservices.zmq;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.zeromq.ZConfig;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import com.neilalexander.jnacl.crypto.curve25519;
import com.neilalexander.jnacl.crypto.curve25519xsalsa20poly1305;

import fr.loghub.naclprovider.NaclPrivateKey;
import fr.loghub.naclprovider.NaclPrivateKeySpec;
import fr.loghub.naclprovider.NaclProvider;
import fr.loghub.naclprovider.NaclPublicKeySpec;
import lombok.Getter;

public class Publisher extends Thread {

    static class NaClServices {
        private final KeyPairGenerator generator;
        private final KeyFactory kf;

        NaClServices() {
            try {
                Provider provider = new NaclProvider();
                generator = KeyPairGenerator.getInstance(NaclProvider.NAME, provider);
                kf = KeyFactory.getInstance(NaclProvider.NAME, provider);
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        }
        byte[] readPrivateKey(String path) {
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
        byte[] writePair(String privatePath) {
            Pattern filePattern = Pattern.compile("(.*?)(\\.[a-zA-Z0-9]+)?$");
            Matcher m = filePattern.matcher(privatePath);
            if (! m.matches()) {
                throw new IllegalArgumentException("Invalid file path for the curve secret key: " + privatePath);
            }
            String fileRadix = m.group(1);
            String fileExtension = m.group(2) != null ? m.group(2) : ".p8";
            Path privateKeyPath = Paths.get(fileRadix + fileExtension);
            try {
                generator.initialize(256);
                KeyPair kp = generator.generateKeyPair();
                PrivateKey pv = kp.getPrivate();
                Files.write(privateKeyPath, pv.getEncoded());

                PublicKey pb = kp.getPublic();
                NaclPublicKeySpec naclpubspec = kf.getKeySpec(pb, NaclPublicKeySpec.class);

                // Building the pub file
                String publicEncoded = String.format("Curve %s%n", Base64.getEncoder().encodeToString(naclpubspec.getBytes()));
                Files.write(Paths.get(fileRadix + ".pub"), publicEncoded.getBytes(StandardCharsets.US_ASCII));

                // Building the zpl file
                ZConfig zconf = new ZConfig("root", null);
                zconf.putValue("curve/public-key", ZMQ.Curve.z85Encode(naclpubspec.getBytes()));
                zconf.save(fileRadix + ".zpl");
                return naclpubspec.getBytes();
            } catch (IOException | InvalidKeySpecException ex) {
                throw new IllegalArgumentException(ex);
            }
        }
    }

    private ZMQ.Socket socket;
    private final ZContext ctx;
    private final ZMQConfiguration<?> config;
    @Getter
    private final BlockingQueue<byte[]> logQueue;
    private volatile boolean closed;
    private final Logger logger;
    private final Runnable curveConfigurator;

    public Publisher(String name, Logger logger, ZMQConfiguration<?> config) {
        ctx = new ZContext(1);
        ctx.setLinger(0);
        this.config = config;
        this.curveConfigurator = getCurveConfigurator();
        this.logger = logger;
        logQueue = new ArrayBlockingQueue<>(config.getHwm());
        setName(name);
        setDaemon(true);
        setUncaughtExceptionHandler(this::exceptionHandler);
        closed = false;
        start();
    }

    private Runnable getCurveConfigurator() {
        boolean autoCreate = Optional.ofNullable(System.getProperty("fr.loghub.zmq.curveAutoCreate"))
                                     .map(Boolean::valueOf)
                                     .orElse(config.autoCreate);

        if (config.privateKeyFile != null && ! config.privateKeyFile.isEmpty()) {
            NaClServices nacl = new NaClServices();
            byte[] publicKey = (config.publicKey != null && ! config.publicKey.isEmpty()) ?
                                       Base64.getDecoder().decode(config.publicKey) : null;
            Path privateKeyPath = Paths.get(config.privateKeyFile);
            if (! Files.exists(privateKeyPath) && autoCreate) {
                publicKey = nacl.writePair(config.privateKeyFile);
            }
            if (! Files.exists(privateKeyPath)) {
                throw new IllegalStateException(String.format("ZMQ private key %s file missing", config.privateKeyFile));
            }
            byte[] secretKey = nacl.readPrivateKey(config.privateKeyFile);
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

    @Override
    public void run() {
        try {
            while (! closed) {
                refreshSocket();
                 // Not a blocking wait, it allows to test if closed every 100 ms
                // Needed because interrupt deactivated for this thread
                byte[] log = logQueue.poll(100, TimeUnit.MILLISECONDS);
                sendData(log);
            }
        } catch (InterruptedException e) {
            // End of processing
            Thread.currentThread().interrupt();
        }
        close();
    }

    private synchronized void refreshSocket() throws InterruptedException {
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

    private synchronized void sendData(byte[] log) {
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

    private void exceptionHandler(Thread t, Throwable ex) {
        logger.error(() -> "Critical exception: " + ex.getMessage(), ex);
        socket.close();
        socket = null;
    }

}
