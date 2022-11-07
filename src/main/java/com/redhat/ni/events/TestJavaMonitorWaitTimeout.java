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

package main.java.com.redhat.ni.events;

import com.redhat.ni.tester.Test;
import com.redhat.ni.tester.Tester;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedObject;
import jdk.jfr.consumer.RecordedThread;

import java.time.Duration;
import java.util.List;

public class TestJavaMonitorWaitTimeout extends Test {
    private static final int MILLIS = 50;
    static final Helper helper = new Helper();
    static Thread unheardNotifierThread;
    static Thread timeoutThread;

    static Thread simpleWaitThread;
    static Thread simpleNotifyThread;
    private boolean timeoutFound = false;
    private boolean simpleWaitFound = false;

    @Override
    public String getName() {
        return "jdk.JavaMonitorWait";
    }
    @Override
    public String getTestName(){
        return getName()+ "_timeout";
    }
    private static void testTimeout() throws InterruptedException {
        Runnable unheardNotifier = () -> {
            helper.unheardNotify();
        };

        Runnable timouter = () -> {
            try {
                helper.timeout();
            } catch (InterruptedException e) {
                throw new RuntimeException();
            }
        };

        unheardNotifierThread = new Thread(unheardNotifier);
        timeoutThread = new Thread(timouter);

        timeoutThread.start();
        timeoutThread.join();

        // wait for timeout before trying to notify
        unheardNotifierThread.start();
        unheardNotifierThread.join();

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
            testTimeout();
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
            if (!eventThread.equals(unheardNotifierThread.getName()) &&
                    !eventThread.equals(timeoutThread.getName()) &&
                    !eventThread.equals(simpleNotifyThread.getName()) &&
                    !eventThread.equals(simpleWaitThread.getName())) {
                continue;
            }
            if (!struct.<RecordedClass>getValue("monitorClass").getName().equals(Helper.class.getName())) {
                continue;
            }
            if (!(event.getDuration().toMillis() >= MILLIS-5)) {
                throw new Exception("Event is wrong duration.");
            }
            if (eventThread.equals(timeoutThread.getName())) {
                if (notifThread != null) {
                    throw new Exception("Notifier of timeout thread should be null");
                }
                if (!struct.<Boolean>getValue("timedOut").booleanValue()) {
                    throw new Exception("Should have timed out.");
                }
                timeoutFound = true;
            } else if (eventThread.equals(simpleWaitThread.getName())) {
                if (!notifThread.equals(simpleNotifyThread.getName())) {
                    throw new Exception("Notifier of simple wait is incorrect: "+ notifThread + " " +simpleNotifyThread.getName());
                }
                simpleWaitFound = true;
            }

        }
        if (!(simpleWaitFound && timeoutFound)) {
            throw new Exception("Couldn't find expected wait events. SimpleWaiter: "+ simpleWaitFound + " timeout: "+ timeoutFound);
        }

    }
    static class Helper {
        public synchronized void timeout() throws InterruptedException {
            wait(MILLIS);
        }

        public synchronized void unheardNotify() {
            notify();
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
