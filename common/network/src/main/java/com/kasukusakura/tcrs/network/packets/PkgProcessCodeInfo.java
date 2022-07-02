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
import java.util.Objects;

public class PkgProcessCodeInfo {
    public static class Update implements Packet {
        public int captchaType;
        public byte[] captchaData;
        public byte[] fastcode;

        public static Update update(int type, byte[] data, byte[] fastcode) {
            Update rsp = new Update();
            rsp.fastcode = fastcode;
            rsp.captchaType = type;
            rsp.captchaData = data;
            return rsp;
        }

        public static final PacketCodec<Update> CODEC = new PacketCodec<Update>() {
            @Override
            public Update decode(int payloadlen, ByteBuf buf) {
                Update rsp = new Update();
                rsp.captchaType = buf.readUnsignedShort();
                int len = buf.readUnsignedShort();
                if (len != 0) {
                    rsp.captchaData = new byte[len];
                    buf.readBytes(rsp.captchaData);
                }
                rsp.fastcode = new byte[buf.readableBytes()];
                buf.readBytes(rsp.fastcode);
                return rsp;
            }

            @Override
            public void write(Update msg, ByteBuf buf) {
                buf.writeShort(msg.captchaType);
                if (msg.captchaData == null) {
                    buf.writeShort(0);
                } else {
                    buf.writeShort(msg.captchaData.length)
                            .writeBytes(msg.captchaData);
                }
                buf.writeBytes(msg.fastcode);
            }

            @Override
            public boolean isInstance(Object msg) {
                return msg instanceof Update;
            }
        };

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Update)) return false;
            Update update = (Update) o;
            return captchaType == update.captchaType && Arrays.equals(captchaData, update.captchaData) && Arrays.equals(fastcode, update.fastcode);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(captchaType);
            result = 31 * result + Arrays.hashCode(captchaData);
            result = 31 * result + Arrays.hashCode(fastcode);
            return result;
        }
    }

    public static class Refresh implements Packet {
        public byte[] fastcode;

        public static Refresh refresh(byte[] fastcode) {
            Refresh rsp = new Refresh();
            rsp.fastcode = fastcode;
            return rsp;
        }

        public static final PacketCodec<Refresh> CODEC = new PacketCodec<Refresh>() {
            @Override
            public Refresh decode(int payloadlen, ByteBuf buf) {
                Refresh rsp = new Refresh();
                rsp.fastcode = new byte[buf.readableBytes()];
                buf.readBytes(rsp.fastcode);
                return rsp;
            }

            @Override
            public void write(Refresh msg, ByteBuf buf) {
                buf.writeBytes(msg.fastcode);
            }

            @Override
            public boolean isInstance(Object msg) {
                return msg instanceof Refresh;
            }
        };

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Refresh)) return false;
            Refresh refresh = (Refresh) o;
            return Arrays.equals(fastcode, refresh.fastcode);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(fastcode);
        }
    }

    public static class Query implements Packet {
        public byte[] fastcode;

        public static Query query(byte[] fastcode) {
            Query rsp = new Query();
            rsp.fastcode = fastcode;
            return rsp;
        }

        public static final PacketCodec<Query> CODEC = new PacketCodec<Query>() {
            @Override
            public Query decode(int payloadlen, ByteBuf buf) {
                Query rsp = new Query();
                rsp.fastcode = new byte[buf.readableBytes()];
                buf.readBytes(rsp.fastcode);
                return rsp;
            }

            @Override
            public void write(Query msg, ByteBuf buf) {
                buf.writeBytes(msg.fastcode);
            }

            @Override
            public boolean isInstance(Object msg) {
                return msg instanceof Query;
            }
        };

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Query)) return false;
            Query refresh = (Query) o;
            return Arrays.equals(fastcode, refresh.fastcode);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(fastcode);
        }
    }

    public static class Response implements Packet {

        public int captchaType;
        public byte[] captchaData;
        public byte[] fastcode;

        public static Response response(int type, byte[] data, byte[] fastcode) {
            Response rsp = new Response();
            rsp.fastcode = fastcode;
            rsp.captchaType = type;
            rsp.captchaData = data;
            return rsp;
        }

        public static final PacketCodec<Response> CODEC = new PacketCodec<Response>() {
            @Override
            public Response decode(int payloadlen, ByteBuf buf) {
                Update update = Update.CODEC.decode(payloadlen, buf);
                Response rsp = new Response();
                rsp.fastcode = update.fastcode;
                rsp.captchaData = update.captchaData;
                rsp.captchaType = update.captchaType;
                return rsp;
            }

            @Override
            public void write(Response msg, ByteBuf buf) {
                Update update = new Update();
                update.captchaData = msg.captchaData;
                update.captchaType = msg.captchaType;
                update.fastcode = msg.fastcode;
                Update.CODEC.write(update, buf);
            }

            @Override
            public boolean isInstance(Object msg) {
                return msg instanceof Response;
            }
        };

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Response)) return false;
            Response response = (Response) o;
            return captchaType == response.captchaType && Arrays.equals(captchaData, response.captchaData) && Arrays.equals(fastcode, response.fastcode);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(captchaType);
            result = 31 * result + Arrays.hashCode(captchaData);
            result = 31 * result + Arrays.hashCode(fastcode);
            return result;
        }
    }

}
