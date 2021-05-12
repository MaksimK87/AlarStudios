package com.alarstudios.task.logic;

import com.alarstudios.task.client.ClientHandler;
import com.alarstudios.task.client.TcpClient;
import com.alarstudios.task.ipscanner.IpScanner;
import com.alarstudios.task.server.ServerHandler;
import com.alarstudios.task.server.TcpServer;
import io.netty.channel.Channel;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class ApplicationLogic extends Thread {

    private static TcpClient client;
    private static TcpServer server;
    private static CopyOnWriteArrayList<InetAddress> availableIp;


    public static AtomicBoolean isLeaderExists;
    public static AtomicBoolean isLeader;

    public static AtomicBoolean isServerActive;
    public static AtomicBoolean isClientActive;
    public static AtomicBoolean isNewLeader;
    public static AtomicBoolean isNewLeaderExists;

    {
        isLeader = new AtomicBoolean(false);
        isServerActive = new AtomicBoolean(false);
        isClientActive = new AtomicBoolean(false);
        isLeaderExists = new AtomicBoolean(false);
        isNewLeader = new AtomicBoolean(false);
        isNewLeaderExists = new AtomicBoolean(false);
    }

    /**
     * Main logic of application.
     * After scanning network, there are list of available ip addresses, for connection and ping.
     * For choosing leader, is taken server with max last number in ip address if new connected server has max
     * number and leader already exists with another ip and has less last number - leader is re-elected again;
     * Finally server starts pinging another participants.
     */
    public void run() {
        try {
            initServer();
            synchronized (TcpServer.pauseLock) {
                TcpServer.pauseLock.wait();
            }
            availableIp = scanNetwork();
            Thread.sleep(5000);
            InetAddress leaderAddress = findLeaderIp(IpScanner.getAvailableIp());
            if (InetAddress.getLocalHost().equals(leaderAddress) && availableIp.size() > 1) {
                assignNewLeader();
            }
            initClient(leaderAddress);
            synchronized (ClientHandler.pauseLock) {
                ClientHandler.pauseLock.wait();
            }
            System.out.println("{Logic} AFTER INIT < client: " + isClientActive
                    + " > " + "< server: " + isServerActive + " > ");
            if (isServerActive.get() && isLeader.get()) {
                pingClients();
            }
        } catch (InterruptedException | UnknownHostException e) {
            //Logging
        }
    }

    /**
     * Scanning network to find another servers.
     */
    private CopyOnWriteArrayList<InetAddress> scanNetwork() throws InterruptedException {
        IpScanner.findNetworkIp();
        Thread.sleep(2000);
        return IpScanner.getAvailableIp();
    }

    /**
     * If local ip address has max "priority" after scanning network and getting available ip addresses and it's not
     * a leader - alert "NewLeader" sends to previous leader and then all nodes will restart and connect to current leader
     */
    private void assignNewLeader() throws InterruptedException {
        isNewLeader.set(true);
        Thread.sleep(400);
        System.out.println("New leader created! " + isNewLeader.get());
        int i = availableIp.size() - 2;
        new Thread(new TcpClient(availableIp.get(i))).start();
        synchronized (TcpClient.ipConnectionLock) {
            TcpClient.ipConnectionLock.wait();
        }
    }

    private InetAddress findLeaderIp(CopyOnWriteArrayList<InetAddress> networkLeaderCandidateChannels) {
        Optional<InetAddress> leaderAddress =
                networkLeaderCandidateChannels
                        .stream()
                        .max(Comparator.comparing((address)
                                -> Byte.toUnsignedInt(address.getAddress()[3])));
        System.out.println("Leader IP address: " + leaderAddress);
        return leaderAddress.get();
    }

    private void pingClients() {
        for (Channel channel : ServerHandler.getPingChannels()) {
            if (channel.isActive() || channel.isOpen()) {
                channel.writeAndFlush("Ping");
            } else {
                ServerHandler.getPingChannels().remove(channel);
            }
        }
    }

    /**
     * First launch of server
     */
    private void initServer() {
        server = new TcpServer();
        new Thread(server).start();
    }

    /**
     * First launch of client
     */
    private void initClient(InetAddress inetAddress) {
        client = new TcpClient(inetAddress);
        new Thread(client).start();
    }

    /**
     * This method stops server in case of new leader is chosen
     */
    public static void stopServer() {
        ServerHandler.getPingChannels().clear();
        server.stopServer();
    }
}