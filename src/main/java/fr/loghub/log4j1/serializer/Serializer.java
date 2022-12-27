package fr.loghub.log4j1.serializer;

import java.io.IOException;

import org.apache.log4j.spi.LoggingEvent;

public interface Serializer {

    byte[] objectToBytes(LoggingEvent event) throws IOException;

}
