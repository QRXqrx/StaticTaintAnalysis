package com.wxy.wala.test;

public class StaticDataflow {

    private int f;

    private int g;

    private void test1() {
        f = 3;
        g = 3;
    }

    private void test2(int i) {
        f = 4;
        g = 3;
        if (i == 5) {
            g = 2;
        } else {
            g = 7;
        }
    }

    public static void main(String[] args) {
        StaticDataflow s = new StaticDataflow();
        s.testInterproc();
    }

    private void testInterproc() {
        f = m();
        test1();
        g = 4;
        test2(5);
    }

    private int m() {
        int i = 3;
        return i;
    }
}