package com.alarstudios.task.client;

import com.alarstudios.task.logic.ApplicationLogic;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.Delimiters;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

public class TcpClient implements Runnable {

    private static final int PORT = 53200;

    public void run() {
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            InetAddress inetAddress = InetAddress.getLocalHost();
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
        } catch (InterruptedException | UnknownHostException e) {
            // Logging exception
            ApplicationLogic.isClientActive.set(false);
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
