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
    private static final ConcurrentHashMap<Channel, Integer> LeaderCandidateChannels = new ConcurrentHashMap<>();
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
        if (msg.contains("LeaderOK")) {
            ApplicationLogic.isDelivered.set(true);
            ctx.close();
            ApplicationLogic.stopServer();
            synchronized (pauseLock) {
                pauseLock.notify();
            }
            return;
        }
        if (msg.contains("Pong")) {
            ctx.writeAndFlush("Ping" + System.lineSeparator());
            return;
        }
        if (isInteger(msg)) {
            if (ApplicationLogic.isLeader.get()) {
                ctx.writeAndFlush("LeaderExists");
                return;
            }
            LeaderCandidateChannels.put(ctx.channel(), Integer.valueOf(msg));
            ctx.flush();
            System.out.println("{ServerHand} Current channelMap size before elections: " + LeaderCandidateChannels.size());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ApplicationLogic.isLeader.set(false);
        cause.printStackTrace();
        ctx.close();
    }

    private boolean isInteger(String str) {
        if (str == null) {
            return false;
        }
        int length = str.length();
        if (length == 0) {
            return false;
        }
        int i = 0;
        if (str.charAt(0) == '-') {
            if (length == 1) {
                return false;
            }
            i = 1;
        }
        for (; i < length; i++) {
            char c = str.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    public static ConcurrentHashMap<Channel, Integer> getLeaderCandidateChannels() {
        return LeaderCandidateChannels;
    }

    public static CopyOnWriteArrayList<Channel> getPingChannels() {
        return pingChannels;
    }
}
