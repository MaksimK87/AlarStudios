package com.alarstudios.task.server;

import com.alarstudios.task.logic.ApplicationLogic;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.CharsetUtil;

import java.util.concurrent.CopyOnWriteArrayList;

public class ServerHandler extends SimpleChannelInboundHandler<String> {

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
        if (msg.contains("newLeader")) {
            ApplicationLogic.isNewLeaderExists.set(true);
            ApplicationLogic.isLeader.set(false);
            System.out.println("{ClientHandler} New leader exists!!!");
            ApplicationLogic.stopServer();
            ctx.close();
            return;
        }
        if (msg.contains("Pong")) {
            ctx.writeAndFlush("Ping" + System.lineSeparator());
        }

    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ApplicationLogic.isLeader.set(false);
        //logging cause
        ctx.close();
    }

    public static CopyOnWriteArrayList<Channel> getPingChannels() {
        return pingChannels;
    }
}
