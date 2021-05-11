package com.alarstudios.task;

import com.alarstudios.task.ipscanner.IpScanner;
import com.alarstudios.task.logic.ApplicationLogic;

import java.util.concurrent.TimeUnit;

public class Main {
    public static void main(String[] args) throws InterruptedException {

        new ApplicationLogic().start();
        TimeUnit.MILLISECONDS.sleep(1000);
        //IpScanner.findNetworkIp();
    }
}
