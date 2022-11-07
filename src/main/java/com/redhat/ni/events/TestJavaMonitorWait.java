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

import jdk.jfr.Recording;
import jdk.jfr.consumer.*;

import java.time.Duration;
import java.util.List;
import com.redhat.ni.tester.Test;
import static java.lang.Math.abs;

public class TestJavaMonitorWait extends Test{
    private static final int MILLIS = 50;
    private static final int COUNT = 10;
    private static String producerName;
    private static String consumerName;
    static final Helper helper = new Helper();

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
            producerName = tp.getName();
            consumerName = tc.getName();
            tp.start();
            tc.start();
            tp.join();
            tc.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            recording.stop();
        }

        List<RecordedEvent> events = getEvents(recording, getName());
        int prodCount = 0;
        int consCount = 0;
        String lastEventThreadName = null; //should alternate if buffer is 1

        for (RecordedEvent event : events) {
            RecordedObject struct = event;

            if (!(event.getDuration().toMillis() >= MILLIS)) {
                continue;
            }
            //check which thread emitted the event
            String eventThread = struct.<RecordedThread>getValue("eventThread").getJavaName();
            String notifThread = struct.<RecordedThread>getValue("notifier") != null ? struct.<RecordedThread>getValue("notifier").getJavaName() : null;
            if (!eventThread.equals(consumerName) &&
                    !eventThread.equals(producerName)) {
                continue;
            }
            if (!struct.<RecordedClass>getValue("monitorClass").getName().equals(Helper.class.getName()) &&
                    (eventThread.equals(consumerName) ||eventThread.equals(producerName))) {
                throw new Exception("Wrong monitor class.");
            }
            if (struct.<Boolean>getValue("timedOut").booleanValue()) {
                throw new Exception("Should not have timed out.");
            }

            if (lastEventThreadName == null) {
                lastEventThreadName = notifThread;
            }
            if (!lastEventThreadName.equals(notifThread)) {
                throw new Exception("Not alternating");
            }
            if (eventThread.equals(producerName)) {
                prodCount++;
                if (!notifThread.equals(consumerName)) {
                    throw new Exception("Wrong notifier");
                }

            } else if (eventThread.equals(consumerName)) {
                consCount++;
                if (!notifThread.equals(producerName)) {
                    throw new Exception("Wrong notifier");
                }
            }
            lastEventThreadName = eventThread;
        }


        if (abs(prodCount - consCount) > 1 || abs(consCount - COUNT) >1) {
            throw new Exception("Wrong number of events: "+prodCount + " "+consCount);
        }

    }
    static class Helper {
        private int count = 0;
        private final int bufferSize = 1;

        public synchronized void produce() throws InterruptedException {
            for (int i = 0; i < COUNT; i++) {
                while (count >= bufferSize) {
                    wait();
                }
                Thread.sleep(MILLIS);
                count++;
                notify();
            }
        }

        public synchronized void consume() throws InterruptedException {
            for (int i = 0; i < COUNT; i++) {
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
