/*
 * Copyright 2021-2022 KasukuSakura Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/KasukuSakura/txcaptcha-resolver/blob/main/LICENSE
 */

package com.kasukusakura.tcrs.mls.resolver

import kotlinx.coroutines.CompletableDeferred
import net.mamoe.mirai.Bot
import net.mamoe.mirai.utils.LoginSolver
import net.miginfocom.swing.MigLayout
import java.awt.*
import java.awt.event.*
import java.beans.PropertyChangeListener
import java.util.*
import javax.swing.*

class TxCaptchaGUILoginSolver : LoginSolver() {
    override suspend fun onSolvePicCaptcha(bot: Bot, data: ByteArray): String? {
        TODO("Not yet implemented")
    }

    override suspend fun onSolveSliderCaptcha(bot: Bot, url: String): String? {
        TODO("Not yet implemented")
    }

    override suspend fun onSolveUnsafeDeviceLoginVerify(bot: Bot, url: String): String? {
        TODO("Not yet implemented")
    }
}

internal sealed class WindowResult {
    internal object Cancelled : WindowResult()
    internal object WindowClosed : WindowResult()

    internal object SelectedOK : WindowResult()

    internal class Confirmed(val data: String) : WindowResult() {
        override fun toString(): String {
            return "Confirmed<$data>"
        }
    }
}

internal class WindowsOptions(
    val layout: MigLayout,
    val contentPane: Container,
    val optionPane: JOptionPane,
) {
    var width: Int = 3

    fun appendFillX(sub: Component) {
        contentPane.add(sub, "spanx $width,growx,wrap")
    }

    fun filledTextField(name: String, value: String): JTextField {
        val field = JTextField(value)
        if (name.isEmpty()) {
            appendFillX(field)
        } else {
            val label = JLabel(name)
            contentPane.add(label)
            contentPane.add(field.also { label.labelFor = it }, "spanx ${width - 1},growx,wrap")
        }
        return field
    }

    private companion object {
        @JvmStatic
        private fun getMnemonic(key: String, l: Locale): Int {
            val value = UIManager.get(key, l) as String? ?: return 0
            try {
                return value.toInt()
            } catch (_: NumberFormatException) {
            }
            return 0
        }
    }

    private val l get() = optionPane.locale
    val BTN_YES by lazy {
        ButtonFactory(
            UIManager.getString("OptionPane.yesButtonText", l),
            getMnemonic("OptionPane.yesButtonMnemonic", l),
            null, -1)
    }
    val BTN_NO by lazy {
        ButtonFactory(
            UIManager.getString("OptionPane.noButtonText", l),
            getMnemonic("OptionPane.noButtonMnemonic", l),
            null, -1)
    }
    val BTN_OK by lazy {
        ButtonFactory(
            UIManager.getString("OptionPane.okButtonText", l),
            getMnemonic("OptionPane.okButtonMnemonic", l),
            null, -1)
    }
    val BTN_CALCEL by lazy {
        ButtonFactory(
            UIManager.getString("OptionPane.cancelButtonText", l),
            getMnemonic("OptionPane.cancelButtonMnemonic", l),
            null, -1
        )
    }

    fun ButtonFactory.withValue(v: WindowResult): JButton = withAction {
        optionPane.value = v
    }

    fun ButtonFactory.withAction(action: ActionListener): JButton {
        return createButton().also { btn ->
            btn.name = "OptionPane.button"
            btn.addActionListener(action)
        }
    }

    fun ButtonFactory.attachToTextField(field: JTextField): JButton = withAction {
        optionPane.value = WindowResult.Confirmed(field.text)
    }
}

internal class ButtonFactory(
    private val text: String,
    private val mnemonic: Int,
    private val icon: Icon?,
    private val minimumWidth: Int = -1,
) {

    fun createButton(): JButton {
        val button = if (minimumWidth > 0) {
            ConstrainedButton(text, minimumWidth)
        } else {
            JButton(text)
        }
        if (icon != null) {
            button.icon = icon
        }
        if (mnemonic != 0) {
            button.mnemonic = mnemonic
        }
        return button
    }

    private class ConstrainedButton(text: String?, val minimumWidth: Int) : JButton(text) {
        override fun getMinimumSize(): Dimension {
            val min = super.getMinimumSize()
            min.width = min.width.coerceAtLeast(minimumWidth)
            return min
        }

        override fun getPreferredSize(): Dimension {
            val pref = super.getPreferredSize()
            pref.width = pref.width.coerceAtLeast(minimumWidth)
            return pref
        }
    }
}

internal suspend fun openWindowCommon(
    window: Window,
    isTopLevel: Boolean = true,
    action: WindowsOptions.() -> Unit,
): WindowResult {
    val response = CompletableDeferred<WindowResult>()

    val optionPane = JOptionPane()
    optionPane.messageType = JOptionPane.PLAIN_MESSAGE
    optionPane.optionType = JOptionPane.OK_CANCEL_OPTION

    val contentPane = JPanel()
    val migLayout = MigLayout("debug", "[][][fill,grow]", "")
    contentPane.layout = migLayout
    optionPane.message = contentPane

    action.invoke(WindowsOptions(
        layout = migLayout,
        contentPane = contentPane,
        optionPane = optionPane,
    ))

    val realWindow = if (isTopLevel) {
        window
    } else {
        JDialog(window)
    }

    response.invokeOnCompletion {
        SwingUtilities.invokeLater { realWindow.dispose() }
    }

    fun processed() {
        val value0 = optionPane.value
        if (value0 is WindowResult) response.complete(value0)
        // Unknown
        response.complete(WindowResult.Cancelled)
    }

    val listener = PropertyChangeListener { evt ->
        if (realWindow.isVisible && evt.source === optionPane) {
            if (evt.propertyName != JOptionPane.VALUE_PROPERTY) return@PropertyChangeListener
            if (evt.newValue != null && evt.newValue != JOptionPane.UNINITIALIZED_VALUE) {
                realWindow.isVisible = false
            }
        }
    }
    optionPane.selectInitialValue()
    optionPane.addPropertyChangeListener(listener)
    val adapter = object : WindowAdapter() {
        private var gotFocus = false
        override fun windowClosing(we: WindowEvent?) {
            // println("Window closing....")
            response.complete(WindowResult.WindowClosed)

            optionPane.value = null
        }

        override fun windowClosed(e: WindowEvent?) {
            optionPane.removePropertyChangeListener(listener)

            response.complete(WindowResult.WindowClosed)
        }

        override fun windowGainedFocus(we: WindowEvent?) {
            // Once window gets focus, set initial focus
            if (!gotFocus) {
                optionPane.selectInitialValue()
                gotFocus = true
            }
        }
    }
    realWindow.addWindowListener(adapter)
    realWindow.addWindowFocusListener(adapter)
    realWindow.addHierarchyListener { evt ->
        if (evt.source !== realWindow) return@addHierarchyListener
        if (evt.changed !== realWindow) return@addHierarchyListener
        if (realWindow.isVisible) return@addHierarchyListener
        if (evt.id == HierarchyEvent.HIERARCHY_CHANGED) {
            if ((evt.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong()) != 0L) {
                // Windows disposing...
                processed()
            }
        }

    }
    realWindow.addComponentListener(object : ComponentAdapter() {
        override fun componentShown(ce: ComponentEvent) {
            // reset value to ensure closing works properly
            optionPane.value = JOptionPane.UNINITIALIZED_VALUE
        }
    })

    if (isTopLevel) {
        window as JFrame
        window.contentPane = optionPane
        window.defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
    } else {
        realWindow.layout = BorderLayout()
        realWindow.add(optionPane, BorderLayout.CENTER)
        realWindow.componentOrientation = window.componentOrientation
    }
    realWindow.pack()
    if (isTopLevel) {
        window.setLocationRelativeTo(null)
    } else {
        realWindow.setLocationRelativeTo(window)
    }
    realWindow.isVisible = true

    return response.await()
}
