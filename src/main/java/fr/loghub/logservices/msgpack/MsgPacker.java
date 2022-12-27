package fr.loghub.logservices.msgpack;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessagePack;
import org.msgpack.value.Value;
import org.msgpack.value.ValueFactory;
import org.msgpack.value.ValueFactory.MapBuilder;

public class MsgPacker extends HashMap<Value, Value> implements AutoCloseable {

    private static final ThreadLocal<MessageBufferPacker> packer = ThreadLocal.withInitial(MessagePack::newDefaultBufferPacker);

    private final Set<String> keys;

    public MsgPacker(int size) {
        super(size);
        keys = new HashSet<>(size);
    }
    
    public boolean containsKey(String key) {
        return keys.contains(key);
    }

    public void put(String k, String v) {
        store(k, v, ValueFactory::newString);
    }
    public void put(String k, List<?> v) {
        store(k, v, this::map);
    }
    public void put(String k, Instant v) {
        store(k, v, this::map);
    }
    public void put(String k, Date v) {
        store(k, v, this::map);
    }
    public void put(String k, Map<String, ?> v) {
        store(k, v, this::map);
    }
    public void put(String k, Throwable v) {
        store(k, v, this::map);
    }
    public void put(String k, boolean v) {
        store(k, v, m -> ValueFactory.newBoolean(v));
    }
    public void put(String k, int v) {
        store(k, v, m -> ValueFactory.newInteger(v));
    }
    public void put(String k, long v) {
        store(k, v, m -> ValueFactory.newInteger(v));
    }
    public void put(String k, Object[] v) {
        store(k, v, this::map);
    }

    private Value map(Object m) {
        if (m == null) {
            return ValueFactory.newNil();
        } else if (m instanceof List) {
            Value[] elements = ((List<?>)m).stream().map(this::map).toArray(Value[]::new);
            return ValueFactory.newArray(elements);
        } else if (m instanceof Throwable) {
            MapBuilder builder = ValueFactory.newMapBuilder();
            resolveThrowable((Throwable) m).forEach((k,v) -> builder.put(map(k), map(v)));
            return builder.build();
        } else if (m instanceof Number) {
            return ValueFactory.newInteger(((Number)m).longValue());
        } else if (m instanceof Boolean) {
            return ValueFactory.newBoolean((Boolean) m);
        } else if (m instanceof Map) {
            MapBuilder builder = ValueFactory.newMapBuilder();
            ((Map<?, ?>)m).forEach((k,v) -> builder.put(map(k), map(v)));
            return builder.build();
        } else if (m instanceof Instant) {
            byte[] bytes = getInstantBytes((Instant) m);
            return ValueFactory.newExtension((byte)-1, bytes);
        } else if (m instanceof Date) {
            byte[] bytes = getInstantBytes(((Date) m).toInstant());
            return ValueFactory.newExtension((byte)-1, bytes);
        } else if (m instanceof int[]) {
           return ValueFactory.newArray(Arrays.stream((int[])m).mapToObj(ValueFactory::newInteger).toArray(Value[]::new));
        } else if (m instanceof long[]) {
            return ValueFactory.newArray(Arrays.stream((long[])m).mapToObj(ValueFactory::newInteger).toArray(Value[]::new));
         } else if (m instanceof double[]) {
            return ValueFactory.newArray(Arrays.stream((double[])m).mapToObj(ValueFactory::newFloat).toArray(Value[]::new));
        } else if (m.getClass().isArray() && Object.class.isAssignableFrom(m.getClass().getComponentType())) {
            return ValueFactory.newArray(Arrays.stream((Object[])m).map(this::map).toArray(Value[]::new));
        } else if (m instanceof String) {
            return ValueFactory.newString((String)m);
        } else {
            assert false : "Unhandled type " +  m.getClass();
            return ValueFactory.newString(m.toString());
        }
    }

    private <V> void store(String k, V v, Function<V, Value> mapper) {
        Objects.requireNonNull(k);
        put(ValueFactory.newString(k), Optional.ofNullable(v).map(mapper).orElse(ValueFactory.newNil()));
    }

    private byte[] getInstantBytes(Instant timestamp) {
        ByteBuffer longBuffer;
        long seconds = timestamp.getEpochSecond();
        int nanoseconds = timestamp.getNano();
        long result = ((long)nanoseconds << 34) | seconds;
        if ((result >> 34) == 0) {
            if ((result & 0xffffffff00000000L) == 0 ) {
                longBuffer = ByteBuffer.wrap(new byte[4]);
                longBuffer.putInt((int) result);
            } else {
                longBuffer = ByteBuffer.wrap(new byte[8]);
                longBuffer.putLong(result);
            }
        } else {
            longBuffer = ByteBuffer.wrap(new byte[12]);
            longBuffer.putInt(nanoseconds);
            longBuffer.putLong(seconds);
        }
        return longBuffer.array();
    }

    private Map<String, Object> resolveThrowable(Throwable t) {
        Map<String, Object> exception = new HashMap<>(4);
        Optional.ofNullable(t.getMessage()).ifPresent(m -> exception.put("message", m));
        exception.put("class", t.getClass().getName());
        List<String> stack = Arrays.stream(t.getStackTrace()).map(StackTraceElement::toString).map(i -> i.replace("\t", "")).collect(Collectors.toList());
        exception.put("stack", stack);
        Optional.ofNullable(t.getCause())
                .map(this::resolveThrowable)
                .ifPresent(i -> exception.put("cause", i));
        return exception;
    }

    public byte[] getBytes() throws IOException {
        try {
            Value v = ValueFactory.newMap(this);
            packer.get().packValue(v);
            return packer.get().toByteArray();
        } finally {
            packer.get().clear();
        }
    }

    @Override
    public void clear() {
        keys.clear();
        super.clear();
    }

    @Override
    public void close() {
        packer.get().clear();
    }

}
