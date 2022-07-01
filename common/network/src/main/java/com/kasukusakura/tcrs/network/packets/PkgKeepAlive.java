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

public class PkgKeepAlive implements Packet {
    private PkgKeepAlive() {
    }

    public static final PkgKeepAlive INSTANCE = new PkgKeepAlive();
    public static final PacketCodec<PkgKeepAlive> CODEC = new PacketCodec<PkgKeepAlive>() {
        @Override
        public PkgKeepAlive decode(int payloadlen, ByteBuf buf) {
            return INSTANCE;
        }

        @Override
        public void write(PkgKeepAlive msg, ByteBuf buf) {
        }

        @Override
        public boolean isInstance(Object msg) {
            return msg == INSTANCE;
        }
    };
}
