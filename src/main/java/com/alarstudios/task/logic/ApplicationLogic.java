package com.alarstudios.task.logic;

import com.alarstudios.task.client.ClientHandler;
import com.alarstudios.task.client.TcpClient;
import com.alarstudios.task.server.ServerHandler;
import com.alarstudios.task.server.TcpServer;
import io.netty.channel.Channel;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class ApplicationLogic extends Thread {

    private static final int PRIORITY_RANGE = 1_000_000;
    private static final int MIN_APP_UNIT_FOR_LEADER_SELECTION = 2;

    private static TcpClient client;
    private static TcpServer server;

    public static String priority;

    public static AtomicBoolean isLeaderExists;
    public static AtomicBoolean isLeader;
    public static AtomicBoolean isServerActive;
    public static AtomicBoolean isClientActive;
    public static AtomicBoolean isDelivered;

    {
        isLeader = new AtomicBoolean(false);
        isServerActive = new AtomicBoolean(false);
        isClientActive = new AtomicBoolean(false);
        isDelivered = new AtomicBoolean(false);
        isLeaderExists = new AtomicBoolean(false);
        priority = generatePriority();
    }

    /**
     * First launch of server
     */
    public void initServer() {
        server = new TcpServer();
        new Thread(server).start();
    }

    /**
     * First launch of client
     */
    public void initClient() {
        client = new TcpClient();
        new Thread(client).start();
    }

    /**
     * Restart client after loosing connection with server
     */
    public void restartClient() {
        ApplicationLogic.priority = null;
        client = new TcpClient();
        new Thread(client).start();
    }

    /**
     * This method stops server in case of leader is chosen
     */
    public static void stopServer() {
        ServerHandler.getPingChannels().clear();
        server.stopServer();
    }


    /**
     * Main logic of application.
     * First launch with several apps - choosing leader, then client with max priority gets "leader notification"
     * and sends response, that message was accepted to active server. Server is stops; Then "leader app starts
     * an own server, all clients waiting for reconnecting. Finally server starts pinging another participants"
     * In case of server - leader stops pinging (clients lose connection with server) - clients initiate
     * new ApplicationLogic thread for new reconnection.
     */
    public void run() {
        try {
            initServer();
            synchronized (TcpServer.pauseLock) {
                TcpServer.pauseLock.wait();
            }
            initClient();
            synchronized (ClientHandler.pauseLock) {
                ClientHandler.pauseLock.wait();
            }
            System.out.println("{Logic} AFTER INIT < client: " + isClientActive +
                    " > " + "< server: " + isServerActive + " > < priority: " + priority + " >");
            if (!isLeaderExists.get()) {
                createLeader();
                while (isClientActive.get()) {
                    Thread.sleep(300);
                }
                if (isLeader.get()) {
                    initServer();
                    synchronized (TcpServer.pauseLock) {
                        TcpServer.pauseLock.wait();
                    }
                    while (!isServerActive.get()) {
                        Thread.sleep(300);
                    }
                }
                restartClient();
                synchronized (ClientHandler.pauseLock) {
                    ClientHandler.pauseLock.wait();
                }
                System.out.println("{Logic} LEADER condition: { isLeader: " + isLeader.get() +
                        " } { isServerActive: " + isServerActive + " } { isClientActive: " + isClientActive
                        + " } { Current priority: " + priority + " }");
            }
            if (isServerActive.get()) {
                pingClients();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * This method for creating leader. Priority for each participant generates randomly when application starts.
     * After launching several apps server which takes port first, waiting for other clients to establish connection
     * and choose leader. Initially leader will be chosen if server find two clients. Leader is app with maximum priority.
     * App that connected after selection just connects to current leader.
     */
    private void createLeader() throws InterruptedException {
        if (isServerActive.get()) {
            while (ServerHandler.getLeaderCandidateChannels().size() < MIN_APP_UNIT_FOR_LEADER_SELECTION) {
                System.out.println("{Logic} Waiting for more connections " + ServerHandler.getLeaderCandidateChannels().size());
                Thread.sleep(2000);
            }
            Channel leaderChannel = findLeader(ServerHandler.getLeaderCandidateChannels());
            sendLeaderNotification(leaderChannel);
        }
    }

    private Channel findLeader(ConcurrentHashMap<Channel, Integer> channels) {
        Optional<Map.Entry<Channel, Integer>> maxPriorityChannel = channels.entrySet().stream()
                .max(Comparator.comparing((Map.Entry<Channel, Integer> entry1) -> entry1.getValue()));
        System.out.println("{Logic} Max priority: " + maxPriorityChannel.get().getValue());
        return maxPriorityChannel.get().getKey();
    }

    private void sendLeaderNotification(Channel leaderChannel) throws InterruptedException {
        leaderChannel.writeAndFlush("leader");
        synchronized (ServerHandler.pauseLock) {
            ServerHandler.pauseLock.wait();
        }
        ServerHandler.getLeaderCandidateChannels().clear();
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

    static String generatePriority() {
        return String.valueOf(new Random().nextInt(PRIORITY_RANGE));
    }

}