package fr.loghub.logservices.zmq;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
            byte[] serverPublicKey = Publisher.NaClServices.writePair(serverKeyPath);
            byte[] serverSecretKey = Publisher.NaClServices.readPrivateKey(serverKeyPath);
            socket.setCurveSecretKey(serverSecretKey);
            socket.setCurvePublicKey(serverPublicKey);
            socket.setCurveServer(true);
            String serverPublicKeyString = ZMQ.Curve.z85Encode(serverPublicKey);
            ZMQConfiguration<?> configuration = new ZMQConfiguration<>(this, "tcp://127.0.0.1:" + port,
                    SocketType.PUSH, Method.CONNECT, 100, 1024, 1, serverPublicKeyString, clientKeyPath, null);
            Publisher pub = new Publisher("totor", logger, configuration);
            Assert.assertTrue(pub.getLogQueue().offer("hello".getBytes(StandardCharsets.UTF_8)));
            Assert.assertEquals("hello", socket.recvStr());
            pub.close();
            ZConfig clientZpl = ZConfig.load(clientKeyPath.replace(".p8", ".zpl"));
            byte[] pubKey = ZMQ.Curve.z85Decode(clientZpl.getValue("curve/public-key"));
            Assert.assertEquals(32, pubKey.length);
        }
    }

}
