package fr.loghub.logservices.zmq;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class AsynchronousPublisher extends Thread {

    private final BlockingQueue<byte[]> logQueue;
    private volatile boolean running;
    private final Publisher publisher;
    private final Logger logger;

    public AsynchronousPublisher(String name, Logger logger, ZMQConfiguration<?> config) {
        publisher = new Publisher(logger, config);
        logQueue = new ArrayBlockingQueue<>(config.getHwm());
        this.logger = logger;
        setName(name);
        setDaemon(true);
        setUncaughtExceptionHandler(this::exceptionHandler);
        running = false;
        start();
    }

    @Override
    public void run() {
        try {
            while (!running) {
                publisher.refreshSocket();
                 // Not a blocking wait, it allows to test if closed every 100 ms
                // Needed because interrupt deactivated for this thread
                byte[] log = logQueue.poll(100, TimeUnit.MILLISECONDS);
                publisher.sendData(log);
            }
        } catch (InterruptedException e) {
            // End of processing
            Thread.currentThread().interrupt();
        }
        publisher.close();
    }

    /* Don't interrupt a ZMQ thread, just finished it */
    @Override
    public synchronized void interrupt() {
        running = true;
    }

    private void exceptionHandler(Thread t, Throwable ex) {
        logger.error(() -> "Critical exception: " + ex.getMessage(), ex);
        publisher.close();
    }

    public boolean send(byte[] content) {
        return logQueue.offer(content);
    }


    public void close() {
        publisher.close();
    }

}
