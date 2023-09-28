package fr.loghub.log4j1;

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
        ctx = new ZContext(1);
        socket = ctx.createSocket(SocketType.PULL);
        socket.bind("tcp://localhost:1518");
    }

    @AfterClass
    public static void stop() {
        Optional.ofNullable(socket).ifPresent(Socket::close);
        Optional.ofNullable(ctx).ifPresent(ZContext::close);
    }

    @Test(timeout = 5000)
    public void testMsgPack() throws InterruptedException, IOException {
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

        do  {
            @SuppressWarnings("unchecked")
            Map<String, ?> msg = msgpack.readValue(socket.recv(), Map.class);
            allmessages.add(msg);
            Thread.sleep(100);
        } while ((socket.getEvents() & ZMQ.ZMQ_POLLIN) != 0);

        Assert.assertEquals(2, allmessages.size());
        Map<String, ?> msg1 = allmessages.remove(0);
        Assert.assertEquals("fr.loghub.log4j1.TestLayout", msg1.remove(FieldsName.LOGGERNAME));
        Assert.assertTrue(msg1.remove(FieldsName.TIMESTAMP) instanceof MessagePackExtensionType);
        Assert.assertNotNull(msg1.remove(FieldsName.THROWN));
        Assert.assertEquals("1 2", msg1.remove(FieldsName.NDC));
        Assert.assertTrue(msg1.containsKey(FieldsName.PROPERTIES));
        Assert.assertEquals("Time-limited test", msg1.remove(FieldsName.THREADNAME));
        Assert.assertEquals("message 1", msg1.remove(FieldsName.MESSAGE));
        Assert.assertEquals("DEBUG", msg1.remove(FieldsName.LEVEL));
        Map<String, Object> location_info1 = (Map<String, Object>) msg1.remove(FieldsName.LOCATIONINFO);
        Assert.assertEquals("TestLayout.java", location_info1.remove(FieldsName.LOCATIONINFO_FILENAME));
        Assert.assertEquals("testMsgPack", location_info1.remove(FieldsName.LOCATIONINFO_METHOD));
        Assert.assertEquals("fr.loghub.log4j1.TestLayout", location_info1.remove(FieldsName.LOCATIONINFO_CLASS));
        Assert.assertNotNull(location_info1.remove(FieldsName.LOCATIONINFO_LINE));
        Assert.assertTrue(location_info1.isEmpty());
        Map<String, Object> properties1 = (Map<String, Object>) msg1.remove(FieldsName.PROPERTIES);
        Assert.assertEquals("testing", properties1.remove("application"));
        Assert.assertEquals("localhost", properties1.remove("hostname"));
        Assert.assertTrue(properties1.isEmpty());
        Assert.assertTrue(msg1.isEmpty());

        Map<String, ?> msg2 = allmessages.remove(0);
        Assert.assertEquals("fr.loghub.log4j1.TestLayout", msg2.remove(FieldsName.LOGGERNAME));
        Assert.assertTrue(msg2.remove(FieldsName.TIMESTAMP) instanceof MessagePackExtensionType);
        Assert.assertFalse(msg2.containsKey(FieldsName.THROWN));
        Assert.assertTrue(msg2.containsKey(FieldsName.PROPERTIES));
        Assert.assertEquals("Time-limited test", msg2.remove(FieldsName.THREADNAME));
        Assert.assertEquals("message 2", msg2.remove(FieldsName.MESSAGE));
        Assert.assertEquals("WARN", msg2.remove(FieldsName.LEVEL));
        Map<String, Object> location_info2 = (Map<String, Object>) msg2.remove(FieldsName.LOCATIONINFO);
    }

}
