package com.alarstudios.task.ipscanner;

import com.alarstudios.task.client.TcpClient;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.CopyOnWriteArrayList;

public class IpScanner {

    private static final CopyOnWriteArrayList<InetAddress> availableIp = new CopyOnWriteArrayList<>();
    public static Object ipLock = new Object();

    public static void findNetworkIp() {
        availableIp.clear();
        final byte[] ip;
        try {
            ip = InetAddress.getLocalHost().getAddress();
        } catch (Exception e) {
            return;
        }

        for (int i = 1; i <= 254; i++) {
            final int j = i;
            new Thread(() -> {
                try {
                    ip[3] = (byte) j;
                    InetAddress address = InetAddress.getByAddress(ip);
                    String output = address.toString().substring(1);
                    if (address.isReachable(5000)) {
                        if (checkConnection(address)) {
                            availableIp.add(address);
                            //System.out.println("Available IP addr: " + address);
                        }
                        // System.out.println(output + " is on the network");
                    } else {
                        // System.out.println("Not Reachable: "+output);
                    }
                } catch (Exception e) {
                    // e.printStackTrace();
                }
            }).start();
        }
    }

    public static CopyOnWriteArrayList<InetAddress> getAvailableIp() {
        Collections.sort(availableIp, Comparator.comparingInt(i -> Byte.toUnsignedInt(i.getAddress()[3])));
        return availableIp;
    }

    private static boolean checkConnection(InetAddress inetAddress) {
        Socket socket;
        try {
            socket = new Socket(inetAddress, TcpClient.PORT);
            socket.close();
            // System.out.println("New socket connection in network");
        } catch (IOException e) {
            return false;
        }
        return true;
    }
}
