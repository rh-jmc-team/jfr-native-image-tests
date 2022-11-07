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

import jdk.jfr.Recording;
import jdk.jfr.consumer.*;

import java.time.Duration;
import java.util.List;

public class TestJavaMonitorWaitNotifyAll extends com.redhat.ni.tester.Test{
    private static final int MILLIS = 50;
    static final Helper helper = new Helper();
    static Thread producerThread1;
    static Thread producerThread2;
    static Thread consumerThread;

    private boolean notifierFound = false;
    private int waitersFound = 0;
    @Override
    public String getName() {
        return "jdk.JavaMonitorWait";
    }

    @Override
    public String getTestName(){
        return getName()+ "_notify_all";
    }
    @Override
    public void test() throws Exception {

        Recording recording = new Recording();
        recording.enable("jdk.JavaMonitorWait").withThreshold(Duration.ofMillis(1));
        try {
            recording.start();

            Runnable producer = () -> {
                try {
                    helper.doWork();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            };
            producerThread1 = new Thread(producer);
            producerThread2 = new Thread(producer);
            Runnable consumer = () -> {
                try {
                    helper.doWork();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            };

            consumerThread = new Thread(consumer);
            producerThread1.start();
            consumerThread.join();
            producerThread1.join();
            producerThread2.join();
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
            if (!eventThread.equals(producerThread1.getName()) &&
                    !eventThread.equals(producerThread2.getName()) &&
                    !eventThread.equals(consumerThread.getName())) {
                continue;
            }
            if (!struct.<RecordedClass>getValue("monitorClass").getName().equals(Helper.class.getName())) {
                continue;
            }
            if (event.getDuration().toMillis() < MILLIS-1) { // -1 for tolerance
                throw new Exception("Event is wrong duration." + event.getDuration().toMillis());
            }

            if (eventThread.equals(consumerThread.getName())) {
                if (!struct.<Boolean>getValue("timedOut").booleanValue()) {
                    throw new Exception("Should have timed out.");
                }
                notifierFound = true;
            } else {
                if (struct.<Boolean>getValue("timedOut").booleanValue()) {
                    throw new Exception("Should not have timed out.");
                }
                if (!notifThread.equals(consumerThread.getName())) {
                    throw new Exception("Notifier thread name is incorrect");
                }
                waitersFound++;
            }
        }
        if (!notifierFound || waitersFound < 2) {
            throw new Exception("Couldn't find expected wait events. NotifierFound: "+ notifierFound + " waitersFound: "+ waitersFound);
        }
    }


    static class Helper {
        public synchronized void doWork() throws InterruptedException {
            if (Thread.currentThread().equals(consumerThread)) {
                wait(MILLIS);
                notifyAll(); // should wake up both producers
            } else if (Thread.currentThread().equals(producerThread1)){
                producerThread2.start();
                wait();
            } else if (Thread.currentThread().equals(producerThread2)){
                consumerThread.start();
                wait();
            }
        }
    }
}
