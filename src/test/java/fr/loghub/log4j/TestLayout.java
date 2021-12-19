package fr.loghub.log4j;

import fr.loghub.logservices.FieldsName;
import zmq.ZMQ;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.log4j.NDC;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.msgpack.jackson.dataformat.MessagePackExtensionType;
import org.msgpack.jackson.dataformat.MessagePackFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ.Socket;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TestLayout {

    private static ZContext ctx;
    private static Socket socket;
    private static int port = 1518;

    @BeforeClass
    public static void start() {
        ZContext ctx = new ZContext(1);
        socket = ctx.createSocket(SocketType.PULL);
        socket.bind("tcp://localhost:1518");
    }

    @AfterClass
    public static void stop() {
        Optional.ofNullable(socket).ifPresent(s -> s.close());
        Optional.ofNullable(ctx).ifPresent(s -> s.close());
    }

    @Test
    public void testMsgPack() throws URISyntaxException, InterruptedException, IOException {
        List<Map<String, ?>> allmessages = new ArrayList<>();
        JsonFactory factory = new MessagePackFactory();
        ObjectMapper msgpack = new ObjectMapper(factory);

        org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(TestLayout.class);

        NDC.push("1");
        NDC.push("2");
        logger.debug("message 1", new RuntimeException(new NullPointerException()));
        Thread.sleep(100);

        while ((socket.getEvents() & ZMQ.ZMQ_POLLIN) != 0) {
            @SuppressWarnings("unchecked")
            Map<String, ?> msg = msgpack.readValue(socket.recv(), Map.class);
            allmessages.add(msg);
        }
        Assert.assertEquals(1, allmessages.size());
        Map<String, ?> msg1 = allmessages.remove(0);
        System.out.println(msg1);

        Assert.assertEquals("fr.loghub.log4j.TestLayout", msg1.get(FieldsName.LOGGER));
        Assert.assertTrue(msg1.get(FieldsName.TIMESTAMP) instanceof MessagePackExtensionType);
        Assert.assertFalse(msg1.containsKey("marker"));
        Assert.assertTrue(msg1.containsKey("stack_trace"));
        Assert.assertFalse(msg1.containsKey("contextStack"));
        Assert.assertFalse(msg1.containsKey("contextData"));
        Assert.assertEquals("main", msg1.get(FieldsName.THREADNAME));
        Assert.assertEquals("message 1", msg1.get(FieldsName.MESSAGE));
        Assert.assertEquals("DEBUG", msg1.get(FieldsName.LEVEL));
    }

}
