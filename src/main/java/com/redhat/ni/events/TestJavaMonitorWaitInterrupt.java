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
    static Helper helper = new Helper();
    static String interruptedName;
    static String interrupterName;
    static String simpleWaitName;
    static String simpleNotifyName;

    private boolean interruptedFound = false;
    private boolean simpleWaitFound = false;

    @Override
    public String getName() {
        return "jdk.JavaMonitorWait - Interrupt";
    }
    @Override
    public void test() throws Exception {

        Recording recording = new Recording();
        recording.enable("jdk.JavaMonitorWait").withThreshold(Duration.ofMillis(1));
        try {
            recording.start();

            Runnable interrupter = () -> {
                try {
                    helper.interrupt();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            };

            Runnable interrupted = () -> {
                try {
                    helper.interrupted();
                    throw new RuntimeException("Was not interrupted!!");
                } catch (InterruptedException e) {
                    //should get interrupted
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
            Thread interrupterThread = new Thread(interrupter);
            Thread interruptedThread = new Thread(interrupted);
            helper.interrupted = interruptedThread;
            interrupterName = interrupterThread.getName();
            interruptedName = interruptedThread.getName();



            interruptedThread.start();
            Thread.sleep(MILLIS); //pause to ensure expected ordering of lock acquisition
            interrupterThread.start();

            interruptedThread.join();
            interrupterThread.join();

            Thread tw = new Thread(simpleWaiter);
            Thread tn = new Thread(simpleNotifier);
            simpleWaitName = tw.getName();
            simpleNotifyName = tn.getName();


            tw.start();
            Thread.sleep(50);
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
            if (!eventThread.equals(interrupterName) &&
                    !eventThread.equals(interruptedName) &&
                    !eventThread.equals(simpleNotifyName) &&
                    !eventThread.equals(simpleWaitName)) {
                continue;
            }
            if (!struct.<RecordedClass>getValue("monitorClass").getName().equals(Helper.class.getName())) {
                continue;
            }
            if (!isGreaterDuration(Duration.ofMillis(MILLIS), event.getDuration())) {
                throw new Exception("Event is wrong duration.");
            }

            if (struct.<Boolean>getValue("timedOut").booleanValue()) {
                throw new Exception("Should not have timed out.");
            }

            if (eventThread.equals(interruptedName)){
                if (notifThread != null) {
                    throw new Exception("Notifier of interrupted thread should be null");
                }
                interruptedFound = true;
            } else if (eventThread.equals(simpleWaitName)) {
                if (!notifThread.equals(simpleNotifyName)) {
                    throw new Exception("Notifier of simple wait is incorrect: "+ notifThread + " " +simpleNotifyName);
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
        public synchronized void interrupted() throws InterruptedException {
            wait();
        }

        public synchronized void interrupt() throws InterruptedException {
            interrupted.interrupt();
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
