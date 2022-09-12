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
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedObject;
import jdk.jfr.consumer.RecordedThread;

import java.time.Duration;
import java.util.List;

public class TestJavaMonitorWaitTimeout extends Test {
    private static final int MILLIS = 50;
    static Helper helper = new Helper();
    static String timeOutName;
    static String notifierName;
    static String simpleWaitName;
    static String simpleNotifyName;

    @Override
    public String getName() {
        return "jdk.JavaMonitorWait - Time Out";
    }
    @Override
    public void test() throws Exception {

        Recording recording = new Recording();
        recording.enable("jdk.JavaMonitorWait").withThreshold(Duration.ofMillis(1));
        try {
            recording.start();
            Runnable unheardNotifier = () -> {
                try {
                    helper.unheardNotify();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            };

            Runnable timouter = () -> {
                try {
                    helper.timeout();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            };

            Runnable simpleWaiter = () -> {
                try {
                    helper.simpleWait();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            };

            Runnable simpleNotifier = () -> {
                try {
                    helper.simpleNotify();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            };
            Thread unheardNotifierThread = new Thread(unheardNotifier);
            Thread timeoutThread = new Thread(timouter);
            timeOutName = unheardNotifierThread.getName();
            notifierName = unheardNotifierThread.getName();


            timeoutThread.start();
            Thread.sleep(10);
            unheardNotifierThread.start();

            timeoutThread.join();
            unheardNotifierThread.join();

            Thread tw = new Thread(simpleWaiter);
            Thread tn = new Thread(simpleNotifier);
            simpleWaitName = tw.getName();
            simpleNotifyName = tn.getName();


            tw.start();
            Thread.sleep(10);
            tn.start();

            tw.join();
            tn.join();

            // sleep so we know the event is recorded
            Thread.sleep(500);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            recording.stop();
        }

        List<RecordedEvent> events = getEvents(recording, getName());
        for (RecordedEvent event : events) {
            RecordedObject struct = event;
            if (!event.getEventType().getName().equals("jdk.JavaMonitorWait")) {
                continue;
            }
            String eventThread = struct.<RecordedThread>getValue("eventThread").getJavaName();
            String notifThread = struct.<RecordedThread>getValue("notifier") != null ? struct.<RecordedThread>getValue("notifier").getJavaName() : null;
            if (!eventThread.equals(notifierName) &&
                    !eventThread.equals(timeOutName) &&
                    !eventThread.equals(simpleNotifyName) &&
                    !eventThread.equals(simpleWaitName)) {
                continue;
            }
            if (!isGreaterDuration(Duration.ofMillis(MILLIS), event.getDuration())) {
                throw new Exception("Event is wrong duration.");
            }
            if (eventThread.equals(timeOutName)) {
                if (notifThread != null) {
                    throw new Exception("Notifier of interrupted thread should be null");
                }
                if (!struct.<Boolean>getValue("timedOut").booleanValue()) {
                    throw new Exception("Should have timed out.");
                }
            } else if (eventThread.equals(simpleWaitName)) {
                if (!notifThread.equals(simpleNotifyName)) {
                    throw new Exception("Notifier of simple wait is incorrect: "+ notifThread + " " +simpleNotifyName);
                }
            }

        }

    }
    static class Helper {
        public synchronized void timeout() throws InterruptedException {
            wait(MILLIS);
        }

        public synchronized void unheardNotify() throws InterruptedException {
            Thread.sleep(2*MILLIS);
            //notify after timeout
            notify();
        }

        public synchronized void simpleWait() throws InterruptedException {
            wait();
        }
        public synchronized void simpleNotify() throws InterruptedException {
            Thread.sleep(2*MILLIS);
            notify();
        }
    }
}
