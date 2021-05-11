package com.alarstudios.task.server;

import com.alarstudios.task.logic.ApplicationLogic;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.CharsetUtil;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class ServerHandler extends SimpleChannelInboundHandler<String> {

    public static final Object pauseLock = new Object();
    private static final ConcurrentHashMap<Channel, Integer> NetworkLeaderCandidateChannels = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Channel, Integer> LocalLeaderCandidateChannels = new ConcurrentHashMap<>();
    private static CopyOnWriteArrayList<Channel> pingChannels = new CopyOnWriteArrayList<>();

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) {
        if (ApplicationLogic.isLeader.get()) {
            ctx.writeAndFlush(Unpooled.copiedBuffer("LeaderExists", CharsetUtil.UTF_8));
        }
        pingChannels.add(ctx.channel());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) {
        if (msg.contains("leader")) {
            ApplicationLogic.isLeader.set(true);
            System.out.println("{ClientHandler} I'm LEADER!!!");
            ctx.writeAndFlush("LeaderExists");
            return;
        }

        if (msg.contains("Pong")) {
            ctx.writeAndFlush("Ping" + System.lineSeparator());
            return;
        }
        if (msg.contains("newLeader")) {
            ApplicationLogic.isLeader.set(false);
            System.out.println("{ClientHandler} New leader exist!!!");
            ctx.flush();
            ApplicationLogic.stopServer();
            return;
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ApplicationLogic.isLeader.set(false);
        cause.printStackTrace();
        ctx.close();
    }

    public static CopyOnWriteArrayList<Channel> getPingChannels() {
        return pingChannels;
    }
}
