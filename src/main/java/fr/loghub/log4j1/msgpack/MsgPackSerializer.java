package fr.loghub.log4j1.msgpack;

import fr.loghub.log4j1.Serializer;
import fr.loghub.log4j1.FieldsName;
import fr.loghub.logservices.msgpack.MsgPacker;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

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
            eventMap.put(FieldsName.LOGGERNAME, event.getLoggerName());
            eventMap.put(FieldsName.TIMESTAMP, Instant.ofEpochMilli(event.getTimeStamp()));
            eventMap.put(FieldsName.LEVEL, event.getLevel().toString());
            eventMap.put(FieldsName.THREADNAME, event.getThreadName());
            if (event.locationInformationExists()) {
                Map<String, String> locationinfo = new HashMap<>(4);
                locationinfo.put(FieldsName.LOCATIONINFO_CLASS, event.getLocationInformation().getClassName());
                locationinfo.put(FieldsName.LOCATIONINFO_FILENAME, event.getLocationInformation().getFileName());
                locationinfo.put(FieldsName.LOCATIONINFO_METHOD, event.getLocationInformation().getMethodName());
                locationinfo.put(FieldsName.LOCATIONINFO_LINE, event.getLocationInformation().getLineNumber());
                eventMap.put(FieldsName.LOCATIONINFO, locationinfo);
            }
            Optional.ofNullable(event.getProperties()).filter(m -> ! m.isEmpty()).ifPresent(s -> eventMap.put(FieldsName.PROPERTIES, s));
            Optional.ofNullable(event.getNDC()).filter(s -> ! s.isEmpty()).ifPresent(s -> eventMap.put(FieldsName.NDC, s));
            Optional.ofNullable(event.getThrowableInformation()).ifPresent(ti -> eventMap.put(FieldsName.THROWN, ti.getThrowable()));
            eventMap.put(FieldsName.MESSAGE, event.getRenderedMessage());

            Value v = ValueFactory.newMap(eventMap);

            packer.packValue(v);
            return packer.toByteArray();
        }
    }

}
