package fr.loghub.log4j1.msgpack;

import org.apache.log4j.Layout;
import org.apache.log4j.spi.LoggingEvent;

public class RenameLayout extends Layout {
    @Override
    public String format(LoggingEvent event) {
        return null;
    }

    @Override
    public boolean ignoresThrowable() {
        return false;
    }

    @Override
    public void activateOptions() {

    }

    public void setMapping(Object o) {
        System.out.println(o);
    }
}
