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
import jdk.jfr.consumer.*;
import com.redhat.ni.tester.Stressor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;

/**
 * Check to make sure
 * 1. Successive chunk rotations do not mess up the events in the resulting JFR file
 */
public class TestStreamingChunkRotation extends Test {
    private static final int MILLIS = 20;
    private static final int TIMEOUT_MILLIS = 10*1000;

    private static final int THREADS = 3;
    private static volatile int remainingEventsInStream = THREADS;
    static volatile int flushes = 0;
    static final Helper helper = new Helper();
    static HashSet<String> streamEvents = new HashSet();
    static volatile boolean streamEndedSuccessfully = false;

    static volatile Path p;
    @Override
    public void test() throws Exception {
        Runnable r = () -> {
            try {
                helper.doEvent();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        };

        var rs = new RecordingStream();
        rs.enable("jdk.JavaMonitorWait").withThreshold(Duration.ofMillis(10)).withStackTrace();
        rs.enable("com.redhat.EndStream");
        rs.onEvent("jdk.JavaMonitorWait", event -> {
            String thread = event.getThread("eventThread").getJavaName();

            if (!event.getClass("monitorClass").getName().equals(Helper.class.getName())) {
                return;
            }
            if (streamEvents.contains(thread)) {
                return;
            }
            streamEvents.add(thread);
            remainingEventsInStream--;
        });

        File directory = new File(".");
        p = new File(directory.getAbsolutePath(), getTestName() + ".jfr").toPath();

        rs.onFlush(() -> {
            try {
                if (flushes == 0) {
                    rs.dump(p); // force chunk rotation
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            flushes++;
        });

        //close stream once we get the signal
        rs.onEvent("com.redhat.EndStream", e -> {
            rs.close();
            streamEndedSuccessfully = true;
        });

        rs.startAsync();
        Stressor.execute(THREADS, r);
        // At this point all events have been generated and all threads joined.

        long start = System.currentTimeMillis();
        while (remainingEventsInStream > 0){
            if (System.currentTimeMillis() - start > TIMEOUT_MILLIS) {
                throw new Exception("Not all expected monitor wait events were found in the JFR stream. Remaining:" + remainingEventsInStream);
            }
        }

        rs.dump(p); // Ensure chunks get rotated a second time

        // We require a signal to close the stream, because if we close the stream immediately after dumping, the dump may not have had time to finish.
        EndStreamEvent endStreamEvent = new EndStreamEvent();
        endStreamEvent.commit();
        rs.awaitTermination(Duration.ofMillis(TIMEOUT_MILLIS));
        if (!streamEndedSuccessfully){
            throw new Exception("unable to find stream end event signal in stream");
        }

        List<RecordedEvent> events = getEvents(p, getName());
        for (RecordedEvent event : events) {
            RecordedObject struct = event;
            String eventThread = struct.<RecordedThread>getValue("eventThread").getJavaName();
            if (struct.<RecordedClass>getValue("monitorClass").getName().equals(Helper.class.getName()) && event.getDuration().toMillis() >= MILLIS -1) {
                if(!streamEvents.contains(eventThread)){
                    continue;
                }
                streamEvents.remove(eventThread);
            }
        }

        if (!streamEvents.isEmpty()){
            throw new Exception("Not all expected monitor wait events were found in the JFR file");
        }

    }
    static class Helper {
        public synchronized void doEvent() throws InterruptedException {
            wait(MILLIS);
        }
    }

    @Override
    public String getName() {
        return "jdk.JavaMonitorWait";
    }
    @Override
    public String getTestName(){
        return "Streaming successive chunk rotations";
    }
}
