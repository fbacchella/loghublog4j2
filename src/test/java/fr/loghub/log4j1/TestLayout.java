package fr.loghub.log4j1;

import fr.loghub.logservices.FieldsName;
import zmq.ZMQ;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.log4j.Logger;
import org.apache.log4j.MDC;
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

        Logger logger = Logger.getLogger(TestLayout.class);

        NDC.push("1");
        NDC.push("2");
        logger.debug("message 1", new RuntimeException(new NullPointerException()));
        NDC.remove();
        MDC.put("key", "value");
        logger.warn("message 2");
        Thread.sleep(100);

        while ((socket.getEvents() & ZMQ.ZMQ_POLLIN) != 0) {
            @SuppressWarnings("unchecked")
            Map<String, ?> msg = msgpack.readValue(socket.recv(), Map.class);
            allmessages.add(msg);
        }
        Assert.assertEquals(2, allmessages.size());
        Map<String, ?> msg1 = allmessages.remove(0);
        System.out.println(msg1);
        Assert.assertEquals("fr.loghub.log4j1.TestLayout", msg1.get(FieldsName.LOGGER));
        Assert.assertTrue(msg1.get(FieldsName.INSTANT) instanceof MessagePackExtensionType);
        Assert.assertTrue(msg1.containsKey(FieldsName.EXCEPTION));
        Assert.assertEquals("1 2", msg1.get(FieldsName.CONTEXTSTACK));
        Assert.assertTrue(msg1.containsKey(FieldsName.CONTEXTPROPERTIES));
        Assert.assertEquals("main", msg1.get(FieldsName.THREADNAME));
        Assert.assertEquals("message 1", msg1.get(FieldsName.MESSAGE));
        Assert.assertEquals("DEBUG", msg1.get(FieldsName.LEVEL));

        Map<String, ?> msg2 = allmessages.remove(0);
        Assert.assertEquals("fr.loghub.log4j1.TestLayout", msg2.get(FieldsName.LOGGER));
        Assert.assertTrue(msg2.get(FieldsName.INSTANT) instanceof MessagePackExtensionType);
        Assert.assertFalse(msg2.containsKey(FieldsName.EXCEPTION));
        Assert.assertFalse(msg2.containsKey(FieldsName.CONTEXTSTACK));
        Assert.assertTrue(msg2.containsKey(FieldsName.CONTEXTPROPERTIES));
        Assert.assertEquals("main", msg2.get(FieldsName.THREADNAME));
        Assert.assertEquals("message 2", msg2.get(FieldsName.MESSAGE));
        Assert.assertEquals("WARN", msg2.get(FieldsName.LEVEL));

    }

}
