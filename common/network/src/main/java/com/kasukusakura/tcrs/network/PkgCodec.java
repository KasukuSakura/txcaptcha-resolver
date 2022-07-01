/*
 * Copyright 2021-2022 KasukuSakura Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/KasukuSakura/txcaptcha-resolver/blob/main/LICENSE
 */

package com.kasukusakura.tcrs.network;

import com.kasukusakura.tcrs.network.packets.*;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;

import java.io.IOException;

@SuppressWarnings("NullableProblems")
class PkgDecoder extends ChannelInboundHandlerAdapter {
    PacketCodec<?>[] codecs;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof ByteBuf) {
            ByteBuf buffer = (ByteBuf) msg;
            while (buffer.isReadable(Integer.BYTES)) {
                buffer.markReaderIndex();
                int pid = buffer.readUnsignedShort();
                int payloadlen = buffer.readUnsignedShort();
                if (!buffer.isReadable(payloadlen)) {
                    buffer.resetReaderIndex();
                    return;
                }
                if (pid < 0) {
                    throw new IOException("Bad packet: pid=" + pid);
                }
                if (pid < codecs.length) {
                    ctx.fireChannelRead(codecs[pid].decode(payloadlen, buffer.readSlice(payloadlen)));
                } else {
                    throw new IOException("Bad packet: pid=" + pid);
                }
            }
            return;
        }
        super.channelRead(ctx, msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        super.exceptionCaught(ctx, cause);
    }
}

class PkgEncoder extends ChannelOutboundHandlerAdapter {
    PacketCodec<?>[] codecs;

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (!(msg instanceof Packet)) {
            ctx.write(msg, promise);
            return;
        }

        Packet pkg = (Packet) msg;
        for (int pid = 0, codecsLength = codecs.length; pid < codecsLength; pid++) {
            PacketCodec codec = codecs[pid];
            if (codec.isInstance(msg)) {
                ByteBuf buf = null;
                try {
                    buf = ctx.alloc().ioBuffer();
                    int payloadLenIndex;
                    buf.writeShort(pid);

                    payloadLenIndex = buf.writerIndex();
                    try {
                        buf.writeShort(0);
                        codec.write(pkg, buf);
                    } finally {
                        ReferenceCountUtil.release(msg);
                    }

                    int writeIndex = buf.writerIndex();
                    buf.writerIndex(payloadLenIndex);
                    buf.writeShort(writeIndex - payloadLenIndex - 2);
                    buf.writerIndex(writeIndex);

                    ctx.write(buf, promise);
                    buf = null;
                } finally {
                    if (buf != null) buf.release();
                }
                return;
            }
        }
        ctx.write(msg, promise);
    }
}

public class PkgCodec {
    PacketCodec<?>[] codecs;

    public PkgCodec() {
        codecs = new PacketCodec[]{
                PkgKeepAlive.CODEC,
                PkgNewProcessCode.Req.CODEC,
                PkgNewProcessCode.Rsp.CODEC,
                PkgNewProcessCode.Complete.CODEC,
                PkgQueryProcessCodeStatus.Req.CODEC,
                PkgQueryProcessCodeStatus.Rsp.CODEC,
        };
    }

    public ChannelInboundHandlerAdapter getDecoder() {
        PkgDecoder decoder = new PkgDecoder();
        decoder.codecs = codecs;
        return decoder;
    }

    public ChannelOutboundHandlerAdapter getEncoder() {
        PkgEncoder encoder = new PkgEncoder();
        encoder.codecs = codecs;
        return encoder;
    }
}
