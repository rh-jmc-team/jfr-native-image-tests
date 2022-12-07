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

import java.util.HashMap;

/**
 * This is the class that loads and launches all the tests.
 *
 */
public class Tester
{
    static HashMap<String,Test> tests = new HashMap<>();// use hashtable in case we want to add ability to run specific tests only
    static Boolean all = true;
    public static void main( String[] args ) throws Exception {
        if (args.length > 0) {
            all = false;
        }
        System.out.println("Loading tests...");
        loadTests();
        System.out.println("Starting tests...");
        int errors = 0;
        if (all) {
            Test test;
            for (String testName : tests.keySet()) {
                test = tests.get(testName);
                if (test != null) {
                    System.out.println(test.getTestName());
                    try {
                        test.test();
                    } catch (Exception e) {
                        System.out.println("-----FAILED "+test.getTestName()+"-----");
                        System.out.println(e.getMessage());
                        e.printStackTrace();
                        errors++;
                    }
                }
            }
        }

        System.out.println("Errors:"+errors);
    }

    /**
     * If you create a new test, add it in this method.
     */
    private static void loadTests() {
        tests.put("TestThreadPark", new com.redhat.ni.events.TestThreadPark());
        tests.put("TestJavaMonitorEnter", new com.redhat.ni.events.TestJavaMonitorEnter());
        tests.put("TestThreadSleep", new com.redhat.ni.events.TestThreadSleep());
        tests.put("TestJavaMonitorWait", new com.redhat.ni.events.TestJavaMonitorWait());
        tests.put("TestJavaMonitorWaitInterrupt", new com.redhat.ni.events.TestJavaMonitorWaitInterrupt());
        tests.put("TestJavaMonitorWaitNotifyAll", new com.redhat.ni.events.TestJavaMonitorWaitNotifyAll());
        tests.put("TestJavaMonitorWaitTimeout", new com.redhat.ni.events.TestJavaMonitorWaitTimeout());
        tests.put("TestStreaming", new com.redhat.ni.streaming.TestStreaming());
        tests.put("TestStreamingChunkRotation", new com.redhat.ni.streaming.TestStreamingChunkRotation());
        tests.put("TestStreamingEventCount",new com.redhat.ni.streaming.TestStreamingEventCount());
        tests.put("StressTest",new com.redhat.ni.streaming.TestStress());
    }
}
