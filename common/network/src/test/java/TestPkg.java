/*
 * Copyright 2021-2022 KasukuSakura Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/KasukuSakura/txcaptcha-resolver/blob/main/LICENSE
 */

import com.kasukusakura.tcrs.network.PkgCodec;
import com.kasukusakura.tcrs.network.packets.Packet;
import com.kasukusakura.tcrs.network.packets.PkgKeepAlive;
import com.kasukusakura.tcrs.network.packets.PkgNewProcessCode;
import com.kasukusakura.tcrs.network.packets.PkgQueryProcessCodeStatus;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;

import java.util.*;

public class TestPkg {
    public static byte[] random(Random random) {
        byte[] rsp = new byte[8];
        random.nextBytes(rsp);
        return rsp;
    }

    private static class TestChannel extends EmbeddedChannel {

        final ByteBuf ootbuf = alloc().ioBuffer();

        @Override
        protected void handleOutboundMessage(Object msg) {
            try {
                if (msg instanceof ByteBuf) {
                    System.out.println("  `- " + ByteBufUtil.hexDump((ByteBuf) msg));
                    ootbuf.writeBytes((ByteBuf) msg);
                } else if (msg instanceof byte[]) {
                    System.out.println("  `- " + ByteBufUtil.hexDump((byte[]) msg));
                    ootbuf.writeBytes((byte[]) msg);
                } else {
                    throw new ClassCastException(String.valueOf(msg));
                }
            } finally {
                ReferenceCountUtil.release(msg);
            }
        }

    }

    public static void main(String[] args) {
        Random random = new Random();
        List<Packet> packets = new ArrayList<>();
        packets.add(PkgKeepAlive.INSTANCE);
        packets.add(PkgKeepAlive.INSTANCE);
        packets.add(PkgNewProcessCode.Req.INSTANCE);
        packets.add(PkgNewProcessCode.Rsp.rsp(random(random)));
        packets.add(PkgNewProcessCode.Complete.complete(random(random), random(random)));
        packets.add(PkgQueryProcessCodeStatus.Req.req(random(random)));
        packets.add(PkgQueryProcessCodeStatus.Rsp.rsp(random(random), random(random)));
        packets.add(PkgKeepAlive.INSTANCE);

        PkgCodec codec = new PkgCodec();
        TestChannel testChannel = new TestChannel();
        testChannel.pipeline().addLast(codec.getDecoder()).addLast(codec.getEncoder());

        for (Packet pkg : packets) {
            System.out.println("Emiting.... " + pkg);
            testChannel.writeAndFlush(pkg);
            System.out.println();
        }
        testChannel.checkException();
        System.out.println("==========================");

        System.out.println(testChannel.ootbuf);
        System.out.println(ByteBufUtil.hexDump(testChannel.ootbuf));

        testChannel.writeOneInbound(testChannel.ootbuf);

        Queue<Object> inboundMessages = testChannel.inboundMessages();
        {
            Iterator<?> parsed = inboundMessages.iterator(), source = packets.iterator();
            while (parsed.hasNext() && source.hasNext()) {
                Object received = parsed.next();
                Object src = source.next();
                System.out.println("== [");
                System.out.println("Origin  packet: " + src);
                System.out.println("Decoded packet: " + received);
                if (received instanceof ByteBuf) {
                    System.out.println("  `- " + ByteBufUtil.hexDump((ByteBuf) received));
                }
                boolean isSame = src.equals(received);
                System.out.println("Same packet:    " + isSame);
                System.out.println("== ]");
                if (!isSame) throw new RuntimeException();
            }
        }
        if (inboundMessages.size() != packets.size()) {
            Iterator<?> parsed = inboundMessages.iterator(), source = packets.iterator();

            while (parsed.hasNext() && source.hasNext()) {
                parsed.next();
                source.next();
            }

            System.out.println("Size not match: srcSize: " + packets.size() + ", parsed: " + inboundMessages.size());
            System.out.println("Remaining sources: ");
            while (source.hasNext()) {
                System.out.println("  `- " + source.next());
            }
            System.out.println("Parsed packets: ");
            while (parsed.hasNext()) {
                Object next = parsed.next();
                System.out.println("  `- " + next);
                if (next instanceof ByteBuf) {
                    System.out.println("    |- " + ByteBufUtil.hexDump((ByteBuf) next));
                }
            }

            throw new RuntimeException();
        }
    }
}
