package fr.loghub.logservices.zmq;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.function.Supplier;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.zeromq.SocketType;
import org.zeromq.ZConfig;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

public class TestCurve {

    @Rule
    public TemporaryFolder testFolder = new TemporaryFolder();

    private final NaClServices nacl = new NaClServices();

    @Test(timeout = 2000)
    public void publisher() throws IOException {
        Logger logger = new Logger() {

            @Override
            public void warn(Supplier<String> message, Throwable t) {
            }

            @Override
            public void error(Supplier<String> message, Throwable ex) {

            }
        };
        try (ZContext ctx = new ZContext(1)) {
            ZMQ.Socket socket = ctx.createSocket(SocketType.PULL);
            int port = socket.bindToRandomPort("tcp://127.0.0.1");
            String serverKeyPath = testFolder.getRoot().toPath().resolve(Paths.get("serversecret.p8")).toString();
            String clientKeyPath = testFolder.getRoot().toPath().resolve(Paths.get("clientsecret.p8")).toString();
            byte[] serverPublicKey = nacl.writePair(serverKeyPath);
            byte[] serverSecretKey = nacl.readPrivateKey(serverKeyPath);
            socket.setCurveSecretKey(serverSecretKey);
            socket.setCurvePublicKey(serverPublicKey);
            socket.setCurveServer(true);
            String serverPublicKeyString = Base64.getEncoder().encodeToString(serverPublicKey);
            ZMQConfiguration<?> configuration = ZMQConfiguration.builder()
                                                                .context(this)
                                                                .endpoint("tcp://127.0.0.1:" + port)
                                                                .type(SocketType.PUSH)
                                                                .method(Method.CONNECT)
                                                                .sendHwm(100)
                                                                .recvHwm(100)
                                                                .maxMsgSize(1024)
                                                                .linger(1)
                                                                .peerPublicKey(serverPublicKeyString)
                                                                .privateKeyFile(clientKeyPath)
                                                                .autoCreate(true)
                                                                .build();
            AsynchronousPublisher pub = new AsynchronousPublisher("testcurve", logger, configuration);
            Assert.assertTrue(pub.send("hello".getBytes(StandardCharsets.UTF_8)));
            Assert.assertEquals("hello", socket.recvStr());
            pub.close();
            ZConfig clientZpl = ZConfig.load(clientKeyPath.replace(".p8", ".zpl"));
            byte[] pubKey = ZMQ.Curve.z85Decode(clientZpl.getValue("curve/public-key"));
            Assert.assertEquals(32, pubKey.length);
        }
    }

    @Test
    public void testFilePattern() {
        checkCreated(testFolder.getRoot().toPath().resolve(Paths.get("file1.p8")), "file1.p8", "file1.zpl");
        checkCreated(testFolder.getRoot().toPath().resolve(Paths.get("file2")), "file2.p8", "file2.zpl");
        checkCreated(testFolder.getRoot().toPath().resolve(Paths.get("file3.pkcs8")), "file3.pkcs8", "file3.zpl");
        checkCreated(testFolder.getRoot().toPath().resolve(Paths.get("file4.sub.p8")), "file4.sub.p8", "file4.sub.zpl");
    }

    private void checkCreated(Path keyPath, String privateKey, String publicKey) {
        nacl.writePair(keyPath.toString());
        Assert.assertTrue(Files.exists(keyPath.getParent().resolve(privateKey)));
        Assert.assertTrue(Files.exists(keyPath.getParent().resolve(publicKey)));
    }

    private void runPublisher(ZMQConfiguration<?> config) throws InterruptedException {
        List<String> messages = new ArrayList<>();
        Logger customLogger = new Logger() {

            @Override
            public void warn(Supplier<String> message, Throwable t) {
                messages.add(message.get());
            }

            @Override
            public void error(Supplier<String> message, Throwable ex) {
                messages.add(message.get());
            }
        };
        AsynchronousPublisher pub = new AsynchronousPublisher("testpublisher", customLogger, config);
        System.clearProperty(Publisher.PROPERTY_AUTOCREATE);
        System.clearProperty(Publisher.PROPERTY_PRIVATEKEYFILE);
        Thread.sleep(100);
        pub.close();
        pub.join(100);
        Assert.assertEquals(0, messages.size());
    }

    @Test
    public void testAutoCreateProps() throws InterruptedException {
        Path privateKeyFile = testFolder.getRoot().toPath().resolve("curve.p8");
        System.setProperty(Publisher.PROPERTY_AUTOCREATE, "true");
        System.setProperty(Publisher.PROPERTY_PRIVATEKEYFILE, privateKeyFile.toString());
        ZMQConfiguration<?> config = ZMQConfiguration.builder()
                                                    .context(this)
                                                    .endpoint("tcp://localhost:0")
                                                    .type(SocketType.PULL)
                                                    .method(Method.BIND)
                                                    .sendHwm(100)
                                                    .recvHwm(100)
                                                    .maxMsgSize(100)
                                                    .linger(0)
                                                    .autoCreate(false)
                                                    .build();
        runPublisher(config);
        Assert.assertTrue(Files.exists(privateKeyFile));
    }

    @Test
    public void testAutoCreateSettings() throws InterruptedException {
        Path privateKeyFile = testFolder.getRoot().toPath().resolve("curve.p8");
        System.clearProperty(Publisher.PROPERTY_AUTOCREATE);
        System.clearProperty(Publisher.PROPERTY_PRIVATEKEYFILE);
        ZMQConfiguration<?> config = ZMQConfiguration.builder()
                                             .context(this)
                                             .endpoint("tcp://localhost:0")
                                             .type(SocketType.PULL)
                                             .method(Method.BIND)
                                             .sendHwm(100)
                                             .recvHwm(100)
                                             .maxMsgSize(100)
                                             .linger(0)
                                             .privateKeyFile(privateKeyFile.toString())
                                             .autoCreate(true)
                                             .build();
        runPublisher(config);
        Assert.assertTrue(Files.exists(privateKeyFile));
    }

    @Test
    public void testNoAutoCreate() {
        System.setProperty(Publisher.PROPERTY_AUTOCREATE, "false");
        Path privateKeyFile = testFolder.getRoot().toPath().resolve("curve.p8");
        System.setProperty(Publisher.PROPERTY_PRIVATEKEYFILE, privateKeyFile.toString());
        ZMQConfiguration<?> config = ZMQConfiguration.builder()
                                             .context(this)
                                             .endpoint("tcp://localhost:0")
                                             .type(SocketType.PULL)
                                             .method(Method.BIND)
                                             .sendHwm(100)
                                             .recvHwm(100)
                                             .maxMsgSize(100)
                                             .linger(0)
                                             .autoCreate(true)
                                             .build();
        IllegalStateException ex = Assert.assertThrows(IllegalStateException.class, () -> runPublisher(config));
        Assert.assertTrue(ex.getMessage().endsWith("file missing"));
    }

}
