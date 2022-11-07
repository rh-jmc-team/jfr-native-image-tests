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

package com.redhat.ni.tester;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class Test {
    private final ChronologicalComparator chronologicalComparator = new ChronologicalComparator();

    public void test() throws Exception {
        System.out.println("Test is unimplemented" + this.getClass().getName());
    }

    public String getName() {
        return "Event has no name";
    }
    public String getTestName() {
        return "Test has no name";
    }


    private Path makeCopy(Recording recording, String testName) throws IOException { // from jdk 19
        Path p = recording.getDestination();
        if (p == null) {
            File directory = new File(".");
            p = new File(directory.getAbsolutePath(), "recording-" + recording.getId() + "-" + testName+ ".jfr").toPath();
            recording.dump(p);
        }
        return p;
    }


    private class ChronologicalComparator implements Comparator<RecordedEvent> {
        @Override
        public int compare(RecordedEvent e1, RecordedEvent e2) {
            return e1.getEndTime().compareTo(e2.getEndTime());
        }
    }

    protected List<RecordedEvent> getEvents(Recording recording, String testName) throws IOException {
        Path p = makeCopy(recording, testName);
        List<RecordedEvent> events = RecordingFile.readAllEvents(p);
        Collections.sort(events, chronologicalComparator);
        // remove events that are not in the list of tested events
        events.removeIf(event -> (!testName.equals(event.getEventType().getName())));
        Files.deleteIfExists(p);
        return events;
    }
}