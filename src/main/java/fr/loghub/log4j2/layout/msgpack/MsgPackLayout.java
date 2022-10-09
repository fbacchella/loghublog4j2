package fr.loghub.log4j2.layout.msgpack;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AppenderLoggingException;
import org.apache.logging.log4j.core.config.Node;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderFactory;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.layout.AbstractLayout;
import org.apache.logging.log4j.core.lookup.StrSubstitutor;
import org.apache.logging.log4j.message.MapMessage;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.ObjectArrayMessage;

import fr.loghub.log4j2.FieldsName;
import fr.loghub.logservices.msgpack.MsgPacker;

@Plugin(name = "MsgPackLayout", category = Node.CATEGORY, elementType = Layout.ELEMENT_TYPE, printObject = true)
public class MsgPackLayout extends AbstractLayout<Message> {

    public static class Builder extends AbstractLayout.Builder<Builder>
    implements org.apache.logging.log4j.core.util.Builder<MsgPackLayout> {

        @PluginElement("AdditionalField")
        private AdditionalField[] additionalFields = new AdditionalField[0];

        @PluginBuilderAttribute
        private boolean locationInfo = false;

        @Override
        public MsgPackLayout build() {
            return new MsgPackLayout(this);
        }

    }

    @PluginBuilderFactory
    public static Builder newBuilder() {
        return new Builder();
    }

    private final AdditionalField[] additionalFields;
    private final boolean locationInfo;

    protected MsgPackLayout(Builder builder) {
        super(builder.getConfiguration(), null, null);
        additionalFields = builder.additionalFields;
        locationInfo = builder.locationInfo;
    }

    private List<String> resolveMark(Marker m, List<String> markers) {
        markers.add(m.getName());
        Optional.ofNullable(m.getParents())
                .map(Arrays::stream)
                .orElse(Stream.empty())
                .forEach(i -> resolveMark(i, markers));
        return markers;
    }

    @Override
    public byte[] toByteArray(LogEvent event) {
        try (MsgPacker eventMap = new MsgPacker(11)){
            eventMap.put(FieldsName.INSTANT, Instant.ofEpochSecond(event.getInstant().getEpochSecond(), event.getInstant().getNanoOfSecond()));
            eventMap.put(FieldsName.THREADNAME, event.getThreadName());
            eventMap.put(FieldsName.THREADPRIORITY, event.getThreadPriority());
            eventMap.put(FieldsName.THREADID, event.getThreadId());
            eventMap.put(FieldsName.LEVEL, event.getLevel().name());
            eventMap.put(FieldsName.LOGGERNAME, event.getLoggerName());
            eventMap.put(FieldsName.LOGGER_FQCN, event.getLoggerFqcn());
            eventMap.put(FieldsName.ENDOFBATCH, event.isEndOfBatch());
            Optional.ofNullable(event.getMarker())
                    .map(m -> resolveMark(m, new ArrayList<>()))
                    .ifPresent(m -> eventMap.put(FieldsName.MARKERS, m));
            Message msg = event.getMessage();
            if (msg instanceof MapMessage) {
                @SuppressWarnings({ "rawtypes", "unchecked" })
                Map<String, ?> map = ((MapMessage)msg).getData();
                eventMap.put(FieldsName.DATA, map);
            } else if (msg instanceof ObjectArrayMessage) {
                Object[] params = msg.getParameters();
                eventMap.put(FieldsName.PARAMETERS, Arrays.asList(params));
            } else {
                eventMap.put(FieldsName.MESSAGE, event.getMessage().getFormattedMessage());
            }
            Optional.ofNullable(event.getThrown()).ifPresent(t -> eventMap.put(FieldsName.THROWN, t));

            Map<String, String> contextData = new HashMap<>();
            Optional.of(event.getContextData())
                    .filter(cd -> ! cd.isEmpty())
                    .ifPresent(cd -> contextData.putAll(cd.toMap()));
            Optional.of(event.getContextStack()).filter(cs -> cs.getDepth() > 0).ifPresent(cs -> eventMap.put(FieldsName.CONTEXTSTACK, cs.asList()));
            if (locationInfo) {
                Optional.ofNullable(event.getSource()).ifPresent(ste -> {
                    Map<String, Object> li = new HashMap<>(4);
                    li.put(FieldsName.LOCATIONINFO_CLASSNAME, ste.getClassName());
                    li.put(FieldsName.LOCATIONINFO_FILENAME, ste.getFileName());
                    li.put(FieldsName.LOCATIONINFO_METHODNAME, ste.getMethodName());
                    li.put(FieldsName.LOCATIONINFO_LINENUMBER, ste.getLineNumber());
                    eventMap.put(FieldsName.LOCATIONINFO, li);
                });
            }
            if (additionalFields.length > 0) {
                StrSubstitutor strSubstitutor = configuration.getStrSubstitutor();
                Arrays.stream(additionalFields).filter(pair -> ! contextData.containsKey(pair.name)).forEach(pair -> {
                    if (pair.valueNeedsLookup) {
                        // Resolve value
                        contextData.put(pair.name, strSubstitutor.replace(event, pair.value));
                    } else {
                        // Plain text value
                        contextData.put(pair.name, pair.value);
                    }
                });
            }
            if (! contextData.isEmpty()) {
                eventMap.put(FieldsName.CONTEXTDATA, contextData);
            }
            return eventMap.getBytes();
        } catch (IOException e) {
            throw new AppenderLoggingException("Can't serialize an event:  " + e.getMessage(), e);
        }
    }

    @Override
    public Message toSerializable(LogEvent event) {
        throw new UnsupportedOperationException("Can't return a message");
    }

    @Override
    public String getContentType() {
        return "application/octet-stream";
    }

}
