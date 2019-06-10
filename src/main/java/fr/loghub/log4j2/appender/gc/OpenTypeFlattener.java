package fr.loghub.log4j2.appender.gc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.TabularDataSupport;
import javax.management.openmbean.TabularType;

class OpenTypeFlattener {
    
    private OpenTypeFlattener() {
    }

    public static Map<String, Object> makeMap(CompositeDataSupport data) {
        CompositeType ct = data.getCompositeType();
        Map<String, Object> content = new HashMap<>(ct.keySet().size());
        for (String k: ct.keySet()) {
            Object v = data.get(k);
            OpenType<?> t = ct.getType(k);
            content.put(k, resolve(t, v));
        }
        return content;
    }

    public static List<Map<String, Object>> makeList(TabularDataSupport data) {
        List<Map<String, Object>> content = new ArrayList<>(data.size());
        TabularType tt = data.getTabularType();
        CompositeType rowType = tt.getRowType();
        Set<String> attributes = rowType.keySet();
        for (Object i: data.values()) {
            CompositeDataSupport ct = (CompositeDataSupport) i;
            Map<String, Object> row = new HashMap<>(attributes.size());
            for (String attr: attributes) {
                row.put(attr, resolve(rowType.getType(attr), ct.get(attr)));
            }
            content.add(row);
        }
        return content;
    }

    private static Object resolve(OpenType<?> t, Object v) {
        if (t instanceof CompositeType) {
            return makeMap((CompositeDataSupport) v);
        } else if (t instanceof TabularType) {
            return makeList((TabularDataSupport) v);
        } else if (t instanceof SimpleType) {
            return v;
        } else {
            return v;
        }
    }

}
