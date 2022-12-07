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
 * 1. The events are emitted
 * 2. There are no duplicate streamed events
 * 3. The resulting JFR dump is readable and events can be read that match the events that were streamed.
 */
public class TestStreaming extends Test {
    private static final int MILLIS = 20;
    private static final int TIMEOUT_MILLIS = 10*1000;
    private static final int THREADS = 3;
    private static volatile int remainingEventsInStream = THREADS*2;
    static volatile int flushes = 0;
    static volatile boolean streamEndedSuccessfully = false;
    static final Helper helper = new Helper();
    static HashSet<String> streamEvents = new HashSet();

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
        rs.enable("jdk.JavaMonitorWait").withThreshold(Duration.ofMillis(MILLIS-1)).withStackTrace();
        rs.enable("com.redhat.EndStream");
        rs.onEvent("jdk.JavaMonitorWait", event -> {
            String thread = event.getThread("eventThread").getJavaName();
            if(!event.getClass("monitorClass").getName().equals(Helper.class.getName())){
                return;
            }
            if(streamEvents.contains(thread)) {
                return;
            }
            streamEvents.add(thread);
            remainingEventsInStream--;

        });

        //close stream once we get the signal
        rs.onEvent("com.redhat.EndStream", e -> {
            rs.close();
            streamEndedSuccessfully = true;
        });

        rs.onFlush(() -> {
            try {
                if (flushes==0) {
                    Stressor.execute(THREADS, r);
                    // at this point all expected events should be generated
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            flushes++;
        });

        rs.startAsync();
        Stressor.execute(THREADS, r);

        long start = System.currentTimeMillis();
        while (remainingEventsInStream > 0) {
            if (System.currentTimeMillis() - start > TIMEOUT_MILLIS && flushes > 1) { // if flushes > 1, then all events should have been generated
                throw new Exception("Not all expected monitor wait events were found in the JFR stream. Remaining:" + remainingEventsInStream);
            }
        }

        File directory = new File(".");
        Path p = new File(directory.getAbsolutePath(),getTestName()+ ".jfr").toPath();
        rs.dump(p);
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
            if (struct.<RecordedClass>getValue("monitorClass").getName().equals(Helper.class.getName()) && event.getDuration().toMillis() >= MILLIS-1) {
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
        return "Streaming Basic";
    }
}
