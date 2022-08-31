package com.redhat.ni.tester;

import java.io.IOException;

public interface Test {
    default void test() throws Exception {
        System.out.println("Test is unimplemented" + this.getClass().getName());
    }

    String getName();
}