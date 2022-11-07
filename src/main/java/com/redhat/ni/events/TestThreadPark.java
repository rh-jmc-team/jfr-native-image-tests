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
import com.redhat.ni.tester.Test;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedEvent;

import jdk.jfr.consumer.RecordedObject;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.locks.LockSupport;
public class TestThreadPark extends Test {
    private static final int MILLIS = 500;
    private static final int NANOS = MILLIS * 1000000;

    static volatile boolean passedCheckpoint = false;

    class Blocker {
    }
    final Blocker blocker = new Blocker();
    @Override
    public void test() throws Exception {
        Recording recording = new Recording();
        recording.enable("jdk.ThreadPark").withThreshold(Duration.ofMillis(1));
        boolean parkNanosFound = false;
        boolean parkNanosFoundBlocker = false;
        boolean parkUntilFound = false;
        boolean parkUnparkFound = false;
        try {
            recording.start();

            LockSupport.parkNanos(NANOS);
            LockSupport.parkNanos(blocker, NANOS);
            LockSupport.parkUntil(System.currentTimeMillis() + MILLIS);

            Runnable waiter = () -> {
                passedCheckpoint = true;
                LockSupport.park();
            };
            Thread waiterThread = new Thread(waiter);
            waiterThread.start();
            while (!waiterThread.getState().equals(Thread.State.WAITING) || !passedCheckpoint) {
                Thread.sleep(10);
            }
            LockSupport.unpark(waiterThread);
            waiterThread.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            recording.stop();
        }

        List<RecordedEvent> events = getEvents(recording, getName());
        for (RecordedEvent event : events) {
            RecordedObject struct = event;
            if (struct.<Long>getValue("timeout") < 0 && struct.<Long>getValue("until") < 0) {
                parkUnparkFound = true;
            } else{
                if (!(struct.<Long>getValue("timeout").longValue() ==(NANOS))) {
                    if (struct.<Long>getValue("timeout") < 0) {
                        parkUntilFound = true;
                        continue;
                    }
                }
                if (struct.getValue("parkedClass") == null) {
                    parkNanosFound = true;
                } else
                    if (struct.<RecordedClass>getValue("parkedClass").getName().equals("com.redhat.ni.events.TestThreadPark$Blocker")) {
                        parkNanosFoundBlocker = true;
                    }
            }

        }
        if (!parkNanosFound){
            throw new Exception("parkNanosFound false");
        }
        if (!parkNanosFoundBlocker){
            throw new Exception("parkNanosFoundBlocker false");
        }
        if (!parkUntilFound){
            throw new Exception("parkUntilFound false");
        }
        if (!parkUnparkFound){
            throw new Exception("parkUnparkFound false");
        }
    }

    @Override
    public String getName(){
        return "jdk.ThreadPark";
    }
}