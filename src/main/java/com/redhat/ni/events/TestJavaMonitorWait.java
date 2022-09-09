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

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

import static com.redhat.ni.tester.Tester.makeCopy;
import static java.lang.Math.abs;

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
        Tester.sortEvents(events);
        Files.deleteIfExists(p);
        int prodCount = 0;
        int consCount = 0;
        Long prodTid = null;
        Long consTid = null;
        Long lastTid = null; //should alternate if buffer is 1
        for (RecordedEvent event : events) {
            RecordedObject struct = event;
            if (!event.getEventType().getName().equals("jdk.JavaMonitorWait")) {
            continue;
            }
            if (!com.redhat.ni.tester.Tester.isEqualDuration(Duration.ofMillis(MILLIS), event.getDuration())) {
                continue;
            }
            //check which thread emitted the event
            Long eventThread = struct.<RecordedThread>getValue("eventThread").getId();
            Long notifThread = struct.<RecordedThread>getValue("notifier").getId();
            if (!struct.<RecordedClass>getValue("monitorClass").getName().equals("com.redhat.ni.events.TestJavaMonitorWait$Helper") &&
                    (eventThread.equals(consTid) ||eventThread.equals(prodTid))) {
                throw new Exception("Wrong monitor class.");
            }
            if (struct.<Boolean>getValue("timedOut").booleanValue()) {
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


        if (abs(prodCount - consCount) > 1 || abs(consCount-COUNT) >1) {
            throw new Exception("Wrong number of events: "+prodCount + " "+consCount);
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
