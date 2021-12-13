package fr.loghub.log4j2.layout.msgpack;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.logging.log4j.core.time.Instant;
import org.apache.logging.log4j.util.ReadOnlyStringMap;
import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessagePack;
import org.msgpack.value.Value;
import org.msgpack.value.ValueFactory;
import org.msgpack.value.ValueFactory.MapBuilder;

class MsgPacker extends HashMap<Value, Value> implements AutoCloseable {

    private static final ThreadLocal<MessageBufferPacker> packer = ThreadLocal.withInitial(MessagePack::newDefaultBufferPacker);

    private final Set<String> keys;

    MsgPacker(int size) {
        super(size);
        keys = new HashSet<>(size);
    }
    
    public boolean containsKey(String key) {
        return keys.contains(key);
    }

    public void put(String k, Number v) {
        store(k, v, i -> ValueFactory.newInteger(i.longValue()));
    }
    public void put(String k, String v) {
        store(k, v, ValueFactory::newString);
    }
    public void put(String k, List<? extends Object> v) {
        store(k, v, this::map);
    }
    public void put(String k, Instant v) {
        store(k, v, this::map);
    }
    public void put(String k, Map<String, ? extends Object> v) {
        store(k, v, this::map);
    }
    public void put(String k, ReadOnlyStringMap v) {
        store(k, v, this::map);
    }
    private Value map(Object m) {
        if (m == null) {
            return ValueFactory.newNil();
        } else if (m instanceof List) {
            List<Value> elements = ((List<?>)m).stream().map(this::map).collect(Collectors.toList());
            return ValueFactory.newArray(elements);
        } else if (m instanceof Number) {
            return ValueFactory.newInteger(((Number)m).longValue());
        } else if (m instanceof Map) {
            MapBuilder builder = ValueFactory.newMapBuilder();
            ((Map<?, ?>)m).forEach((k,v) -> builder.put(map(k), map(v)));
            return builder.build();
        } else if (m instanceof Instant) {
            byte[] bytes = getInstantBytes((Instant) m);
            return ValueFactory.newExtension((byte)-1, bytes);
        } else if (m instanceof ReadOnlyStringMap) {
            MapBuilder builder = ValueFactory.newMapBuilder();
            ((ReadOnlyStringMap)m).forEach((k,v) -> builder.put(map(k), map(v)));
            return builder.build();
        } else {
            return ValueFactory.newString(m.toString());
        }
    }

    private <V> void store(String k, V v, Function<V, Value> mapper) {
        Objects.requireNonNull(k);
        put(ValueFactory.newString(k), Optional.ofNullable(v).map(mapper::apply).orElse(ValueFactory.newNil()));
    }

    private byte[] getInstantBytes(Instant timestamp) {
        ByteBuffer longBuffer;
        long seconds = timestamp.getEpochSecond();
        int nanoseconds = timestamp.getNanoOfSecond();
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
    public void close() throws IOException {
        packer.get().clear();
    }

}
