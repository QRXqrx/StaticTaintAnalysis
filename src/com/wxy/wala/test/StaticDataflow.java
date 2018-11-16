package com.wxy.wala.test;

public class StaticDataflow {
    static int f;

    static int g;

    public static void test1() {
        f = 3;
        f = 3;
    }

    public static void test2() {
        f = 4;
        g = 3;
        if (f == 5) {
            g = 2;
        } else {
            g = 7;
        }
    }

    public static void main(String[] args) {
        testInterproc();
    }

    private static void testInterproc() {
        int a = 5;
        f = 3;
        m();
        for (int i = 0; i < f + a; i++) {
            g = 4;
            m();
        }

    }

    private static void m() {
        f = 2;
    }
}

