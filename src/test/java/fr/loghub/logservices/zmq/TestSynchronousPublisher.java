package fr.loghub.logservices.zmq;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.junit.Assert;
import org.junit.Test;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

/**
 * Non-regression test for the {@link SynchronousPublisher} used directly, i.e.
 * without a dedicated thread calling {@code refreshSocket()} beforehand. This is
 * the situation produced by an {@code AsyncLoggerContext}: the {@code ZMQManager}
 * builds a bare synchronous publisher. Before the fix, the very first
 * {@link Publisher#send(byte[])} threw a {@code NullPointerException} because the
 * socket was never initialized.
 */
public class TestSynchronousPublisher {

    private static class CollectingLogger implements Logger {
        final List<String> messages = new ArrayList<>();

        @Override
        public void warn(Supplier<String> message, Throwable t) {
            messages.add(message.get());
        }

        @Override
        public void error(Supplier<String> message, Throwable ex) {
            messages.add(message.get());
        }
    }

    @Test(timeout = 5000)
    public void sendInitializesSocket() {
        CollectingLogger logger = new CollectingLogger();
        try (ZContext ctx = new ZContext(1)) {
            ZMQ.Socket socket = ctx.createSocket(SocketType.PULL);
            int port = socket.bindToRandomPort("tcp://127.0.0.1");
            ZMQConfiguration<?> configuration = ZMQConfiguration.builder()
                                                                .context(this)
                                                                .endpoint("tcp://127.0.0.1:" + port)
                                                                .type(SocketType.PUSH)
                                                                .method(Method.CONNECT)
                                                                .sendHwm(100)
                                                                .recvHwm(100)
                                                                .maxMsgSize(1024)
                                                                .linger(1)
                                                                .build();
            // Directly build a bare synchronous publisher, as ZMQManager does for an
            // AsyncLoggerContext. No thread calls refreshSocket() here.
            Publisher publisher = Publisher.synchronous(logger, configuration);
            try {
                // Before the fix this call threw a NullPointerException (socket == null).
                Assert.assertTrue(publisher.send("hello".getBytes(StandardCharsets.UTF_8)));
                Assert.assertEquals("hello", socket.recvStr());
                // A second send must reuse the already created socket and still work.
                Assert.assertTrue(publisher.send("world".getBytes(StandardCharsets.UTF_8)));
                Assert.assertEquals("world", socket.recvStr());
            } finally {
                publisher.close();
            }
            // No connection error must have been reported.
            Assert.assertEquals(logger.messages.toString(), 0, logger.messages.size());
        }
    }

}
