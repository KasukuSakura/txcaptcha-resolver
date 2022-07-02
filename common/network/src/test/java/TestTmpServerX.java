/*
 * Copyright 2021-2022 KasukuSakura Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/KasukuSakura/txcaptcha-resolver/blob/main/LICENSE
 */

import com.kasukusakura.tcrs.client.AutoReconnectClientConnection;
import com.kasukusakura.tcrs.network.packets.PkgProcessCodeInfo;
import com.kasukusakura.tcrs.server.TCRSServerChannelInitializer;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import java.net.InetSocketAddress;
import java.util.function.Supplier;

public class TestTmpServerX {
    public static void main(String[] args) throws Throwable {
        EventLoopGroup eventLoopGroup = new NioEventLoopGroup();
        int port = 12445;
        Channel serverChannel = new ServerBootstrap()
                .channel(NioServerSocketChannel.class)
                .group(eventLoopGroup)
                .childHandler(new TCRSServerChannelInitializer() {
                    @Override
                    protected void debugMsg(ChannelHandlerContext ctx, Supplier<String> msg) {
                        System.out.println(ctx.channel() + " || " + msg.get());
                    }
                })
                .bind(port)
                .awaitUninterruptibly()
                .channel();

        class MyConnection extends AutoReconnectClientConnection {
            byte[] myticket;
            byte[] mycode;

            @Override
            protected void onFastCodeReceived(byte[] fastcode) {
                mycode = fastcode;
                System.out.println("CLIENT: Received fast code: " + new String(fastcode));
            }

            @Override
            protected void onTickReceived(byte[] ticket, byte[] fastcode) {
                myticket = ticket;
                System.out.println("CLIENT: Received ticket: " + new String(ticket));
            }

            @Override
            protected void onReceivedProcessCodeInfo(PkgProcessCodeInfo.Response response) {
                System.out.println("CLIENT: ProcessCodeInfo: type=" + response.captchaType + ", data=" + (response.captchaData != null ? new String(response.captchaData) : "<null>"));
            }
        }

        MyConnection connection = new MyConnection();
        connection.connect(new InetSocketAddress("::1", port));

        connection.sendNewFastCodeReq();
        Thread.sleep(1000L);
        connection.sendProcessCodeInfoUpdate(50, null, connection.mycode);
        connection.fetchProcessCodeInfo(connection.mycode);
        connection.sendTicketQueryRequest(connection.mycode);
        Thread.sleep(1000L);
        connection.sendTicketQueryRequest(connection.mycode);
        Thread.sleep(1000L);
        connection.sendTicketQueryRequest(connection.mycode);
        Thread.sleep(1000L);


        connection.completeTicket(connection.mycode, "AAAAAAAAAaewjaoiwTicket".getBytes());
        Thread.sleep(1000L);
        connection.sendTicketQueryRequest(connection.mycode);
        connection.sendTicketQueryRequest("Some Unknown code".getBytes());

        connection.disconnect();
        serverChannel.close();
        eventLoopGroup.shutdownGracefully();
    }
}
