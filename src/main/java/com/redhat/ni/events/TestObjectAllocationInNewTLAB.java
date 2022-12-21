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

package com.redhat.ni.events;

import com.redhat.ni.tester.Test;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedObject;
import jdk.jfr.consumer.RecordedThread;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * This test may not work with Hotspot. This is because Hotspot uses dynamic resizing of TLABs, whereas SVM doesn't.
 */
public class TestObjectAllocationInNewTLAB extends Test {
    static final int KILO = 1024;
    static final int DEFAULT_ALIGNED_HEAP_CHUNK_SIZE = KILO * KILO; // the default size for serial and epsilon GC in SVM.

    public static Helper helper = null;
    public static byte[] byteArray = null;
    @Override
    public void test() throws Exception {

        Recording recording = new Recording();
        recording.enable("jdk.ObjectAllocationInNewTLAB");
        try {
            recording.start();

            // These arrays must result in exceeding the large array threshold, resulting in new TLABs.
            byte [] bigByte = new byte[2 * DEFAULT_ALIGNED_HEAP_CHUNK_SIZE];
            Arrays.fill(bigByte, (byte) 0);

            char [] bigChar = new char[DEFAULT_ALIGNED_HEAP_CHUNK_SIZE]; // Using char, so it's the same size as bigByte.
            Arrays.fill(bigChar, 'm');

            // Try to exhaust TLAB with arrays
            for (int i = 0; i < DEFAULT_ALIGNED_HEAP_CHUNK_SIZE/KILO; i++){
                byteArray = new byte[KILO];
                Arrays.fill(byteArray, (byte) 0);
            }

            // Try to exhaust TLAB with instances
            for (int i = 0; i < DEFAULT_ALIGNED_HEAP_CHUNK_SIZE; i++){
                helper = new Helper();
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            recording.stop();
        }

        List<RecordedEvent> events = getEvents(recording, getName());

        boolean foundBigByte = false;
        boolean foundSmallByte = false;
        boolean foundBigChar = false;
        boolean foundInstance = false;
        for (RecordedEvent event : events) {
            String eventThread = event.<RecordedThread> getValue("eventThread").getJavaName();
            if (!eventThread.equals("main")) {
                continue;
            }
            if (event.<Long>getValue("allocationSize").longValue() >= (2 * DEFAULT_ALIGNED_HEAP_CHUNK_SIZE) && event.<Long>getValue("tlabSize").longValue() >= (2 * DEFAULT_ALIGNED_HEAP_CHUNK_SIZE)) { // >= tp account for size of reference
                // verify previous owner
                if(event.<RecordedClass> getValue("objectClass").getName().equals(char[].class.getName())) {
                    foundBigChar = true;
                } else if (event.<RecordedClass> getValue("objectClass").getName().equals(byte[].class.getName())) {
                    foundBigByte = true;
                }
            } else if (event.<Long>getValue("allocationSize").longValue() >= KILO
                    && event.<Long>getValue("tlabSize").longValue() == (DEFAULT_ALIGNED_HEAP_CHUNK_SIZE)
                    && event.<RecordedClass> getValue("objectClass").getName().equals(byte[].class.getName())) {

                     foundSmallByte = true;

            } else if (event.<Long>getValue("tlabSize").longValue() == (DEFAULT_ALIGNED_HEAP_CHUNK_SIZE)
                    && event.<RecordedClass> getValue("objectClass").getName().equals(Helper.class.getName())) {
                foundInstance = true;
            }
        }
        if(!foundBigChar || !foundBigByte || !foundSmallByte || !foundInstance){
            throw new Exception("Expected events not found");
        }
    }

    /**
     * This class is only needed to provide a unique name in the event's "objectClass" field that we check.
     */
    static class Helper {
        int testField;
    }
    @Override
    public String getName() {
        return "jdk.ObjectAllocationInNewTLAB";
    }
    @Override
    public String getTestName(){
        return getName();
    }
}
