package fr.loghub.logback;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.msgpack.jackson.dataformat.MessagePackExtensionType;
import org.msgpack.jackson.dataformat.MessagePackFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ.Socket;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;

import ch.qos.logback.classic.LoggerContext;
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
        System.setProperty("fr.loghub.logback.test.port", Integer.toString(port));
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

        Logger logger = LoggerFactory.getLogger(TestLayout.class);
        String uuid = UUID.randomUUID().toString().replace("-", "");
        MDC.put("traceid", uuid);
        Marker testing = MarkerFactory.getMarker("TESTING_CONTEXT");

        logger.debug("Exception 1", new RuntimeException(new NullPointerException()));
        logger.info(testing, "Message 1");

        do {
            @SuppressWarnings("unchecked")
            Map<String, ?> msg = msgpack.readValue(socket.recv(), Map.class);
            allmessages.add(msg);
            Thread.sleep(100);
        } while ((socket.getEvents() & ZMQ.ZMQ_POLLIN) != 0);

        Assert.assertEquals(2, allmessages.size());
        Map<String, ?> msg1 = allmessages.remove(0);
        Assert.assertEquals("fr.loghub.logback.TestLayout", msg1.remove(FieldsName.LOGGERNAME));
        Assert.assertEquals(Thread.currentThread().getName(), msg1.remove(FieldsName.THREADNAME));
        Assert.assertEquals("DEBUG", msg1.remove(FieldsName.LEVEL));
        Assert.assertEquals(MessagePackExtensionType.class, msg1.remove(FieldsName.TIMESTAMP).getClass());
        Assert.assertNotNull(msg1.remove(FieldsName.THROWN));
        Assert.assertEquals("Exception 1", msg1.remove(FieldsName.MESSAGE));
        Map<?, ?> props1 = (Map) msg1.remove(FieldsName.PROPERTYMAP);
        Assert.assertEquals(3, props1.size());
        Assert.assertEquals("1", props1.get("name1"));
        Assert.assertEquals("2", props1.get("name2"));
        Assert.assertEquals(uuid, props1.get("traceid"));
        Assert.assertEquals(msg1.toString(), 0, msg1.size());

        Map<String, ?> msg2 = allmessages.remove(0);
        Assert.assertEquals("fr.loghub.logback.TestLayout", msg2.remove(FieldsName.LOGGERNAME));
        Assert.assertEquals(Thread.currentThread().getName(), msg2.remove(FieldsName.THREADNAME));
        Assert.assertTrue(msg2.remove(FieldsName.TIMESTAMP) instanceof MessagePackExtensionType);
        Assert.assertEquals("INFO", msg2.remove(FieldsName.LEVEL));
        Assert.assertNull(msg2.remove(FieldsName.THROWN));
        Assert.assertEquals("Message 1", msg2.remove(FieldsName.MESSAGE));
        Map<?, ?> props2 = (Map) msg2.remove(FieldsName.PROPERTYMAP);
        Assert.assertEquals(3, props2.size());
        Assert.assertEquals("1", props2.get("name1"));
        Assert.assertEquals("2", props2.get("name2"));
        Assert.assertEquals(uuid, props2.get("traceid"));
        List markers = (List) msg2.remove(FieldsName.MARKERS);
        Assert.assertEquals(1, markers.size());
        Assert.assertEquals("TESTING_CONTEXT", markers.get(0));
        Assert.assertEquals(msg2.toString(), 0, msg2.size());

        Assert.assertEquals(0, StatusListener.statuses.size());
    }

}
