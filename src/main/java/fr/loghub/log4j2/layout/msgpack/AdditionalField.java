package fr.loghub.log4j2.layout.msgpack;

import org.apache.logging.log4j.core.config.ConfigurationException;
import org.apache.logging.log4j.core.config.Node;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderFactory;
import org.apache.logging.log4j.core.config.plugins.PluginValue;

import lombok.ToString;

@ToString
@Plugin(name = "AdditionalField", category = Node.CATEGORY, printObject = true)
public class AdditionalField {

    final String name;
    final String value;
    final boolean valueNeedsLookup;

    AdditionalField(Builder builder) {
        if (builder.name == null) {
            throw new ConfigurationException("name is required in an AdditionalField");
        }
        this.name = builder.name;
        this.value = builder.value;
        this.valueNeedsLookup = value != null && value.contains("${");
    }

    @PluginBuilderFactory
    public static Builder newBuilder() {
        return new Builder();
    }

    public static class Builder implements org.apache.logging.log4j.core.util.Builder<AdditionalField> {

        @PluginBuilderAttribute
        private String name;

        @PluginValue("value")
        private String value;

        @Override
        public AdditionalField build() {
            return new AdditionalField(this);
        }
    }

}
