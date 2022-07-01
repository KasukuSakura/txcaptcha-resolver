/*
 * Copyright 2021-2022 KasukuSakura Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/KasukuSakura/txcaptcha-resolver/blob/main/LICENSE
 */

package testw

import com.kasukusakura.tcrs.mls.resolver.WindowResult
import com.kasukusakura.tcrs.mls.resolver.openWindowCommon
import kotlinx.coroutines.runBlocking
import javax.swing.JFrame
import javax.swing.JLabel

fun main() {
    com.formdev.flatlaf.intellijthemes.FlatDarkFlatIJTheme.setup()

//    val msg = JPanel()
    runBlocking {
        val topFrame = JFrame()
        topFrame.setLocationRelativeTo(null)
        topFrame.isVisible = true
        topFrame.defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
        val rsp = openWindowCommon(topFrame, isTopLevel = false) {
            layout.layoutConstraints = ""

            filledTextField("Test", "https://github.com")
            filledTextField("Test!!!!!!!!!!!!!", "https://github.com")
            appendFillX(JLabel("Hello World"))

//            optionPane.optionType = JOptionPane.OK_CANCEL_OPTION
            optionPane.options = arrayOf(
                BTN_OK.attachToTextField(filledTextField("ticket", "")).also {
                    optionPane.initialValue = it
                },
                BTN_CALCEL.withValue(WindowResult.Cancelled),
            )
        }
        println("RSP: $rsp")
        topFrame.dispose()
    }
}