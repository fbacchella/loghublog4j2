package fr.loghub.logservices.zmq;

import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.zeromq.Curve;
import org.zeromq.Method;
import org.zeromq.SocketConfigurator;
import org.zeromq.SocketType;
import org.zeromq.ZConfig;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import fr.loghub.logservices.zmq.ZMQConfiguration.Builder;
import zmq.io.mechanism.curve.CurveMechanismSettings;

public class TestCurve {

    @Rule
    public TemporaryFolder testFolder = new TemporaryFolder();

    private final NaClServices nacl = new NaClServices();

    @Before
    public void clear() {
        System.clearProperty(Publisher.PROPERTY_AUTOCREATE);
        System.clearProperty(Publisher.PROPERTY_PRIVATEKEYFILE);
    }

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
        try (ZContext ctx = new ZContext(1);
             ZMQ.Socket socket = ctx.createSocket(SocketType.PULL)
        ) {
            Path keydir =  testFolder.newFolder("keys").toPath();
            Path serverKeyPath = keydir.resolve("serversecret.p8");
            Path clientKeyPath = keydir.resolve("clientsecret.p8");
            byte[] serverPublicKey = nacl.writePair(serverKeyPath);
            byte[] serverSecretKey = nacl.readPrivateKey(serverKeyPath);
            CurveMechanismSettings curveMechanism = CurveMechanismSettings.getBuilder()
                                                                  .setSecretKey(serverSecretKey)
                                                                  .setPublicKey(serverPublicKey)
                                                                  .build();
            socket.setMechanism(curveMechanism);
            int port = socket.bindToRandomPort("tcp://127.0.0.1");
            SocketConfigurator.Builder scBuilder = SocketConfigurator.builder();
            scBuilder.curvePeerPublicKey(serverPublicKey);
            ZMQConfiguration<?> configuration = ZMQConfiguration.builder()
                                                                .context(this)
                                                                .endpoint("tcp://127.0.0.1:" + port)
                                                                .type(SocketType.PUSH)
                                                                .method(Method.CONNECT)
                                                                .configurator(scBuilder)
                                                                .privateKeyFile(clientKeyPath)
                                                                .autoCreate(true)
                                                                .build();
            AsynchronousPublisher pub = new AsynchronousPublisher("testcurve", logger, configuration);
            Assert.assertTrue(pub.send("hello".getBytes(StandardCharsets.UTF_8)));
            Assert.assertEquals("hello", socket.recvStr());
            pub.close();
            ZConfig clientZpl = ZConfig.load(new FileReader(clientKeyPath.toString().replace(".p8", ".zpl")));
            byte[] pubKey = Curve.z85Decode(clientZpl.getValue("curve/public-key"));
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
        nacl.writePair(keyPath);
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
                                                    .configurator(SocketConfigurator.builder())
                                                    .endpoint("tcp://localhost:0")
                                                    .type(SocketType.PULL)
                                                    .method(Method.BIND)
                                                    .autoCreate(false)
                                                    .build();
        runPublisher(config);
        Assert.assertTrue(Files.exists(privateKeyFile));
    }

    @Test
    public void testAutoCreateSettings() throws InterruptedException {
        Path privateKeyFile = testFolder.getRoot().toPath().resolve("curve.p8");
        ZMQConfiguration<?> config = ZMQConfiguration.builder()
                                             .context(this)
                                             .configurator(SocketConfigurator.builder())
                                             .endpoint("tcp://localhost:0")
                                             .type(SocketType.PULL)
                                             .method(Method.BIND)
                                             .privateKeyFile(privateKeyFile)
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
        Builder<?> config = ZMQConfiguration.builder()
                                            .context(this)
                                            .endpoint("tcp://localhost:0")
                                            .type(SocketType.PULL)
                                            .method(Method.BIND)
                                            .autoCreate(true);
        IllegalStateException ex = Assert.assertThrows(IllegalStateException.class, config::build);
        Assert.assertTrue(ex.getMessage().endsWith("file missing"));
    }

}
