package com.redhat.ni.tester;


import com.redhat.ni.events.TestThreadPark;
import com.redhat.ni.tester.Test;
import jdk.jfr.Recording;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * This is the class that loads and launches all the tests.
 *
 */
public class Tester
{
    public static final long MS_TOLERANCE = 10;
    static HashMap<String,Test> tests = new HashMap<>();
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
    }

    /**
     * If you create a new test, add it in this method.
     */
    private static void loadTests() {
        tests.put("TestThreadPark", new TestThreadPark());
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
}
