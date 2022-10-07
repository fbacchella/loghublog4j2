package fr.loghub.log4j1.serializer;

import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.spi.ErrorCode;
import org.apache.log4j.spi.LoggingEvent;

public abstract class SerializerAppender extends AppenderSkeleton {

    private String serializerName = JavaSerializer.class.getName();
    private Serializer serializer;

    @Getter @Setter
    private String hostname =  null;
    @Getter @Setter
    private String application;
    @Getter @Setter
    private boolean locationInfo = false;

    @Override
    public final void activateOptions() {
        try {
            @SuppressWarnings("unchecked")
            Class<? extends Serializer> c = (Class<? extends Serializer>) getClass().getClassLoader().loadClass(serializerName);
            serializer = c.getConstructor().newInstance();
        } catch (ClassCastException | ClassNotFoundException | IllegalArgumentException | SecurityException | InstantiationException | IllegalAccessException
                | NoSuchMethodException e) {
            errorHandler.error("failed to create serializer", e, ErrorCode.GENERIC_FAILURE);
            return;
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception) {
                errorHandler.error("failed to create serializer", (Exception) e.getCause(), ErrorCode.GENERIC_FAILURE);
            } else {
                errorHandler.error("failed to create serializer", e, ErrorCode.GENERIC_FAILURE);
            }
            return;
        }
        if (hostname == null) {
            try {
                hostname = InetAddress.getLocalHost().getHostName();
            } catch (UnknownHostException e) {
                errorHandler.error(e.getMessage(), e, ErrorCode.GENERIC_FAILURE);
                return;
            }
        }
        subOptions();
    }

    protected abstract void subOptions();

    @Override
    protected final void append(LoggingEvent event) {
        @SuppressWarnings("unchecked")
        Map<String, String> eventProps = event.getProperties();

        // The event is copied, because a host field is added in the properties
        LoggingEvent modifiedEvent = new LoggingEvent(event.getFQNOfLoggerClass(), event.getLogger(), event.getTimeStamp(), event.getLevel(), event.getMessage(),
                event.getThreadName(), event.getThrowableInformation(), event.getNDC(),
                locationInfo ? event.getLocationInformation() : null, new HashMap<String,String>(eventProps));

        if (application != null) {
            modifiedEvent.setProperty("application", application);
        }
        modifiedEvent.setProperty("hostname", hostname);
        try {
            send(serializer.objectToBytes(modifiedEvent));
        } catch (IOException e) {
            errorHandler.error("failed to serialize event", e, ErrorCode.GENERIC_FAILURE);
        }
    }

    protected abstract void send(byte[] content);

    @Override
    public boolean requiresLayout() {
        return false;
    }

    public String getSerializer() {
        return serializerName;
    }

    /**
     * The class name of a implementation of a {@link Serializer} interface.
     * @param serializer
     */
    public void setSerializer(String serializer) {
        this.serializerName = serializer;
    }

}
