package com.redhat.ni.events;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedObject;
import jdk.jfr.consumer.RecordedThread;
import main.java.com.redhat.ni.tester.Stressor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.locks.LockSupport;
import com.redhat.ni.tester.Test;
import static java.lang.Math.abs;

public class TestJavaMonitorEnter extends Test {
    private static final int THREADS = 10;
    private static final int MILLIS = 60;

    static Object monitor = new Object();
    private static Queue<String> orderedWaiterNames = new LinkedList<>();
    @Override
    public void test() throws Exception {
        int threadCount = THREADS;
        Runnable r = () -> {
            // create contention between threads for one lock
            try {
                doWork(monitor);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

        };
        Thread.UncaughtExceptionHandler eh = (t, e) -> e.printStackTrace();
        Recording recording = new Recording();
        recording.enable("jdk.JavaMonitorEnter").withThreshold(Duration.ofMillis(1));
        try {
            recording.start();
            LockSupport.parkNanos(1000 * 1000000);
            Stressor.execute(threadCount, eh, r);
            // sleep so we know the event is recorded
            Thread.sleep(500);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            recording.stop();
        }

        List<RecordedEvent> events = getEvents(recording, getName());
        int count = 0;

        orderedWaiterNames.poll(); //first worker does not wait
        String waiterName = orderedWaiterNames.poll();
        Long prev = 0L;
        for (RecordedEvent event : events) {
            RecordedObject struct = event;
            String eventThread = struct.<RecordedThread>getValue("eventThread").getJavaName();
            if (event.getEventType().getName().equals("jdk.JavaMonitorEnter")
                    && isGreaterDuration(Duration.ofMillis(MILLIS), event.getDuration())
                    && waiterName.equals(eventThread)) {
                Long duration = event.getDuration().toMillis();
                if ( abs(duration - prev - MILLIS) > MS_TOLERANCE ) {
                    throw new Exception( "Durations not as expected ");
                }
                count++;
                waiterName = orderedWaiterNames.poll();
                prev = duration;
            }
        }
        if (count != THREADS - 1){ // -1 because first thread does not get blocked by any previous thread
            throw new Exception("Wrong number of Java Monitor Enter Events " + count);
        }
    }

    private static void doWork(Object obj) throws InterruptedException {
        synchronized(obj){
            Thread.sleep(MILLIS);
            orderedWaiterNames.add(Thread.currentThread().getName());
        }

    }
    @Override
    public String getName() {
        return "jdk.JavaMonitorEnter";
    }
}
