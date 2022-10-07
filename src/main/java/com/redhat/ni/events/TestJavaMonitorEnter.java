package com.redhat.ni.events;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedClass;
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
    private static final int MILLIS = 60;

    static boolean inCritical = false;
    static Thread firstThread;
    static Thread secondThread;
    static final Helper helper = new Helper();
    @Override
    public void test() throws Exception {
        Runnable first = () -> {
            try {
                helper.doWork();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        };

        Runnable second = () -> {
            try {
                // wait until lock is held
                while (!inCritical) {
                    Thread.sleep(10);
                }
                helper.doWork();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        };
        firstThread = new Thread(first);
        secondThread = new Thread(second);


        Recording recording = new Recording();
        recording.enable("jdk.JavaMonitorEnter").withThreshold(Duration.ofMillis(1));
        try {
            recording.start();
            firstThread.start();
            secondThread.start();

            firstThread.join();
            secondThread.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            recording.stop();
        }

        List<RecordedEvent> events = getEvents(recording, getName());

        boolean found = false;
        for (RecordedEvent event : events) {
            RecordedObject struct = event;
            String eventThread = struct.<RecordedThread> getValue("eventThread").getJavaName();
            if (struct.<RecordedClass> getValue("monitorClass").getName().equals(Helper.class.getName()) && event.getDuration().toMillis() >= MILLIS && secondThread.getName().equals(eventThread)) {

                // verify previous owner
                if(!struct.<RecordedThread> getValue("previousOwner").getJavaName().equals(firstThread.getName())) {
                    throw new Exception("prev owner wrong");
                }
                found = true;
                break;
            }
        }
        if(!found){
            throw new Exception("Expected monitor blocked event not found");
        }
    }

    static class Helper {
        private synchronized void doWork() throws InterruptedException {
            inCritical = true;
            if (Thread.currentThread().equals(secondThread)) {
                inCritical = false;
                return; // second thread doesn't need to do work.
            }

            // spin until second thread blocks
            while (!secondThread.getState().equals(Thread.State.BLOCKED)) {
                Thread.sleep(10);
            }

            Thread.sleep(MILLIS);
            inCritical = false;
        }
    }
    @Override
    public String getName() {
        return "jdk.JavaMonitorEnter";
    }
}
