package fr.loghub.logback.encoder.msgpack;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Marker;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.encoder.EncoderBase;
import fr.loghub.logback.FieldsName;
import fr.loghub.logservices.msgpack.MsgPacker;

public class MsgPackEncoder extends EncoderBase<ILoggingEvent> {

    private final List<AdditionalField> additionalFieldList = new ArrayList<>();

    @Override
    public byte[] headerBytes() {
        return null;
    }

    @Override
    public byte[] encode(ILoggingEvent event) {
        try (MsgPacker eventMap = new MsgPacker(7)){
            eventMap.put(FieldsName.SEQUENCENUMBER, event.getSequenceNumber());
            eventMap.put(FieldsName.INSTANT, event.getInstant());
            eventMap.put(FieldsName.THREADNAME, event.getThreadName());
            eventMap.put(FieldsName.LEVEL, event.getLevel().toString());
            eventMap.put(FieldsName.LOGGERNAME, event.getLoggerName());
            eventMap.put(FieldsName.MESSAGE, event.getFormattedMessage());
            eventMap.put(FieldsName.PROPERTYMAP, event.getMDCPropertyMap());
            if (event.getMarkerList() != null && ! event.getMarkerList().isEmpty()) {
                List<String> markers = new ArrayList<>();
                event.getMarkerList().forEach(m -> resolveMark(m, markers));
                eventMap.put(FieldsName.MARKERS, markers);
            }
            if (event.getArgumentArray() != null && event.getArgumentArray().length !=0) {
                eventMap.put(FieldsName.ARGUMENTARRAY, event.getArgumentArray());
            }
            Optional.ofNullable(event.getThrowableProxy()).ifPresent(t -> eventMap.put(FieldsName.THROWN, ((ThrowableProxy)event.getThrowableProxy()).getThrowable()));

            Map<String, String> contextData = new HashMap<>(additionalFieldList.size() + event.getMDCPropertyMap().size());
            if (! additionalFieldList.isEmpty()) {
                additionalFieldList.stream()
                                   .filter(pair -> ! contextData.containsKey(pair.getName()))
                                   .forEach(pair -> contextData.put(pair.getName(), pair.getValue()));
            }
            contextData.putAll(event.getMDCPropertyMap());
            if (! contextData.isEmpty()) {
                eventMap.put(FieldsName.PROPERTYMAP, contextData);
            }
            return eventMap.getBytes();
        } catch (IOException | RuntimeException e) {
            addError("Can't serialize an event:  " + e.getMessage(), e);
            return null;
        }
    }

    private void resolveMark(Marker m, List<String> markers) {
        markers.add(m.getName());
        m.iterator().forEachRemaining(i -> resolveMark(i, markers));
    }

    @Override
    public byte[] footerBytes() {
        return null;
    }

    public void addAdditionalField(AdditionalField additionalField) {
        additionalFieldList.add(additionalField);
    }

}
