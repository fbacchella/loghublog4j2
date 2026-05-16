package fr.loghub.logservices.zmq;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import org.zeromq.SocketConfigurator;
import org.zeromq.SocketType;
import org.zeromq.Method;

import com.neilalexander.jnacl.crypto.curve25519;
import com.neilalexander.jnacl.crypto.curve25519xsalsa20poly1305;

import static fr.loghub.logservices.zmq.Publisher.PROPERTY_AUTOCREATE;
import static fr.loghub.logservices.zmq.Publisher.PROPERTY_PRIVATEKEYFILE;

public class ZMQConfiguration<C> {

    public static final SocketType DEFAULT_TYPE = SocketType.PUB;
    public static final Method DEFAULT_METHOD = Method.CONNECT;

    public final C context;
    public final String endpoint;
    public final SocketConfigurator configurator;
    public final SocketType type;
    public final Method method;
    public final int ioThreads;

    private ZMQConfiguration(Builder<C> builder) {
        this.context = builder.context;
        this.endpoint = builder.endpoint;
        configureCurve(builder);
        this.configurator = builder.configuratorBuilder.build();
        this.type = builder.type;
        this.method = builder.method;
        this.ioThreads = builder.ioThreads;
    }

    public static <C> Builder<C> builder() {
        return new Builder<>();
    }

    public static class Builder<C> {
        private C context;
        private String endpoint;
        private SocketConfigurator.Builder configuratorBuilder;
        private SocketType type = DEFAULT_TYPE;
        private Method method = DEFAULT_METHOD;
        private Path privateKeyFile = null;
        private boolean autoCreate = false;
        private int ioThreads = 1;

        public Builder<C> context(C context) {
            if (context == null) throw new NullPointerException("context is marked non-null but is null");
            this.context = context;
            return this;
        }

        public Builder<C> endpoint(String endpoint) {
            if (endpoint == null) throw new NullPointerException("endpoint is marked non-null but is null");
            this.endpoint = endpoint;
            return this;
        }

        public Builder<C> configurator(SocketConfigurator.Builder configuratorBuilder) {
            this.configuratorBuilder = configuratorBuilder;
            return this;
        }

        public Builder<C> type(SocketType type) {
            this.type = type;
            return this;
        }

        public Builder<C> method(Method method) {
            this.method = method;
            return this;
        }

        public Builder<C> privateKeyFile(Path privateKeyFile) {
            this.privateKeyFile = privateKeyFile;
            return this;
        }

        public Builder<C> autoCreate(boolean autoCreate) {
            this.autoCreate = autoCreate;
            return this;
        }

        public Builder<C> ioThreads(int ioThreads) {
            this.ioThreads = ioThreads;
            return this;
        }

        public ZMQConfiguration<C> build() {
            if (context == null) throw new NullPointerException("context is marked non-null but is null");
            if (endpoint == null) throw new NullPointerException("endpoint is marked non-null but is null");
            return new ZMQConfiguration<>(this);
        }
    }

    private void configureCurve(Builder<C> builder) {
        boolean autoCreate = Optional.ofNullable(System.getProperty(PROPERTY_AUTOCREATE))
                                     .map(Boolean::valueOf)
                                     .orElse(builder.autoCreate);
        Path privateKeyPath = Optional.ofNullable(System.getProperty(PROPERTY_PRIVATEKEYFILE))
                                      .filter(s -> ! s.isEmpty())
                                      .map(Paths::get)
                                      .orElse(builder.privateKeyFile);
        if (privateKeyPath != null) {
            NaClServices nacl = new NaClServices();
            byte[] publicKey;
            if (! Files.exists(privateKeyPath) && autoCreate) {
                publicKey = nacl.writePair(privateKeyPath);
            } else if (! Files.exists(privateKeyPath)) {
                throw new IllegalStateException(String.format("ZMQ private key %s file missing", privateKeyPath));
            } else {
                publicKey = null;
            }
            byte[] secretKey = nacl.readPrivateKey(privateKeyPath);
            if (publicKey == null) {
                publicKey = new byte[curve25519xsalsa20poly1305.crypto_secretbox_PUBLICKEYBYTES];
                curve25519.crypto_scalarmult_base(publicKey, secretKey);
            }
            builder.configuratorBuilder.curvePublicKey(publicKey);
            builder.configuratorBuilder.curveSecretKey(secretKey);
        }
    }


}
