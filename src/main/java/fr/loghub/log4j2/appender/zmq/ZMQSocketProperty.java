package fr.loghub.log4j2.appender.zmq;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.config.Node;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;

import lombok.Getter;

@Getter
@Plugin(name = "ZMQSocketProperty", category = Node.CATEGORY)
public class ZMQSocketProperty {

    private final String name;
    private final String value;

    private ZMQSocketProperty(String name, String value) {
        this.name  = name;
        this.value = value;
    }

    @PluginFactory
    public static ZMQSocketProperty createEntry(
            @PluginAttribute("name")  String name,
            @PluginAttribute("value") String value
    ) {
        if (name == null || name.isEmpty()) {
            LOGGER.error("<ZMQSocketProperty> element is missing its 'name' attribute.");
            return null;
        }
        return new ZMQSocketProperty(name, value != null ? value : "");
    }

    private static final org.apache.logging.log4j.Logger LOGGER = LogManager.getLogger();
}
