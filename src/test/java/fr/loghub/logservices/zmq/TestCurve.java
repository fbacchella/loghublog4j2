package fr.loghub.logservices.zmq;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    private final Publisher.NaClServices nacl = new Publisher.NaClServices();

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
            String serverPublicKeyString = ZMQ.Curve.z85Encode(serverPublicKey);
            ZMQConfiguration<?> configuration = new ZMQConfiguration<>(this, "tcp://127.0.0.1:" + port,
                    SocketType.PUSH, Method.CONNECT, 100, 1024, 1, serverPublicKeyString, clientKeyPath, null, -1);
            Publisher pub = new Publisher("testcurve", logger, configuration);
            Assert.assertTrue(pub.getLogQueue().offer("hello".getBytes(StandardCharsets.UTF_8)));
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

}