package com.redhat.ni.events;

import com.redhat.ni.tester.Tester;
import jdk.jfr.Recording;
import jdk.jfr.consumer.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

import static com.redhat.ni.tester.Tester.makeCopy;

public class TestJavaMonitorWait implements com.redhat.ni.tester.Test{
    private static final int MILLIS = 50;
    private static final int COUNT = 10;
    static Helper helper = new Helper();

    @Override
    public String getName() {
        return "jdk.JavaMonitorWait";
    }
    @Override
    public void test() throws Exception {

        Recording recording = new Recording();
        recording.enable("jdk.JavaMonitorWait").withThreshold(Duration.ofMillis(1));
        try {
            recording.start();

            Runnable consumer = () -> {
                try {
                    helper.consume();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            };

            Runnable producer = () -> {
                try {
                    helper.produce();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            };
            Thread tc = new Thread(consumer);
            Thread tp = new Thread(producer);
            tp.start();
            tc.start();
            tp.join();
            tc.join();

            // sleep so we know the event is recorded
            Thread.sleep(500);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            recording.stop();
        }

        Path p = makeCopy(recording);
        List<RecordedEvent> events = RecordingFile.readAllEvents(p);
        Collections.sort(events, new Tester.ChronologicalComparator());
        Files.deleteIfExists(p);
        int prodCount = 0;
        int consCount = 0;
        Long prodTid = null;
        Long consTid = null;
        Long lastTid = null; //should alternate if buffer is 1
        for (RecordedEvent event : events) {
            RecordedObject struct = event;
            if (event.getEventType().getName().equals("jdk.JavaMonitorWait")) {
                if (com.redhat.ni.tester.Tester.isEqualDuration(Duration.ofMillis(MILLIS), event.getDuration())) {
                    //check which thread emitted the event
                    Long eventThread = struct.<RecordedThread>getValue("eventThread").getId();
                    Long notifThread = struct.<RecordedThread>getValue("notifier").getId();
                    if (!struct.<RecordedClass>getValue("monitorClass").getName().equals("com.redhat.ni.events.TestJavaMonitorWait$Helper") &&
                            (eventThread.equals(consTid) ||eventThread.equals(prodTid))) {
                        throw new Exception("Wrong monitor class.");
                    }
                    if (struct.<Boolean>getValue("timedOut")) {
                        throw new Exception("Should not have timed out.");
                    }

                    if (prodTid == null) {
                        prodTid = eventThread;
                        consTid = notifThread;
                        lastTid = notifThread;
                    }
                    if (!lastTid.equals(notifThread)) {
                        throw new Exception("Not alternating");
                    }
                    if (eventThread.equals(prodTid)) {
                        prodCount++;
                        if (!notifThread.equals(consTid)) {
                            throw new Exception("Wrong notifier");
                        }

                    } else if (eventThread.equals(consTid)) {
                        consCount++;
                        if (!notifThread.equals(prodTid)) {
                            throw new Exception("Wrong notifier");
                        }
                    }
                    lastTid = eventThread;
                }

            }
        }
        if (prodCount !=(consCount) || consCount !=COUNT-1) {
            throw new Exception("Wrong number of events");
        }

    }
    static class Helper {
        private int count = 0;
        private final int bufferSize = 1;

        public synchronized void produce() throws InterruptedException {
            for (int i = 0; i< COUNT; i++) {
                while (count >= bufferSize) {
                    wait();
                }
                Thread.sleep(MILLIS);
                count++;
                notify();
            }
        }

        public synchronized void consume() throws InterruptedException {
            for (int i = 0; i< COUNT; i++) {
                while (count == 0) {
                    wait();
                }
                Thread.sleep(MILLIS);
                count--;
                notify();
            }
        }
    }
}
