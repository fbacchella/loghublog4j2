package fr.loghub.log4j2;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.MarkerManager;
import org.apache.logging.log4j.ThreadContext;
import org.junit.Assert;
import org.junit.Test;
import org.msgpack.jackson.dataformat.MessagePackExtensionType;
import org.msgpack.jackson.dataformat.MessagePackFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ.Socket;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;

import zmq.ZMQ;


public class TestLayout {

    @Test
    public void testMsgPack() throws URISyntaxException, InterruptedException, IOException {
        JsonFactory factory = new MessagePackFactory();
        ObjectMapper msgpack = new ObjectMapper(factory);

        List<Map<String, ?>> allmessages = new ArrayList<>();
        ZContext ctx = new ZContext(1);
        Socket socket = ctx.createSocket(SocketType.PULL);
        int port = -1;
        try {
            port = socket.bindToRandomPort("tcp://localhost");
            System.setProperty("fr.loghub.log4j2.test.port", Integer.toString(port));
            Logger logger = LogManager.getLogger(TestLayout.class);
            logger.debug("message 1", new RuntimeException());
            ThreadContext.push("ThreadContextValue");
            ThreadContext.put("key", "value");
            logger.warn(MarkerManager.getMarker("marker1"), "message 2");
            System.gc();
            Thread.sleep(100);

            while ((socket.getEvents() & ZMQ.ZMQ_POLLIN) != 0) {
                @SuppressWarnings("unchecked")
                Map<String, ?> msg = msgpack.readValue(socket.recv(), Map.class);
                allmessages.add(msg);
            }
        } finally {
            socket.close();
            ctx.close();
        }
        Assert.assertEquals(4, allmessages.size());
        Map<String, ?> msg1 = allmessages.get(0);
        Map<String, ?> msg2 = allmessages.get(1);
        Map<String, ?> msg3 = allmessages.get(2);
        Map<String, ?> msg4 = allmessages.get(3);
        @SuppressWarnings("unchecked")
        Map<String, ?> gcValues3 = (Map<String, ?>) msg3.get("values");
        @SuppressWarnings("unchecked")
        Map<String, ?> gcValues4 = (Map<String, ?>) msg4.get("values");

        Assert.assertEquals("fr.loghub.log4j2.TestLayout", msg1.get("loggerName"));
        Assert.assertEquals("fr.loghub.log4j2.TestLayout", msg2.get("loggerName"));
        Assert.assertTrue(((String)msg3.get("loggerName")).startsWith("gc."));
        Assert.assertTrue(((String)msg4.get("loggerName")).startsWith("gc."));

        Assert.assertTrue(msg1.get("instant") instanceof MessagePackExtensionType);
        Assert.assertTrue(msg2.get("instant") instanceof MessagePackExtensionType);
        Assert.assertTrue(msg3.get("instant") instanceof MessagePackExtensionType);
        Assert.assertTrue(msg3.get("instant") instanceof MessagePackExtensionType);

        Assert.assertFalse(msg1.containsKey("marker"));
        Assert.assertTrue(msg2.containsKey("marker"));

        Assert.assertTrue(msg1.containsKey("thrown"));
        Assert.assertFalse(msg2.containsKey("thrown"));

        Assert.assertFalse(msg1.containsKey("contextStack"));
        Assert.assertTrue(msg2.containsKey("contextStack"));

        Assert.assertFalse(msg1.containsKey("contextData"));
        Assert.assertTrue(msg2.containsKey("contextData"));

        Assert.assertEquals("message 1", msg1.get("message"));
        Assert.assertEquals("message 2", msg2.get("message"));

        Assert.assertEquals("DEBUG", msg1.get("level"));
        Assert.assertEquals("WARN", msg2.get("level"));
        Assert.assertEquals("FATAL", msg3.get("level"));
        Assert.assertEquals("FATAL", msg4.get("level"));

        Assert.assertEquals("System.gc()", gcValues3.get("gcCause"));
        Assert.assertEquals("System.gc()", gcValues4.get("gcCause"));

        Assert.assertTrue(gcValues3.containsKey("gcInfo"));
        Assert.assertTrue(gcValues4.containsKey("gcInfo"));

        Assert.assertEquals("1", msg1.get("a"));
        Assert.assertEquals("1", msg2.get("a"));
        Assert.assertEquals("1", msg3.get("a"));
        Assert.assertEquals("1", msg4.get("a"));

        Assert.assertEquals(Integer.toString(port), msg1.get("b"));
        Assert.assertEquals(Integer.toString(port), msg2.get("b"));
        Assert.assertEquals(Integer.toString(port), msg3.get("b"));
        Assert.assertEquals(Integer.toString(port), msg4.get("b"));
    }

}
