package com.wxy.wala.test;

public class Main {
    public static void main(String[] args) {
        String data = System.getProperty(args[0]); // SOURCE
        Test test = new Test(data);
        //String d1 = test.data;
        String d1 = test.processData(Boolean.parseBoolean(args[1]));
        System.out.println(d1); // SINK
    }
}
