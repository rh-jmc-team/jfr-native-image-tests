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

package com.redhat.ni.streaming;

import com.redhat.ni.tester.Test;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingStream;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;

/**
 * This test induces repeated chunk rotations and spawns many threads that create native events.
 * The goal of this test is to repeatedly create and remove nodes from the JfrBufferNodeLinkedList.
 * This is a stress test to expose bugs from concurrent operations on the linked list.
 */
public class TestStress extends Test {
    private static final int THREADS = 8;
    private static final int COUNT = 10;
    private static int iterations = 10;
    private static volatile int remainingClassEventsInStream = THREADS*COUNT*10;
    private static volatile int remainingIntegerEventsInStream = THREADS*COUNT*10;
    private static volatile int remainingStringEventsInStream = THREADS*COUNT*10;
    private static volatile int remainingWaitEventsInStream = THREADS*COUNT*10;
    static volatile int flushes = 0;
    static volatile boolean doneCollection = false;
    static volatile boolean streamEndedSuccessfully = false;
    private static final int TIMEOUT_MILLIS = 10*1000;
    static final Helper helper = new Helper();

    static volatile Path p;
    @Override
    public void test() throws Exception {
        Runnable r = () -> {
            for (int i = 0; i < COUNT; i++) {
                StringEvent stringEvent = new StringEvent();
                stringEvent.message = "StringEvent has been generated as part of TestConcurrentEvents.";
                stringEvent.commit();

                IntegerEvent integerEvent = new IntegerEvent();
                integerEvent.number = Integer.MAX_VALUE;
                integerEvent.commit();

                ClassEvent classEvent = new ClassEvent();
                classEvent.clazz = Math.class;
                classEvent.commit();
                try {
                    helper.doEvent();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        };

        var rs = new RecordingStream();
        rs.enable("com.redhat.String");
        rs.enable("com.redhat.Integer");
        rs.enable("com.redhat.Class");
        rs.enable("com.redhat.EndStream");
        rs.enable("jdk.JavaMonitorWait").withThreshold(Duration.ofNanos(0));
        rs.onEvent("com.redhat.Class", event -> {
            remainingClassEventsInStream--;
        });
        rs.onEvent("com.redhat.Integer", event -> {
            remainingIntegerEventsInStream--;
        });
        rs.onEvent("com.redhat.String", event -> {
            remainingStringEventsInStream--;
        });
        rs.onEvent("jdk.JavaMonitorWait", event -> {
            if(!event.getClass("monitorClass").getName().equals(Helper.class.getName())){
                return;
            }
            remainingWaitEventsInStream--;
        });
        //close stream once we get the signal
        rs.onEvent("com.redhat.EndStream", e -> {
            rs.close();
            streamEndedSuccessfully = true;
        });

        File directory = new File(".");
        p = new File(directory.getAbsolutePath(),getTestName()+ ".jfr").toPath();

        Runnable rotateChunk = () -> {
            try {
                if (flushes%3==0 && !doneCollection) {
                    rs.dump(p); // force chunk rotation
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            flushes++;
        };

        rs.onFlush(rotateChunk);
        rs.startAsync();
        while(iterations > 0) {
            com.redhat.ni.tester.Stressor.execute(THREADS, r);
            iterations--;
        }
        // At this point all events have been generated and all threads joined.

        long start = System.currentTimeMillis();
        while (remainingClassEventsInStream != 0 || remainingIntegerEventsInStream != 0 || remainingStringEventsInStream != 0 ||remainingWaitEventsInStream != 0) {
            if (System.currentTimeMillis() - start > TIMEOUT_MILLIS) {
                throw new Exception("Not all expected events were found in the stream. Class:" + remainingClassEventsInStream
                        + " Integer:" + remainingIntegerEventsInStream + " String:" + remainingStringEventsInStream + " Wait:"+remainingWaitEventsInStream);
            }
        }
        doneCollection = true;

        rs.dump(p);
        // We require a signal to close the stream, because if we close the stream immediately after dumping, the dump may not have had time to finish.
        EndStreamEvent endStreamEvent = new EndStreamEvent();
        endStreamEvent.commit();
        rs.awaitTermination(Duration.ofMillis(TIMEOUT_MILLIS));
        if (!streamEndedSuccessfully){
            throw new Exception("unable to find stream end event signal in stream");
        }
    }

    static class Helper {
        public synchronized void doEvent() throws InterruptedException {
            wait(0,1);
        }
    }

    @Override
    public String getName() {
        return "com.redhat.String";
    }
    @Override
    public String getTestName(){
        return "StressTest";
    }
}
