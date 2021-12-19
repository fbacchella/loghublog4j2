package fr.loghub.log4j1;

import fr.loghub.log4j1.Serializer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.Map;

import org.apache.log4j.spi.LoggingEvent;

public class JavaSerializer implements Serializer {

        @Override
        public byte[] objectToBytes(LoggingEvent event) throws IOException {
                // Done in org.apache.log4j.net.SocketAppender
                // Might be cargo cult
                event.getNDC();
                event.getThreadName();
                event.getMDCCopy();
                event.getRenderedMessage();
                event.getThrowableStrRep();
                event.getLocationInformation();

                try (ByteArrayOutputStream buffer = new ByteArrayOutputStream() ; ObjectOutputStream oos = new ObjectOutputStream(buffer)){
                        oos.writeObject(event);
                        oos.flush();
                        return buffer.toByteArray();
                }
        }

}
