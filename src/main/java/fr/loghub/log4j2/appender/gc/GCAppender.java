package fr.loghub.log4j2.appender.gc;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.management.InstanceNotFoundException;
import javax.management.ListenerNotFoundException;
import javax.management.MBeanServer;
import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeDataSupport;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.AbstractLifeCycle;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.ConfigurationException;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderFactory;
import org.apache.logging.log4j.message.MapMessage;

@Plugin(
        name = "GCAppender", 
        category = Core.CATEGORY_NAME, 
        elementType = Appender.ELEMENT_TYPE)
public class GCAppender extends AbstractAppender {

    public static class Builder extends AbstractAppender.Builder<Builder>
    implements org.apache.logging.log4j.core.util.Builder<GCAppender> {

        @PluginBuilderAttribute("level")
        String level = Level.DEBUG.name();

        @PluginBuilderAttribute("parent")
        String parent = "gc";

        @Override
        public GCAppender build() {
            return new GCAppender(this);
        }
    }

    @PluginBuilderFactory
    public static Builder newBuilder() {
        return new Builder();
    }

    private final Level level;
    private final String parent;
    
    private final List<Map.Entry<ObjectName, NotificationListener>> listeners;

    protected GCAppender(Builder builder) {
        super(builder.getName(), builder.getFilter(), builder.getLayout(), builder.isIgnoreExceptions(), builder.getPropertyArray());
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();

        List<GarbageCollectorMXBean> gcs = ManagementFactory.getGarbageCollectorMXBeans();
        listeners = new ArrayList<>(gcs.size());
        gcs.stream().map(GarbageCollectorMXBean::getObjectName).forEach(on -> {
            try {
                NotificationListener nl = this::getEvent;
                listeners.add(new AbstractMap.SimpleImmutableEntry<>(on, nl));
                server.addNotificationListener(on, nl, null, null);
            } catch (InstanceNotFoundException ex) {
                throw new ConfigurationException(ex);
            }
        });
        level = Level.getLevel(builder.level.toUpperCase(Locale.ENGLISH));
        parent = builder.parent;
    }

    @Override
    protected void setStopping() {
        super.setStopping();
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        listeners.forEach(e -> {
            try {
                server.removeNotificationListener(e.getKey(), e.getValue());
            } catch (InstanceNotFoundException | ListenerNotFoundException ex) {
                AbstractLifeCycle.LOGGER.error("Can't remove listener \"{}\": {}", e.getKey(), ex.getMessage());
                AbstractLifeCycle.LOGGER.catching(Level.DEBUG, ex);
            }
        });
    }

    private void getEvent(Notification notification, Object handback) {
        CompositeDataSupport cds = (CompositeDataSupport) notification.getUserData();
        String gcName = cds.get("gcName").toString();
        Logger l = LogManager.getLogger(parent + "." + gcName);
        l.log(level, () -> {
            Map<String, Object> details = OpenTypeFlattener.makeMap(cds);
            return new MapMessage<>(details);
        });
    }

    @Override
    public boolean isFiltered(LogEvent event) {
        return false;
    }

    @Override
    public void append(LogEvent event) {
        // Not logging
    }

}
