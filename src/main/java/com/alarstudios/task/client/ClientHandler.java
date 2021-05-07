package com.alarstudios.task.client;

import com.alarstudios.task.logic.ApplicationLogic;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.CharsetUtil;

public class ClientHandler extends SimpleChannelInboundHandler<String> {

    public static final Object pauseLock = new Object();

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) {

        if (msg.contains("leader")) {
            ApplicationLogic.isLeader.set(true);
            System.out.println("{ClientHandler} I'm LEADER!!!");
            ctx.writeAndFlush("LeaderOK");
            return;
        }
        if (msg.contains("LeaderExists")) {
            System.out.println("{ClientHandler} Connected to LEADER!");
            ApplicationLogic.isLeaderExists.set(true);
            ApplicationLogic.priority = null;
            ctx.writeAndFlush("Pong");
            return;
        }

        if (msg.contains("Ping")) {
            //System.out.println(msg);
            ctx.writeAndFlush("Pong" + System.lineSeparator());
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        synchronized (pauseLock) {
            pauseLock.notify();
        }
        ApplicationLogic.isClientActive.set(true);
        if (ApplicationLogic.priority != null) {
            ctx.writeAndFlush(Unpooled.copiedBuffer(ApplicationLogic.priority, CharsetUtil.UTF_8));
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ApplicationLogic.isClientActive.set(false);
        cause.printStackTrace();
        ctx.close();
        new ApplicationLogic().start();
        System.out.println("Server connection has lost, application will restart!");
    }
}