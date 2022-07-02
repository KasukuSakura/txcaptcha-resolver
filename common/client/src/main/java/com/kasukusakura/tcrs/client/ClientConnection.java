/*
 * Copyright 2021-2022 KasukuSakura Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/KasukuSakura/txcaptcha-resolver/blob/main/LICENSE
 */

package com.kasukusakura.tcrs.client;


import com.kasukusakura.tcrs.network.PkgCodec;
import com.kasukusakura.tcrs.network.packets.*;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.timeout.ReadTimeoutHandler;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;

public abstract class ClientConnection {
    protected PkgCodec codec = new PkgCodec();
    protected Channel bindChannel;
    protected Queue<Object> pendingPacketsToSend = new ConcurrentLinkedDeque<>();
    protected boolean disconnected;

    protected void onTickReceived(byte[] ticket, byte[] fastcode) {
    }

    protected void onFastCodeReceived(byte[] fastcode) {
    }

    protected void sendPacket(Object pkg) {
        if (disconnected) {
            pendingPacketsToSend.clear();
            return;
        }
        if (bindChannel != null && bindChannel.isOpen() && bindChannel.isActive()) {
            if (bindChannel.eventLoop().inEventLoop()) {
                while (true) {
                    Object next = pendingPacketsToSend.poll();
                    if (next == null) break;
                    bindChannel.write(next);
                }

                if (pkg != null) {
                    bindChannel.writeAndFlush(pkg);
                } else bindChannel.flush();
            } else if (pkg != null || !pendingPacketsToSend.isEmpty()) {
                bindChannel.eventLoop().execute(() -> {
                    while (true) {
                        Object next = pendingPacketsToSend.poll();
                        if (next == null) break;
                        bindChannel.write(next);
                    }

                    if (pkg == null) {
                        bindChannel.flush();
                    } else {
                        bindChannel.writeAndFlush(pkg);
                    }
                });
            }
        } else {
            pendingPacketsToSend.add(pkg);
        }
    }

    public void sendTicketQueryRequest(byte[] fastcode) {
        sendPacket(PkgQueryProcessCodeStatus.Req.req(fastcode));
    }

    public void completeTicket(byte[] fastcode, byte[] ticket) {
        sendPacket(PkgNewProcessCode.Complete.complete(fastcode, ticket));
    }

    public void keepalive() {
        sendPacket(PkgKeepAlive.INSTANCE);
    }

    public void sendNewFastCodeReq() {
        sendPacket(PkgNewProcessCode.Req.INSTANCE);
    }

    public void sendProcessCodeInfoUpdate(int captchaType, byte[] cpatchaData, byte[] fastcode) {
        sendPacket(PkgProcessCodeInfo.Update.update(captchaType, cpatchaData, fastcode));
    }

    public void sendProcessCodeRefresh(byte[] fastcode) {
        sendPacket(PkgProcessCodeInfo.Refresh.refresh(fastcode));
    }

    public void fetchProcessCodeInfo(byte[] fastcode) {
        sendPacket(PkgProcessCodeInfo.Query.query(fastcode));
    }

    protected void onReceivedProcessCodeInfo(PkgProcessCodeInfo.Response response) {
    }


    protected Bootstrap basicBootstrap() {
        return new Bootstrap()
                .handler(new ClientChannelInitializer());
    }

    protected void bindConnection(Channel channel) {
        this.bindChannel = channel;
        flush();
    }

    public void flush() {
        if (disconnected) {
            pendingPacketsToSend.clear();
            return;
        }
        sendPacket(null);
    }

    public void disconnect() {
        disconnected = true;
        if (bindChannel != null) {
            bindChannel.close();
            bindChannel = null;
        }
        pendingPacketsToSend.clear();
        onDisconnect();
    }

    protected void onDisconnect() {
    }


    @SuppressWarnings("UnnecessaryReturnStatement")
    protected void handlePacket(ChannelHandlerContext ctx, Packet msg) {
        if (msg == PkgKeepAlive.INSTANCE) return;
        if (msg instanceof PkgNewProcessCode.Rsp) {
            onFastCodeReceived(((PkgNewProcessCode.Rsp) msg).fastcode);
            return;
        }
        if (msg instanceof PkgQueryProcessCodeStatus.Rsp) {
            PkgQueryProcessCodeStatus.Rsp rsp = (PkgQueryProcessCodeStatus.Rsp) msg;
            if (rsp.ticket != null) {
                onTickReceived(rsp.ticket, rsp.fastcode);
            }
            return;
        }
        if (msg instanceof PkgProcessCodeInfo.Response) {
            onReceivedProcessCodeInfo((PkgProcessCodeInfo.Response) msg);
            return;
        }
    }

    protected class ClientChannelInitializer extends ChannelInitializer<Channel> {
        @Override
        protected void initChannel(Channel ch) throws Exception {
            ch.pipeline()
                    .addLast("timeout", new ReadTimeoutHandler(10000, TimeUnit.MILLISECONDS))
                    .addLast("encoder", codec.getEncoder())
                    .addLast("decoder", codec.getDecoder())
                    .addLast("processor", new ClientChannelHandler());
            channelInit(ch);
        }
    }

    protected void channelInit(Channel ch) throws Exception {
    }

    protected void logError(Throwable throwable, ChannelHandlerContext ctx) {
        throwable.printStackTrace(System.err);
    }

    protected class ClientChannelHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof Packet) {
                handlePacket(ctx, (Packet) msg);
                return;
            }
            super.channelRead(ctx, msg);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            logError(cause, ctx);
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            bindConnection(ctx.channel());
            ctx.fireChannelActive();
        }
    }
}
