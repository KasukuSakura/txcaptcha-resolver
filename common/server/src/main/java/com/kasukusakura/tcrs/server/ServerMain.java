/*
 * Copyright 2021-2022 KasukuSakura Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/KasukuSakura/txcaptcha-resolver/blob/main/LICENSE
 */

package com.kasukusakura.tcrs.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import java.util.function.Supplier;

public class ServerMain {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("java -jar txcaptcha-resolver-server.jar <port> [addr]");
            System.exit(1);
        }
        int port = Integer.parseInt(args[0]);

        ServerBootstrap bootstrap = new ServerBootstrap()
                .channel(NioServerSocketChannel.class)
                .group(new NioEventLoopGroup())
                .childHandler(new TCRSServerChannelInitializer() {
                    @Override
                    protected void debugMsg(ChannelHandlerContext ctx, Supplier<String> msg) {
                        System.out.println(ctx.channel() + " || " + msg.get());
                    }
                });

        ChannelFuture bootstrapFuture;
        if (args.length >= 2) {
            bootstrapFuture = bootstrap.bind(args[0], port);
        } else {
            bootstrapFuture = bootstrap.bind(port);
        }
        bootstrapFuture.awaitUninterruptibly();
        if (!bootstrapFuture.isSuccess()) {
            bootstrapFuture.cause().printStackTrace(System.err);
            System.exit(1);
        } else {
            System.out.println("Server started on " + bootstrapFuture.channel().localAddress());
        }
    }
}
