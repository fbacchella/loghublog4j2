package fr.loghub.logback;

import java.util.ArrayList;
import java.util.List;

import ch.qos.logback.core.spi.LifeCycle;
import ch.qos.logback.core.status.Status;

public class StatusListener implements ch.qos.logback.core.status.StatusListener, LifeCycle {

    public static final List<Status> statuses = new ArrayList<>();

    @Override
    public void start() {
        statuses.clear();
    }

    @Override
    public void stop() {
        statuses.clear();
    }

    @Override
    public boolean isStarted() {
        return true;
    }

    @Override
    public void addStatusEvent(Status status) {
        if (status.getLevel() >= Status.WARN) {
            System.err.println(status);
            statuses.add(status);
        }
    }
}
