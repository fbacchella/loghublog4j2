package fr.loghub.log4j2;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.MarkerManager;
import org.apache.logging.log4j.ThreadContext;
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

import fr.loghub.logservices.FieldsName;
import zmq.ZMQ;

public class TestLayout {

    private static ZContext ctx;
    private static Socket socket;
    private static int port = -1;

    @BeforeClass
    public static void start() {
        ctx = new ZContext(1);
        socket = ctx.createSocket(SocketType.PULL);
        port = socket.bindToRandomPort("tcp://localhost");
        System.setProperty("fr.loghub.log4j2.test.port", Integer.toString(port));
    }

    @AfterClass
    public static void stop() {
        Optional.ofNullable(socket).ifPresent(Socket::close);
        Optional.ofNullable(ctx).ifPresent(ZContext::close);
    }

    @Test
    public void testMsgPack() throws URISyntaxException, InterruptedException, IOException {
        List<Map<String, ?>> allmessages = new ArrayList<>();
        List<Map<String, ?>> allgc = new ArrayList<>();
        JsonFactory factory = new MessagePackFactory();
        ObjectMapper msgpack = new ObjectMapper(factory);

        Logger logger = LogManager.getLogger(TestLayout.class);

        logger.debug("message 1", new RuntimeException(new NullPointerException()));
        ThreadContext.push("ThreadContextValue");
        ThreadContext.put("key", "value");
        logger.warn(MarkerManager.getMarker("marker1"), "message 2");
        System.gc();
        Thread.sleep(100);

        while ((socket.getEvents() & ZMQ.ZMQ_POLLIN) != 0) {
            @SuppressWarnings("unchecked")
            Map<String, ?> msg = msgpack.readValue(socket.recv(), Map.class);
            if (msg.get(FieldsName.LOGGER).toString().startsWith("gc.")) {
                allgc.add(msg);
            } else {
                allmessages.add(msg);
            }
        }
        Assert.assertTrue(allmessages.size() == 2);
        Map<String, ?> msg1 = allmessages.remove(0);
        Map<String, ?> msg2 = allmessages.remove(0);

        Assert.assertEquals("fr.loghub.log4j2.TestLayout", msg1.remove(FieldsName.LOGGER));
        Assert.assertEquals("fr.loghub.log4j2.TestLayout", msg2.remove(FieldsName.LOGGER));

        Assert.assertTrue(msg1.get(FieldsName.TIMESTAMP) instanceof MessagePackExtensionType);
        Assert.assertTrue(msg2.get(FieldsName.TIMESTAMP) instanceof MessagePackExtensionType);

        Assert.assertEquals("DEBUG", msg1.get(FieldsName.LEVEL));
        Assert.assertEquals("WARN", msg2.get(FieldsName.LEVEL));

        Assert.assertFalse(msg1.containsKey("marker"));
        Assert.assertTrue(msg2.containsKey("marker"));

        Assert.assertTrue(msg1.containsKey(FieldsName.EXCEPTION));
        Assert.assertFalse(msg2.containsKey(FieldsName.EXCEPTION));

        Assert.assertFalse(msg1.containsKey("contextStack"));
        Assert.assertTrue(msg2.containsKey("contextStack"));

        Assert.assertFalse(msg1.containsKey("contextData"));
        Assert.assertTrue(msg2.containsKey("contextData"));

        Assert.assertEquals("message 1", msg1.get("message"));
        Assert.assertEquals("message 2", msg2.get("message"));

        Assert.assertEquals("1", msg1.get("a"));
        Assert.assertEquals("1", msg2.get("a"));

        Assert.assertEquals(Integer.toString(port), msg1.get("b"));
        Assert.assertEquals(Integer.toString(port), msg2.get("b"));

        // Looking for the "System.gc()" message, but other gc may have been sent
        boolean systemgcFound = false;
        for (Map<String, ?> trygcmsg: allgc) {
            Assert.assertTrue(trygcmsg.containsKey("values"));
            @SuppressWarnings("unchecked")
            Map<String, ?> gcValues = (Map<String, ?>) trygcmsg.get("values");
            Assert.assertTrue(((String)trygcmsg.get(FieldsName.LOGGER)).startsWith("gc."));
            Assert.assertTrue(trygcmsg.get(FieldsName.TIMESTAMP) instanceof MessagePackExtensionType);
            Assert.assertEquals("FATAL", trygcmsg.get(FieldsName.LEVEL));
            if ("System.gc()".equals(gcValues.get("gcCause"))) {
                systemgcFound = true;
            }
            Assert.assertTrue(gcValues.containsKey("gcInfo"));
            Assert.assertEquals("1", trygcmsg.get("a"));
        }
        Assert.assertTrue(systemgcFound);
    }

}
