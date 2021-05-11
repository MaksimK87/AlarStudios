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

    {
        isLeader = new AtomicBoolean(false);
        isServerActive = new AtomicBoolean(false);
        isClientActive = new AtomicBoolean(false);
        isLeaderExists = new AtomicBoolean(false);
        isNewLeader = new AtomicBoolean(false);
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
     * Restart client after loosing connection with server
     */
    private void restartClient(InetAddress inetAddress) {
        client = new TcpClient(inetAddress);
        new Thread(client).start();
    }

    /**
     * Scanning network to find another servers
     */
    private CopyOnWriteArrayList<InetAddress> scanNetwork() throws InterruptedException {
        IpScanner.findNetworkIp();
        Thread.sleep(3000);
        return IpScanner.getAvailableIp();
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
            System.out.println("{Logic} AFTER INIT < client: " + isClientActive +
                    " > " + "< server: " + isServerActive + " > ");
            if (!isLeaderExists.get()) {

                System.out.println("{Logic} LEADER condition: { isLeader: " + isLeader.get() +
                        " } { isServerActive: " + isServerActive + " } { isClientActive: " + isClientActive
                        + " }");
            }
            if (isServerActive.get() && isLeader.get()) {
                pingClients();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }

    private void assignNewLeader() {
        isNewLeader.set(true);
        for (int i = availableIp.size() - 2; i >= 0; i--) {
            new TcpClient(availableIp.get(i));
        }

    }

    private InetAddress findLeaderIp(CopyOnWriteArrayList<InetAddress> networkLeaderCandidateChannels) {
        System.out.println("Leader candidate channel: " + networkLeaderCandidateChannels);
        Optional<InetAddress> leaderAddress = networkLeaderCandidateChannels
                .stream()
                .max(Comparator.comparing((address)
                        -> Byte.toUnsignedInt(address.getAddress()[3])));
        System.out.println("Leader IP address: " + leaderAddress);
        return leaderAddress.get();
    }

    /**
     * This method for creating leader. Priority for each participant generates randomly when application starts.
     * After launching several apps server which takes port first, waiting for other clients to establish connection
     * and choose leader. Initially leader will be chosen if server find two clients. Leader is app with maximum priority.
     * App that connected after selection just connects to current leader.
     */
    /*private void createLocalLeader() throws InterruptedException {
        if (isServerActive.get()) {
            while (ServerHandler.getLocalLeaderCandidateChannels().size() < MIN_APP_UNIT_FOR_LEADER_SELECTION) {
                System.out.println("{Logic} Waiting for more connections " + ServerHandler.getLocalLeaderCandidateChannels().size());
                Thread.sleep(2000);
            }
            Channel leaderChannel = findLocalLeader(ServerHandler.getLocalLeaderCandidateChannels());
            sendLocalLeaderNotification(leaderChannel);
        }
    }*/
    private void pingClients() {
        for (Channel channel : ServerHandler.getPingChannels()) {
            if (channel.isActive() || channel.isOpen()) {
                channel.writeAndFlush("Ping");
            } else {
                ServerHandler.getPingChannels().remove(channel);
            }
        }
    }

}