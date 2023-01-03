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
import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedObject;
import jdk.jfr.consumer.RecordedThread;

import java.time.Duration;
import java.util.List;
import com.redhat.ni.tester.Test;

public class TestJavaMonitorEnter extends Test {
    private static final int MILLIS = 60;

    static volatile boolean passedCheckpoint = false;
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
                passedCheckpoint = true;
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
            if (Thread.currentThread().equals(secondThread)) {
                return; // second thread doesn't need to do work.
            }
            // ensure ordering of critical section entry
            secondThread.start();

            // spin until second thread blocks
            while (!secondThread.getState().equals(Thread.State.BLOCKED) || !passedCheckpoint) {
                Thread.sleep(10);
            }
            Thread.sleep(MILLIS);
        }
    }
    @Override
    public String getName() {
        return "jdk.JavaMonitorEnter";
    }
    @Override
    public String getTestName(){
        return getName();
    }
}
