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

import com.redhat.ni.tester.Test;
import jdk.jfr.Recording;
import jdk.jfr.consumer.*;

import java.time.Duration;
import java.util.List;

public class TestThreadSleep extends Test {
    private static final int MILLIS = 50;
    static String sleepingThreadName;

    @Override
    public void test() throws Exception {
        Recording recording = new Recording();
        recording.enable("jdk.ThreadSleep").withThreshold(Duration.ofMillis(1));

        try {
            recording.start();
            sleepingThreadName = Thread.currentThread().getName();
            Thread.sleep(MILLIS);
            // sleep so we know the event is recorded
            Thread.sleep(500);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            recording.stop();
        }
        List<RecordedEvent> events = getEvents(recording, getName());
        boolean foundSleepEvent = false;
        for (RecordedEvent event : events) {
            if (!event.getEventType().getName().equals("jdk.ThreadSleep")) {
                continue;
            }
            RecordedObject struct = event;
            String eventThread = struct.<RecordedThread>getValue("eventThread").getJavaName();
            if (!eventThread.equals(sleepingThreadName)) {
                continue;
            }
            if (!isEqualDuration(event.getDuration(), Duration.ofMillis(MILLIS))) {
                continue;
            }
            foundSleepEvent = true;
            break;
        }
        if (!foundSleepEvent) {
            throw new Exception("Sleep event not found.");
        }
    }

    @Override
    public String getName(){
        return "jdk.ThreadSleep";
    }
}