/*
 * Copyright 2021-2022 KasukuSakura Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/KasukuSakura/txcaptcha-resolver/blob/main/LICENSE
 */

package com.kasukusakura.tcrs.mls.console

import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import javax.swing.UIManager

object ConsolePluginMain : KotlinPlugin(
    JvmPluginDescription(id = "com.kasukusakura.tcrs.mls", version = "0.0.1")
) {
    @Suppress("MemberVisibilityCanBePrivate")
    internal var applyFlatLafStyle = true

    override fun onEnable() {
        // DEBUG ONLY
        if (applyFlatLafStyle) {
            jvmPluginClasspath.downloadAndAddToPath(
                jvmPluginClasspath.pluginIndependentLibrariesClassLoader,
                listOf("com.formdev:flatlaf:2.3", "com.formdev:flatlaf-intellij-themes:2.3")
            )
            Class.forName("com.formdev.flatlaf.intellijthemes.FlatDarkFlatIJTheme").getMethod("setup").invoke(null)
            UIManager.getDefaults()["ClassLoader"] = jvmPluginClasspath.pluginIndependentLibrariesClassLoader
        }
    }
}
