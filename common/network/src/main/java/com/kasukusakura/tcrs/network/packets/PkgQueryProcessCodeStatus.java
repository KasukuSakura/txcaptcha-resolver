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

public class PkgQueryProcessCodeStatus {
    public static class Req implements Packet {
        public byte[] fastcode;

        public static final PacketCodec<Req> CODEC = new PacketCodec<Req>() {
            @Override
            public Req decode(int payloadlen, ByteBuf buf) {
                Req rsp = new Req();
                rsp.fastcode = new byte[payloadlen];
                buf.readBytes(rsp.fastcode);
                return rsp;
            }

            @Override
            public void write(Req msg, ByteBuf buf) {
                buf.writeBytes(msg.fastcode);
            }

            @Override
            public boolean isInstance(Object msg) {
                return msg instanceof Req;
            }
        };

        public static Req req(byte[] fastcode) {
            Req req = new Req();
            req.fastcode = fastcode;
            return req;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Req)) return false;
            Req req = (Req) o;
            return Arrays.equals(fastcode, req.fastcode);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(fastcode);
        }
    }

    public static class Rsp implements Packet {
        public byte[] fastcode;
        public byte[] ticket;
        public static final PacketCodec<Rsp> CODEC = new PacketCodec<Rsp>() {
            @Override
            public Rsp decode(int payloadlen, ByteBuf buf) {
                Rsp rsp = new Rsp();
                if (buf.readBoolean()) {
                    rsp.ticket = new byte[buf.readUnsignedShort()];
                    buf.readBytes(rsp.ticket);
                }
                rsp.fastcode = new byte[buf.readableBytes()];
                buf.readBytes(rsp.fastcode);
                return rsp;
            }

            @Override
            public void write(Rsp msg, ByteBuf buf) {
                byte[] ticket = msg.ticket;
                if (ticket == null) {
                    buf.writeBoolean(false);
                } else {
                    buf.writeBoolean(true);
                    buf.writeShort(ticket.length);
                    buf.writeBytes(ticket);
                }
                buf.writeBytes(msg.fastcode);
            }

            @Override
            public boolean isInstance(Object msg) {
                return msg instanceof Rsp;
            }
        };

        public static Rsp rsp(byte[] fastcode, byte[] ticket) {
            Rsp rsp = new Rsp();
            rsp.fastcode = fastcode;
            rsp.ticket = ticket;
            return rsp;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Rsp)) return false;
            Rsp rsp = (Rsp) o;
            return Arrays.equals(fastcode, rsp.fastcode) && Arrays.equals(ticket, rsp.ticket);
        }

        @Override
        public int hashCode() {
            int result = Arrays.hashCode(fastcode);
            result = 31 * result + Arrays.hashCode(ticket);
            return result;
        }
    }
}
