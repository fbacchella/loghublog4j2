package fr.loghub.log4j1.msgpack;

import fr.loghub.log4j1.Serializer;
import fr.loghub.logservices.FieldsName;
import fr.loghub.logservices.msgpack.MsgPacker;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.spi.LoggingEvent;
import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessagePack;
import org.msgpack.value.Value;
import org.msgpack.value.ValueFactory;

public class MsgPackSerializer implements Serializer {

    @Override
    public byte[] objectToBytes(LoggingEvent event) throws IOException {
        try (MessageBufferPacker packer = MessagePack.newDefaultBufferPacker()){
            MsgPacker eventMap = new MsgPacker(16);
            eventMap.put(FieldsName.LOGGER, event.getLoggerName());
            eventMap.put(FieldsName.TIMESTAMP, Instant.ofEpochMilli(event.getTimeStamp()));
            eventMap.put(FieldsName.LEVEL, event.getLevel().toString());
            eventMap.put(FieldsName.THREADNAME, event.getThreadName());
            if (event.locationInformationExists()) {
                Map<String, String> locationinfo = new HashMap<>(4);
                locationinfo.put(FieldsName.LOCATIONINFO_CLASS, event.getLocationInformation().getClassName());
                locationinfo.put(FieldsName.LOCATIONINFO_FILE, event.getLocationInformation().getFileName());
                locationinfo.put(FieldsName.LOCATIONINFO_METHOD, event.getLocationInformation().getMethodName());
                locationinfo.put(FieldsName.LOCATIONINFO_LINE, event.getLocationInformation().getLineNumber());
                eventMap.put(FieldsName.LOCATIONINFO, locationinfo);
            }
            eventMap.put("NDC", event.getNDC());
            if (event.getThrowableInformation() != null) {
                eventMap.put(FieldsName.EXCEPTION, event.getThrowableInformation().getThrowable());
            }
            @SuppressWarnings("unchecked")
            Map<String, ?> m = event.getProperties();
            if (m.size() > 0) {
                eventMap.put("properties", m);
            }
            eventMap.put(FieldsName.MESSAGE, event.getRenderedMessage());

            Value v = ValueFactory.newMap(eventMap);

            packer.packValue(v);
            return packer.toByteArray();
        }
    }
}
