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

            LockSupport.parkNanos(500 * 1000000);
            LockSupport.parkNanos(blocker, 500*1000000);
            LockSupport.parkUntil(System.currentTimeMillis() + 500);

            Thread mainThread = Thread.currentThread();
            Runnable unparker = () -> {
                try {
                    Thread.sleep(1000);
                    LockSupport.unpark(mainThread);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            };
            Thread unparkerThread = new Thread(unparker);
            unparkerThread.start();
            LockSupport.park();
            unparkerThread.join();

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
            if (event.getEventType().getName().equals("jdk.ThreadPark")) {

                if (isEqualDuration(event.getDuration(), Duration.ofMillis(500))) {
                    if (!struct.<Long>getValue("timeout").equals(new Long(500*1000000))) {
                        if (struct.<Long>getValue("timeout") < 0) {
                            parkUntilFound = true;
                        }
                    }

                    if (struct.getValue("parkedClass") == null) {
                        parkNanosFound = true;
                    } else
                        if (struct.<RecordedClass>getValue("parkedClass").getName().equals("com.redhat.ni.events.TestThreadPark$Blocker")) {
                            parkNanosFoundBlocker = true;
                        }
                }else {
                    if (struct.<Long>getValue("timeout") < 0 && struct.<Long>getValue("until") < 0) {
                        parkUnparkFound = true;
                    }
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