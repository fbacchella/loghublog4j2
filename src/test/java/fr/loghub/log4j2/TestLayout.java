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
        Assert.assertEquals("fr.loghub.log4j2.TestLayout", msg1.remove(FieldsName.LOGGER));
        Assert.assertEquals("org.apache.logging.log4j.spi.AbstractLogger", msg1.remove(FieldsName.LOGGER_FQCN));
        Assert.assertEquals(Thread.currentThread().getName(), msg1.remove(FieldsName.THREADNAME));
        Assert.assertEquals(Thread.currentThread().getPriority(), msg1.remove(FieldsName.THREADPRIORITY));
        Assert.assertEquals((int)Thread.currentThread().getId(), msg1.remove(FieldsName.THREADID));
        Assert.assertEquals("DEBUG", msg1.remove(FieldsName.LEVEL));
        Assert.assertEquals(false, msg1.remove(FieldsName.ENDOFBATCH));
        Assert.assertTrue(msg1.remove(FieldsName.INSTANT) instanceof MessagePackExtensionType);
        Assert.assertNull(msg1.remove(FieldsName.MARKERS));
        Assert.assertNotNull(msg1.remove(FieldsName.EXCEPTION));

        Assert.assertEquals("message 1", msg1.remove("message"));
        Map<?, ?> props1 = (Map)msg1.remove(FieldsName.CONTEXTPROPERTIES);
        Assert.assertEquals(2, props1.size());
        Assert.assertEquals("1", props1.get("a"));
        Assert.assertEquals(Integer.toString(port), props1.get("b"));
        Assert.assertEquals(msg1.toString(), 0, msg1.size());

        Map<String, ?> msg2 = allmessages.remove(0);
        Assert.assertEquals("fr.loghub.log4j2.TestLayout", msg2.remove(FieldsName.LOGGER));
        Assert.assertEquals("org.apache.logging.log4j.spi.AbstractLogger", msg2.remove(FieldsName.LOGGER_FQCN));
        Assert.assertEquals(Thread.currentThread().getName(), msg2.remove(FieldsName.THREADNAME));
        Assert.assertEquals(Thread.currentThread().getPriority(), msg2.remove(FieldsName.THREADPRIORITY));
        Assert.assertEquals((int)Thread.currentThread().getId(), msg2.remove(FieldsName.THREADID));
        Assert.assertTrue(msg2.remove(FieldsName.INSTANT) instanceof MessagePackExtensionType);
        Assert.assertEquals("WARN", msg2.remove(FieldsName.LEVEL));
        Assert.assertEquals(false, msg2.remove(FieldsName.ENDOFBATCH));
        Map markers2 = (Map) msg2.remove(FieldsName.MARKERS);
        Assert.assertNotNull(markers2);
        Assert.assertNull(msg2.remove(FieldsName.EXCEPTION));
        Assert.assertEquals("message 2", msg2.remove("message"));
        List stack2 = (List) msg2.remove(FieldsName.CONTEXTSTACK);
        Assert.assertEquals(1, stack2.size());
        Assert.assertEquals("ThreadContextValue", stack2.get(0));
        Assert.assertNotNull(stack2);
        Map<?, ?> props2 = (Map)msg2.remove(FieldsName.CONTEXTPROPERTIES);
        Assert.assertEquals(3, props2.size());
        Assert.assertEquals("1", props2.get("a"));
        Assert.assertEquals(Integer.toString(port), props2.get("b"));
        Assert.assertEquals("value", props2.get("key"));
        Assert.assertEquals(msg2.toString(), 0, msg2.size());

        // Looking for the "System.gc()" message, but other gc may have been sent
        boolean systemgcFound = false;
        for (Map<String, ?> trygcmsg: allgc) {
            Assert.assertTrue(trygcmsg.containsKey("values"));
            @SuppressWarnings("unchecked")
            Map<String, ?> gcValues = (Map<String, ?>) trygcmsg.get("values");
            Assert.assertTrue(((String)trygcmsg.get(FieldsName.LOGGER)).startsWith("gc."));
            Assert.assertTrue(trygcmsg.get(FieldsName.INSTANT) instanceof MessagePackExtensionType);
            Assert.assertEquals("FATAL", trygcmsg.get(FieldsName.LEVEL));
            if ("System.gc()".equals(gcValues.get("gcCause"))) {
                systemgcFound = true;
            }
            Assert.assertTrue(gcValues.containsKey("gcInfo"));
            Assert.assertEquals("1", props1.get("a"));
        }
        Assert.assertTrue(systemgcFound);
    }

}
