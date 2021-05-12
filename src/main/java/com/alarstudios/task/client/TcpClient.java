package com.alarstudios.task.client;

import com.alarstudios.task.logic.ApplicationLogic;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;

import java.net.InetAddress;
import java.net.InetSocketAddress;

public class TcpClient implements Runnable {

    public static final Object ipConnectionLock = new Object();
    public static final int PORT = 53200;
    private InetAddress inetAddress;


    public TcpClient(InetAddress inetAddress) {
        this.inetAddress = inetAddress;
    }

    public void run() {
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            // InetAddress inetAddress = InetAddress.getLocalHost();
            Bootstrap clientBootstrap = new Bootstrap();
            clientBootstrap.group(group);
            clientBootstrap.channel(NioSocketChannel.class);
            clientBootstrap.option(ChannelOption.SO_KEEPALIVE, true);
            clientBootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 30000);
            clientBootstrap.remoteAddress(new InetSocketAddress(inetAddress, PORT));
            ApplicationLogic.isClientActive.set(true);
            clientBootstrap.handler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel socketChannel) {
                    socketChannel.pipeline().addLast(
                            new StringEncoder(),
                            new StringDecoder(),
                            new ClientHandler());
                }
            });
            ChannelFuture channelFuture = clientBootstrap.connect().sync();
            channelFuture.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            synchronized (ipConnectionLock) {
                ipConnectionLock.notify();
            }
            // Logging exception
        } finally {
            try {
                ApplicationLogic.isClientActive.set(false);
                group.shutdownGracefully().sync();
            } catch (InterruptedException e) {
                // Logging exception
            }
        }
    }
}
