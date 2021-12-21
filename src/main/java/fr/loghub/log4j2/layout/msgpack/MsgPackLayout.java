package fr.loghub.log4j2.layout.msgpack;

import fr.loghub.logservices.FieldsName;
import fr.loghub.logservices.msgpack.MsgPacker;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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

    private Map<String, Object> resolveMark(Marker m) {
        Map<String, Object> markerMap = new HashMap<>(2);
        markerMap.put("name", m.getName());
        List<Map<String, Object>> parents = Arrays.stream(Optional.ofNullable(m.getParents()).orElse(new Marker[] {}))
                                                  .map(this::resolveMark)
                                                  .collect(Collectors.toList());
        if (! parents.isEmpty()) {
            markerMap.put("parents", parents);
        }
        return markerMap;
    }

    @Override
    public byte[] toByteArray(LogEvent event) {
        try (MsgPacker eventMap = new MsgPacker(11)){
            eventMap.put(FieldsName.INSTANT, Instant.ofEpochSecond(event.getInstant().getEpochSecond(), event.getInstant().getNanoOfSecond()));
            eventMap.put(FieldsName.THREADNAME, event.getThreadName());
            eventMap.put(FieldsName.THREADPRIORITY, event.getThreadPriority());
            eventMap.put(FieldsName.THREADID, event.getThreadId());
            eventMap.put(FieldsName.LEVEL, event.getLevel().name());
            eventMap.put(FieldsName.LOGGER, event.getLoggerName());
            eventMap.put(FieldsName.LOGGER_FQCN, event.getLoggerFqcn());
            eventMap.put(FieldsName.ENDOFBATCH, event.isEndOfBatch());
            Optional.ofNullable(event.getMarker())
                    .map(this::resolveMark)
                    .ifPresent(m -> eventMap.put(FieldsName.MARKERS, m));
            Message msg = event.getMessage();
            if (msg instanceof MapMessage) {
                @SuppressWarnings({ "rawtypes", "unchecked" })
                Map<String, ?> map = ((MapMessage)msg).getData();
                eventMap.put("values", map);
            } else if (msg instanceof ObjectArrayMessage) {
                Object[] params = ((ObjectArrayMessage)msg).getParameters();
                eventMap.put("content", Arrays.asList(params));
            } else {
                eventMap.put(FieldsName.MESSAGE, event.getMessage().getFormattedMessage());
            }
            Optional.ofNullable(event.getThrown()).ifPresent(t -> eventMap.put(FieldsName.EXCEPTION, t));
            // Ignoring end of batch eventMap.put("endOfBatch", event.isEndOfBatch());
            // Ignoring fqcn of logger eventMap.put("loggerFqcn", event.getLoggerFqcn());

            Map<String, String> ctxtProps = new HashMap<>();
            Optional.of(event.getContextData())
                    .filter(cd -> ! cd.isEmpty())
                    .ifPresent(cd -> ctxtProps.putAll(cd.toMap()));
            Optional.of(event.getContextStack()).filter(cs -> cs.getDepth() > 0).ifPresent(cs -> eventMap.put(FieldsName.CONTEXTSTACK, cs.asList()));
            if (locationInfo) {
                Optional.ofNullable(event.getSource()).ifPresent(ste -> {
                    Map<String, Object> locationInfo = new HashMap<>(4);
                    locationInfo.put(FieldsName.LOCATIONINFO_CLASS, ste.getClassName());
                    locationInfo.put(FieldsName.LOCATIONINFO_FILE, ste.getFileName());
                    locationInfo.put(FieldsName.LOCATIONINFO_METHOD, ste.getMethodName());
                    locationInfo.put(FieldsName.LOCATIONINFO_LINE, ste.getLineNumber());
                    eventMap.put(FieldsName.LOCATIONINFO, locationInfo);
                });
            }
            if (additionalFields.length > 0) {
                StrSubstitutor strSubstitutor = configuration.getStrSubstitutor();
                Arrays.stream(additionalFields).filter(pair -> ! ctxtProps.containsKey(pair.name)).forEach(pair -> {
                    if (pair.valueNeedsLookup) {
                        // Resolve value
                        ctxtProps.put(pair.name, strSubstitutor.replace(event, pair.value));
                    } else {
                        // Plain text value
                        ctxtProps.put(pair.name, pair.value);
                    }
                });
            }
            if (! ctxtProps.isEmpty()) {
                eventMap.put(FieldsName.CONTEXTPROPERTIES, ctxtProps);
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
