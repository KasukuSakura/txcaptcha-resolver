/*
 * Copyright 2021-2022 KasukuSakura Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/KasukuSakura/txcaptcha-resolver/blob/main/LICENSE
 */

package com.kasukusakura.tcrs.network.packets;

import io.netty.buffer.ByteBuf;

import java.util.Arrays;

public class PkgNewProcessCode {
    public static class Req implements Packet {
        private Req() {
        }

        public static final Req INSTANCE = new Req();
        public static final PacketCodec<Req> CODEC = new PacketCodec<Req>() {
            @Override
            public Req decode(int payloadlen, ByteBuf buf) {
                return INSTANCE;
            }

            @Override
            public void write(Req msg, ByteBuf buf) {
            }

            @Override
            public boolean isInstance(Object msg) {
                return msg == INSTANCE;
            }
        };
    }

    public static class Rsp implements Packet {
        public byte[] fastcode;
        public static final PacketCodec<Rsp> CODEC = new PacketCodec<Rsp>() {
            @Override
            public Rsp decode(int payloadlen, ByteBuf buf) {
                Rsp rsp = new Rsp();
                rsp.fastcode = new byte[payloadlen];
                buf.readBytes(rsp.fastcode);
                return rsp;
            }

            @Override
            public void write(Rsp msg, ByteBuf buf) {
                buf.writeBytes(msg.fastcode);
            }

            @Override
            public boolean isInstance(Object msg) {
                return msg instanceof Rsp;
            }
        };

        public static Rsp rsp(byte[] fastcode) {
            Rsp rsp = new Rsp();
            rsp.fastcode = fastcode;
            return rsp;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Rsp)) return false;
            Rsp rsp = (Rsp) o;
            return Arrays.equals(fastcode, rsp.fastcode);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(fastcode);
        }
    }

    public static class Complete implements Packet {
        public byte[] ticket;
        public byte[] fastcode;
        public static final PacketCodec<Complete> CODEC = new PacketCodec<Complete>() {
            @Override
            public Complete decode(int payloadlen, ByteBuf buf) {
                Complete rsp = new Complete();

                int len = buf.readUnsignedShort();
                rsp.ticket = new byte[len];
                buf.readBytes(rsp.ticket);

                rsp.fastcode = new byte[buf.readableBytes()];
                buf.readBytes(rsp.fastcode);
                return rsp;
            }

            @Override
            public void write(Complete msg, ByteBuf buf) {
                buf.writeShort(msg.ticket.length);
                buf.writeBytes(msg.ticket);

                buf.writeBytes(msg.fastcode);
            }

            @Override
            public boolean isInstance(Object msg) {
                return msg instanceof Complete;
            }
        };

        public static Complete complete(byte[] fastcode, byte[] ticket) {
            Complete rsp = new Complete();
            rsp.fastcode = fastcode;
            rsp.ticket = ticket;
            return rsp;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Complete)) return false;
            Complete complete = (Complete) o;
            return Arrays.equals(ticket, complete.ticket) && Arrays.equals(fastcode, complete.fastcode);
        }

        @Override
        public int hashCode() {
            int result = Arrays.hashCode(ticket);
            result = 31 * result + Arrays.hashCode(fastcode);
            return result;
        }
    }
}
