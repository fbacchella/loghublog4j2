package fr.loghub.log4j2.layout.msgpack;

import java.io.IOException;
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

    private Map<String, Object> resolveThrowable(Throwable t) {
        Map<String, Object> exception = new HashMap<>(4);
        Optional.ofNullable(t.getMessage()).ifPresent(m -> exception.put("message", m));
        exception.put("name", t.getClass().getName());
        List<String> stack = Arrays.stream(t.getStackTrace()).map(StackTraceElement::toString).map(i -> i.replace("\t", "")).collect(Collectors.toList());
        exception.put("extendedStackTrace", stack);
        Optional.ofNullable(t.getCause())
        .map(this::resolveThrowable)
        .ifPresent(i -> exception.put("cause", i));
        return exception;
    }

    private Map<String, Object> resolveMark(Marker m) {
        Map<String, Object> markerMap = new HashMap<>(2);
        markerMap.put("name", m.getName());
        List<Map<String, Object>> parents = Arrays.stream(Optional.ofNullable(m.getParents()).orElse(new Marker[] {})).map(this::resolveMark).collect(Collectors.toList());
        if (! parents.isEmpty()) {
            markerMap.put("parents", parents);
        }
        return markerMap;
    }

    @Override
    public byte[] toByteArray(LogEvent event) {
        try (MsgPacker eventMap = new MsgPacker(11)){
            eventMap.put("instant", event.getInstant());
            eventMap.put("thread", event.getThreadName());
            eventMap.put("level", event.getLevel().name());
            eventMap.put("loggerName", event.getLoggerName());
            Optional.ofNullable(event.getMarker()).ifPresent(m -> eventMap.put("marker", resolveMark(m)));
            Message msg = event.getMessage();
            if (msg instanceof MapMessage) {
                @SuppressWarnings({ "rawtypes", "unchecked" })
                Map<String, ?> map = ((MapMessage)msg).getData();
                eventMap.put("values", map);
            } else if (msg instanceof ObjectArrayMessage) {
                Object[] params = ((ObjectArrayMessage)msg).getParameters();
                eventMap.put("content", Arrays.asList(params));
            } else {
                eventMap.put("message", event.getMessage().getFormattedMessage());
            }
            Optional.ofNullable(event.getThrown()).ifPresent(t -> eventMap.put("thrown", resolveThrowable(t)));
            // Ignoring end of batch eventMap.put("endOfBatch", event.isEndOfBatch());
            // Ignoring fqcn of logger eventMap.put("loggerFqcn", event.getLoggerFqcn());
            Optional.of(event.getContextData()).filter(cd -> cd.size() > 0).ifPresent(cd -> eventMap.put("contextData", cd));
            Optional.of(event.getContextStack()).filter(cs -> cs.getDepth() > 0).ifPresent(cs -> eventMap.put("contextStack", cs.asList()));
            eventMap.put("threadId", event.getThreadId());
            eventMap.put("threadPriority", event.getThreadPriority());
            if (locationInfo) {
                Optional.ofNullable(event.getSource()).ifPresent(ste -> {
                    Map<String, Object> locationinfo = new HashMap<>(4);
                    locationinfo.put("class", ste.getClassName());
                    locationinfo.put("file", ste.getFileName());
                    locationinfo.put("method", ste.getMethodName());
                    locationinfo.put("line", ste.getLineNumber());
                    eventMap.put("source", locationinfo);
                });
            }
            if (additionalFields.length > 0) {
                StrSubstitutor strSubstitutor = configuration.getStrSubstitutor();
                Arrays.stream(additionalFields).filter(pair -> ! eventMap.containsKey(pair.name)).forEach(pair -> {
                    if (pair.valueNeedsLookup) {
                        // Resolve value
                        eventMap.put(pair.name, strSubstitutor.replace(event, pair.value));
                    } else {
                        // Plain text value
                        eventMap.put(pair.name, pair.value);
                    }
                });
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
