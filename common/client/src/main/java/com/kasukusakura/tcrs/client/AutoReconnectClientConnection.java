/*
 * Copyright 2021-2022 KasukuSakura Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/KasukuSakura/txcaptcha-resolver/blob/main/LICENSE
 */

package com.kasukusakura.tcrs.client;

import com.kasukusakura.tcrs.network.packets.PkgKeepAlive;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.ScheduledFuture;

import java.net.SocketAddress;
import java.util.concurrent.TimeUnit;

public abstract class AutoReconnectClientConnection extends ClientConnection {
    protected EventLoopGroup eventLoopGroup;
    protected boolean shutdownEventLoopGroupWhenDisconnect;
    protected Class<? extends Channel> channelType = NioSocketChannel.class;
    protected SocketAddress address;


    public void connect(SocketAddress address) {
        this.address = address;
        reconnect();
    }

    protected void reconnect() {
        if (disconnected) {
            throw new RuntimeException("Connection was closed.");
        }

        Channel c = bindChannel;
        if (c != null && c.isActive() && c.isOpen()) {
            return;
        }
        this.bindChannel = null;
        if (eventLoopGroup == null) {
            eventLoopGroup = new NioEventLoopGroup();
            shutdownEventLoopGroupWhenDisconnect = true;
        }
        basicBootstrap()
                .channel(channelType)
                .group(eventLoopGroup)
                .connect(address)
                .addListener(cf -> {
                    if (!cf.isSuccess()) {
                        logError(cf.cause(), null);
                        eventLoopGroup.schedule(() -> {
                            if (!disconnected) reconnect();
                        }, 1000, TimeUnit.MILLISECONDS);
                    } else {
                        ScheduledFuture<?> future = eventLoopGroup.scheduleWithFixedDelay(() -> sendPacket(PkgKeepAlive.INSTANCE), 1000, 1000, TimeUnit.MILLISECONDS);
                        Channel channel = ((ChannelFuture) cf).channel();
                        channel.closeFuture().addListener($$$$ -> {
                            future.cancel(true);
                            if (disconnected) return;
                            eventLoopGroup.schedule(() -> {
                                if (!disconnected) reconnect();
                            }, 1000, TimeUnit.MILLISECONDS);
                        });
                    }
                });
    }

    @Override
    protected void onDisconnect() {
        if (shutdownEventLoopGroupWhenDisconnect && eventLoopGroup != null) {
            eventLoopGroup.shutdownGracefully();
            eventLoopGroup = null;
        }
    }
}
