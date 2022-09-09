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

import com.redhat.ni.events.TestJavaMonitorWait;
import com.redhat.ni.events.TestJavaMonitorWaitInterrupt;
import com.redhat.ni.events.TestThreadPark;
import com.redhat.ni.tester.Test;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;

/**
 * This is the class that loads and launches all the tests.
 *
 */
public class Tester
{
    public static final long MS_TOLERANCE = 10;
    static HashMap<String,Test> tests = new HashMap<>();// use hashtable in case we want to add ability to run specific tests only
    static Boolean all = true;
    public static void main( String[] args ) throws Exception {
        if (args.length > 0) {
            all = false;
        }
        System.out.println("Loading tests...");
        loadTests();
        System.out.println("Starting tests...");

        if (all) {
            Test test;
            for (String testName : tests.keySet()) {
                test = tests.get(testName);
                if (test != null) {
                    System.out.println(test.getName());
                    test.test();
                }
            }
        }

        System.out.println("If there were no exceptions, all tests PASSED.");
    }

    /**
     * If you create a new test, add it in this method.
     */
    private static void loadTests() {
        tests.put("TestThreadPark", new TestThreadPark());
        tests.put("TestJavaMonitorEnter", new com.redhat.ni.events.TestJavaMonitorEnter());
        tests.put("TestJavaMonitorWait", new TestJavaMonitorWait());
        tests.put("TestJavaMonitorWaitInterrupt", new TestJavaMonitorWaitInterrupt());
    }

    public static Path makeCopy(Recording recording) throws IOException { // from jdk 19
        Path p = recording.getDestination();
        if (p == null) {
            File directory = new File(".");
            // FIXME: Must come up with a way to give human-readable name
            // this will at least not clash when running parallel.
            ProcessHandle h = ProcessHandle.current();
            p = new File(directory.getAbsolutePath(), "recording-" + recording.getId() + "-pid" + h.pid() + ".jfr").toPath();
            recording.dump(p);
        }
        return p;
    }

    public static boolean isEqualDuration(Duration d1, Duration d2) throws Exception {
        return d1.minus(d2).abs().compareTo(Duration.ofMillis(MS_TOLERANCE)) < 0;

    }

    public static boolean isGreaterDuration(Duration smaller, Duration larger) throws Exception {
        return smaller.minus(larger.plus(Duration.ofMillis(MS_TOLERANCE))).isNegative(); // True if 'larger' really is bigger
    }

    public static class ChronologicalComparator implements Comparator<RecordedEvent> {
        @Override
        public int compare(RecordedEvent e1, RecordedEvent e2) {
            return e1.getStartTime().compareTo(e2.getStartTime());
        }
    }
}
