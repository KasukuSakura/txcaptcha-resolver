/*
 * Copyright 2021-2022 KasukuSakura Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/KasukuSakura/txcaptcha-resolver/blob/main/LICENSE
 */

package com.kasukusakura.tcrs.server;

import com.kasukusakura.tcrs.network.PkgCodec;
import com.kasukusakura.tcrs.network.packets.*;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.concurrent.ScheduledFuture;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class TCRSServerChannelInitializer extends ChannelInitializer<Channel> {
    protected PkgCodec codec = new PkgCodec();
    protected Random random = new Random();
    private final Map<String, CusPkgRsp> processes = new ConcurrentHashMap<>();
    private static final byte[] KEYS = (""
            + "1234567890"
            + "3062154987"
            + "3576894210"
            + "0316258974"
    ).getBytes(StandardCharsets.ISO_8859_1);

    protected void debugMsg(ChannelHandlerContext ctx, Supplier<String> msg) {
    }

    private static class CusPkgRsp extends PkgQueryProcessCodeStatus.Rsp {
        public long allocateTime;
        public int captchaType;
        public byte[] captchaData;
    }

    public void clearInvalidatedCaches() {
        long now = System.currentTimeMillis();
        processes.values().removeIf(rsp -> now - rsp.allocateTime > 60_000L);
    }

    @Override
    protected void initChannel(Channel ch) throws Exception {
        ch.pipeline()
                .addLast("timeout", new ReadTimeoutHandler(10000, TimeUnit.MILLISECONDS))
                .addLast("encoder", codec.getEncoder())
                .addLast("decoder", codec.getDecoder())
                .addLast("processor", new TCRSServerChannelHandler());
    }

    @SuppressWarnings("UnnecessaryReturnStatement")
    private void handlePacket(ChannelHandlerContext ctx, Packet pkg) {
        if (pkg == PkgKeepAlive.INSTANCE) {
            ctx.writeAndFlush(pkg);
            return;
        }
        if (pkg == PkgNewProcessCode.Req.INSTANCE) {
            byte[] tmp = new byte[8];
            CusPkgRsp rsp = new CusPkgRsp();
            rsp.fastcode = tmp;
            rsp.allocateTime = System.currentTimeMillis();
            do {
                for (int i = 0; i < 8; i++) {
                    tmp[i] = KEYS[(random.nextInt() & 0xFFFFFF) % KEYS.length];
                }
                String key = new String(tmp, StandardCharsets.ISO_8859_1);
                if (processes.putIfAbsent(key, rsp) == null) {
                    debugMsg(ctx, () -> "Allocated new fast process code: " + key);
                    ctx.writeAndFlush(PkgNewProcessCode.Rsp.rsp(tmp));
                    return;
                }
            } while (true);
        }
        if (pkg instanceof PkgNewProcessCode.Complete) {
            PkgNewProcessCode.Complete cp = (PkgNewProcessCode.Complete) pkg;
            String key = new String(cp.fastcode, StandardCharsets.ISO_8859_1);
            CusPkgRsp rsp = new CusPkgRsp();
            rsp.ticket = cp.ticket;
            rsp.fastcode = cp.fastcode;
            rsp.allocateTime = System.currentTimeMillis();
            processes.put(key, rsp);
            debugMsg(ctx, () -> "Fast process code ticket updated: key=" + new String(rsp.fastcode) + ", ticket=" + new String(rsp.ticket));
            return;
        }
        if (pkg instanceof PkgQueryProcessCodeStatus.Req) {
            PkgQueryProcessCodeStatus.Req req = (PkgQueryProcessCodeStatus.Req) pkg;
            String key = new String(req.fastcode, StandardCharsets.ISO_8859_1);
            CusPkgRsp rsp = processes.get(key);
            if (rsp != null) {
                debugMsg(ctx, () -> "Process code query [" + key + "]: " + (rsp.ticket == null ? "<waiting>" : new String(rsp.ticket)));
                ctx.write(rsp);
            } else {
                debugMsg(ctx, () -> "Process code query [" + key + "]: <unknown key>");
                ctx.write(PkgQueryProcessCodeStatus.Rsp.rsp(req.fastcode, null));
            }
            ctx.flush();
            return;
        }
        if (pkg instanceof PkgProcessCodeInfo.Update) {
            PkgProcessCodeInfo.Update req = (PkgProcessCodeInfo.Update) pkg;
            String key = new String(req.fastcode, StandardCharsets.ISO_8859_1);
            CusPkgRsp session = processes.get(key);
            if (session == null) {
                debugMsg(ctx, () -> "Skipped PkgProcessCodeInfo.Update because session[" + key + "] not found");
                return;
            }
            session.allocateTime = System.currentTimeMillis();
            session.captchaType = req.captchaType;
            session.captchaData = req.captchaData;
            debugMsg(ctx, () -> "Processed PkgProcessCodeInfo.Update[" + key + "] with captcha type [" + req.captchaType + "]");
            return;
        }
        if (pkg instanceof PkgProcessCodeInfo.Refresh) {
            PkgProcessCodeInfo.Refresh req = (PkgProcessCodeInfo.Refresh) pkg;
            String key = new String(req.fastcode, StandardCharsets.ISO_8859_1);
            CusPkgRsp session = processes.get(key);
            if (session == null) {
                debugMsg(ctx, () -> "Skipped PkgProcessCodeInfo.Refresh because session[" + key + "] not found");
                return;
            }
            session.allocateTime = System.currentTimeMillis();
            // debugMsg(ctx, () -> "Processed PkgProcessCodeInfo.Refresh[" + key + "]");
            return;
        }
        if (pkg instanceof PkgProcessCodeInfo.Query) {
            PkgProcessCodeInfo.Query req = (PkgProcessCodeInfo.Query) pkg;
            String key = new String(req.fastcode, StandardCharsets.ISO_8859_1);
            CusPkgRsp session = processes.get(key);
            if (session == null) {
                debugMsg(ctx, () -> "Skipped PkgProcessCodeInfo.Query because session[" + key + "] not found");
                ctx.writeAndFlush(PkgProcessCodeInfo.Response.response(0, null, req.fastcode));
                return;
            }
            session.allocateTime = System.currentTimeMillis();
            ctx.writeAndFlush(PkgProcessCodeInfo.Response.response(
                    session.captchaType, session.captchaData, req.fastcode
            ));
            debugMsg(ctx, () -> "Responded PkgProcessCodeInfo.Query[" + key + "] with captcha type[" + session.captchaType + "]");
            return;
        }
    }

    protected void logError(Throwable error) {
        error.printStackTrace(System.err);
    }

    private class TCRSServerChannelHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            logError(cause);
            ctx.channel().close();
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof Packet) {
                handlePacket(ctx, (Packet) msg);
                return;
            }
            super.channelRead(ctx, msg);
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            ScheduledFuture<?> scheduledFuture = ctx.channel().eventLoop().scheduleWithFixedDelay(
                    TCRSServerChannelInitializer.this::clearInvalidatedCaches,
                    1, 1, TimeUnit.MINUTES
            );
            ctx.channel().closeFuture().addListener($$$$ -> scheduledFuture.cancel(true));

            ctx.fireChannelActive();
        }
    }
}
