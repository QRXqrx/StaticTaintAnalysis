package com.wxy.wala.main;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;

public class Config {
    private HashSet<String> sink = new HashSet<>();
    private HashSet<String> source = new HashSet<>();
    private String filepath;
    public String mainclass;

    public Config(String filepath) {
        this.filepath = filepath;
        parse();
    }

    private boolean parse() {
        File file = new File(filepath);
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(file));
            String temp = null;
            while ((temp = reader.readLine()) != null) {
                String[] d = temp.split(" ");
                if (d[0].equals("SINK")) {
                    sink.add(d[1]);
                } else if (d[0].equals("SOURCE")) {
                    source.add(d[1]);
                } else if (d[0].equals("MAIN")) {
                    mainclass = d[1];
                }
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return true;
    }

    public boolean isSink(String sig) {
        return sink.contains(sig);
    }

    public boolean isSource(String sig) {
        return source.contains(sig);
    }

    public void Check() {
        for (String s : sink) {
            System.out.println(s);
        }
        for (String s : source) {
            System.out.println(s);
        }
    }

}
