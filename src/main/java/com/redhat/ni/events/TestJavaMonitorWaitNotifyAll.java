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

import com.redhat.ni.tester.Tester;
import jdk.jfr.Recording;
import jdk.jfr.consumer.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

import static com.redhat.ni.tester.Tester.makeCopy;
import static java.lang.Math.abs;

public class TestJavaMonitorWaitNotifyAll implements com.redhat.ni.tester.Test{
    private static final int MILLIS = 50;
    static String waiterName1;
    static String waiterName2;
    static String notifierName;

    static Helper helper = new Helper();

    @Override
    public String getName() {
        return "jdk.JavaMonitorWait - Notify All";
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
            Thread tc1 = new Thread(consumer);
            Thread tp1 = new Thread(producer);
            Thread tp2 = new Thread(producer);
            waiterName1 = tp1.getName();
            waiterName2 = tp2.getName();
            notifierName = tc1.getName();


            tp1.start();
            tp2.start();
            tc1.start();

            tp1.join();
            tp2.join();
            tc1.join();

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
        for (RecordedEvent event : events) {
            RecordedObject struct = event;
            if (!event.getEventType().getName().equals("jdk.JavaMonitorWait")) {
                continue;
            }
            String eventThread = struct.<RecordedThread>getValue("eventThread").getJavaName();
            String notifThread = struct.<RecordedThread>getValue("notifier") != null ? struct.<RecordedThread>getValue("notifier").getJavaName() : null;
            if (!eventThread.equals(waiterName1) &&
                    !eventThread.equals(waiterName2) &&
                    !eventThread.equals(notifierName)) {
                continue;
            }
            if (!com.redhat.ni.tester.Tester.isGreaterDuration(Duration.ofMillis(MILLIS), event.getDuration())) {
                throw new Exception("Event is wrong duration.");
            }


            if (eventThread.equals(notifierName)) {
                if (!struct.<Boolean>getValue("timedOut").booleanValue()) {
                    throw new Exception("Should have timed out.");
                }
            } else {
                if (struct.<Boolean>getValue("timedOut").booleanValue()) {
                    throw new Exception("Should not have timed out.");
                }
                if (!notifThread.equals(notifierName)) {
                    throw new Exception("Notifier thread name is incorrect");
                }
            }
        }
    }


    static class Helper {
        public synchronized void produce() throws InterruptedException {
            wait();
        }

        public synchronized void consume() throws InterruptedException {
            //give the producers a headstart so they can start waiting
            wait(MILLIS);
            notifyAll(); //should wake up both producers
        }
    }
}