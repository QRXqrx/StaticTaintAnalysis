package com.wxy.wala.test;

public class StaticDataflow {
    private static int setx() {
        return 10;
    }

    private static int sety() {
        return 5;
    }

    public static void main(String[] args) {
        int i = setx();
        int j = sety();
        int x = 0;
        if (i == 10) {
            x = j - i;
        } else {
            x = i + j;
        }
        int y = 0;
        if (j == 5) {
            y = j - 2 * i;
        } else {
            y = i + 2 * j;
        }
        System.out.println(x + y);
    }
}