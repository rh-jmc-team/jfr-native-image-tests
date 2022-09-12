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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class Test {
    public static final long MS_TOLERANCE = 10;
    private ChronologicalComparator chronologicalComparator = new ChronologicalComparator();

    public void test() throws Exception {
        System.out.println("Test is unimplemented" + this.getClass().getName());
    }

    public String getName() {
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

    /** Used for comparing durations with a tolerance of MS_TOLERANCE */
    protected boolean isEqualDuration(Duration d1, Duration d2) {
        return d1.minus(d2).abs().compareTo(Duration.ofMillis(MS_TOLERANCE)) < 0;

    }

    /** Used for comparing durations with a tolerance of MS_TOLERANCE. True if 'larger' really is bigger */
    protected boolean isGreaterDuration(Duration smaller, Duration larger) {
        return smaller.minus(larger.plus(Duration.ofMillis(MS_TOLERANCE))).isNegative();
    }

    private class ChronologicalComparator implements Comparator<RecordedEvent> {
        @Override
        public int compare(RecordedEvent e1, RecordedEvent e2) {
            return e1.getStartTime().compareTo(e2.getStartTime());
        }
    }

    protected List<RecordedEvent> getEvents(Recording recording, String testName) throws IOException {
        Path p = makeCopy(recording, testName);
        List<RecordedEvent> events = RecordingFile.readAllEvents(p);
        Collections.sort(events, chronologicalComparator);
        Files.deleteIfExists(p);
        return events;
    }
}