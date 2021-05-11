package com.alarstudios.task.server;

import com.alarstudios.task.logic.ApplicationLogic;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;

import java.net.InetAddress;

public class TcpServer implements Runnable {

    public static volatile Object pauseLock = new Object();
    private static final int PORT = 53200;
    private ChannelFuture channelFuture;

    public void run() {
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            InetAddress inetAddress = InetAddress.getLocalHost();
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap.group(bossGroup, workerGroup);
            serverBootstrap.channel(NioServerSocketChannel.class);
            serverBootstrap.childHandler(new ChannelInitializer<SocketChannel>() {

                @Override
                protected void initChannel(SocketChannel socketChannel) {
                    socketChannel.pipeline().addLast(new StringEncoder()
                            , new StringDecoder()
                            , new ServerHandler());
                }
            });
            serverBootstrap.childOption(ChannelOption.SO_KEEPALIVE, true);
            ChannelFuture channelFuture = serverBootstrap.bind(inetAddress, PORT).sync();
            this.channelFuture = channelFuture;
            ApplicationLogic.isServerActive.set(true);
            synchronized (pauseLock) {
                pauseLock.notify();
            }
            channelFuture.channel().closeFuture().sync();
        } catch (Exception e) {
            // Logging exception
        } finally {
            try {
                ApplicationLogic.isServerActive.set(false);
                workerGroup.shutdownGracefully().sync();
                bossGroup.shutdownGracefully().sync();
                synchronized (pauseLock) {
                    pauseLock.notify();
                }
                System.out.println("{TcpServer} server was stopped");
            } catch (InterruptedException e) {
                // Logging exception
            }
        }
    }

    public void stopServer() {
        if (channelFuture != null) {
            channelFuture.channel().close();
            ApplicationLogic.isServerActive.set(false);
        }
    }
}
