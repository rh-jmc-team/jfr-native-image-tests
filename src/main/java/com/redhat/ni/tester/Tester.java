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
import com.redhat.ni.events.TestJavaMonitorEnter;
import main.java.com.redhat.ni.events.TestJavaMonitorWaitNotifyAll;
import main.java.com.redhat.ni.events.TestJavaMonitorWaitTimeout;
import main.java.com.redhat.ni.events.TestThreadSleep;

import java.util.HashMap;
import com.redhat.ni.tester.Test;

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

        if (all) {
            Test test;
            for (String testName : tests.keySet()) {
                test = tests.get(testName);
                if (test != null) {
                    System.out.println(test.getName());
                    try {
                        test.test();
                    } catch (Exception e) {
                        System.out.println(e.getMessage());
                        e.printStackTrace();
                    }
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
        tests.put("TestJavaMonitorEnter", new TestJavaMonitorEnter());
        tests.put("TestJavaMonitorWait", new TestJavaMonitorWait());
        tests.put("TestJavaMonitorWaitInterrupt", new TestJavaMonitorWaitInterrupt());
        tests.put("TestJavaMonitorWaitNotifyAll", new TestJavaMonitorWaitNotifyAll());
        tests.put("TestJavaMonitorWaitTimeout", new TestJavaMonitorWaitTimeout());
        tests.put("TestThreadSleep", new TestThreadSleep());
    }
}
