package fr.loghub.log4j1;

import java.io.IOException;
import java.io.StringReader;
import java.util.Map;

import org.apache.log4j.spi.LoggingEvent;

public interface Serializer {

    public byte[] objectToBytes(LoggingEvent event) throws IOException;

}
