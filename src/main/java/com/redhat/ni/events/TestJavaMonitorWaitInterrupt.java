/*
 * Copyright (c) 2022, Red Hat, Inc.
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This file is part of the Red Hat GraalVM Testing Suite (the suite).
 *
 * The suite is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * The suite is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the suite.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.redhat.ni.events;

import com.redhat.ni.tester.Tester;
import jdk.jfr.Recording;
import jdk.jfr.consumer.*;

import java.time.Duration;
import java.util.List;
import com.redhat.ni.tester.Test;
import main.java.com.redhat.ni.events.TestJavaMonitorWaitNotifyAll;

public class TestJavaMonitorWaitInterrupt extends Test{
    private static final int MILLIS = 50;
    static final Helper helper = new Helper();
    static Thread interruptedThread;
    static Thread interrupterThread;
    static Thread simpleWaitThread;
    static Thread simpleNotifyThread;

    private boolean interruptedFound = false;
    private boolean simpleWaitFound = false;

    @Override
    public String getName() {
        return "jdk.JavaMonitorWait";
    }
    @Override
    public String getTestName(){
        return getName()+ "_interrupt";
    }
    private static void testInterruption() throws Exception {

        Runnable interrupted = () -> {
            try {
                helper.interrupt(); // must enter first
                throw new RuntimeException("Was not interrupted!!");
            } catch (InterruptedException e) {
                // should get interrupted
            }
        };
        interruptedThread = new Thread(interrupted);

        Runnable interrupter = () -> {
            try {
                helper.interrupt();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        };

        interrupterThread = new Thread(interrupter);
        interruptedThread.start();
        interruptedThread.join();
        interrupterThread.join();
    }

    private static void testWaitNotify() throws Exception {
        Runnable simpleWaiter = () -> {
            try {
                helper.simpleNotify();
            } catch (InterruptedException e) {
                throw new RuntimeException();
            }
        };

        Runnable simpleNotifier = () -> {
            try {
                helper.simpleNotify();
            } catch (Exception e) {
                throw new RuntimeException();
            }
        };

        simpleWaitThread = new Thread(simpleWaiter);
        simpleNotifyThread = new Thread(simpleNotifier);

        simpleWaitThread.start();
        simpleWaitThread.join();
        simpleNotifyThread.join();
    }
    @Override
    public void test() throws Exception {

        Recording recording = new Recording();
        recording.enable("jdk.JavaMonitorWait").withThreshold(Duration.ofMillis(1));
        try {
            recording.start();
            testInterruption();
            testWaitNotify();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            recording.stop();
        }

        List<RecordedEvent> events = getEvents(recording, getName());
        for (RecordedEvent event : events) {
            RecordedObject struct = event;

            String eventThread = struct.<RecordedThread>getValue("eventThread").getJavaName();
            String notifThread = struct.<RecordedThread>getValue("notifier") != null ? struct.<RecordedThread>getValue("notifier").getJavaName() : null;
            if (!eventThread.equals(interrupterThread.getName()) &&
                    !eventThread.equals(interruptedThread.getName()) &&
                    !eventThread.equals(simpleNotifyThread.getName()) &&
                    !eventThread.equals(simpleWaitThread.getName())) {
                continue;
            }
            if (!struct.<RecordedClass>getValue("monitorClass").getName().equals(Helper.class.getName())) {
                continue;
            }
            if (!(event.getDuration().toMillis() >= MILLIS)) {
                throw new Exception("Event is wrong duration.");
            }

            if (struct.<Boolean>getValue("timedOut").booleanValue()) {
                throw new Exception("Should not have timed out.");
            }

            if (eventThread.equals(interruptedThread.getName())){
                if (notifThread != null) {
                    throw new Exception("Notifier of interrupted thread should be null");
                }
                interruptedFound = true;
            } else if (eventThread.equals(simpleWaitThread.getName())) {
                if (!notifThread.equals(simpleNotifyThread.getName())) {
                    throw new Exception("Notifier of simple wait is incorrect: "+ notifThread + " " +simpleNotifyThread.getName());
                }
                simpleWaitFound = true;
            }
        }
        if (!(simpleWaitFound && interruptedFound)) {
            throw new Exception("Couldn't find expected wait events. SimpleWaiter: "+ simpleWaitFound + " interrupted: "+ interruptedFound);
        }
    }

    static class Helper {
        public Thread interrupted;

        public synchronized void interrupt() throws InterruptedException {
            if (Thread.currentThread().equals(interruptedThread)) {
                // Ensure T1 enters critical section first
                interrupterThread.start();
                wait(); // allow T2 to enter section
            } else if (Thread.currentThread().equals(interrupterThread)) {
                // If T2 is in the critical section T1 is already waiting.
                Thread.sleep(MILLIS);
                interruptedThread.interrupt();
            }
        }

        public synchronized void simpleNotify() throws InterruptedException {
            if (Thread.currentThread().equals(simpleWaitThread)) {
                simpleNotifyThread.start();
                wait();
            } else if (Thread.currentThread().equals(simpleNotifyThread)) {
                Thread.sleep(MILLIS);
                notify();
            }
        }
    }
}
