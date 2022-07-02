/*
 * Copyright 2021-2022 KasukuSakura Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/KasukuSakura/txcaptcha-resolver/blob/main/LICENSE
 */

package com.kasukusakura.tcrs.mls.resolver

import com.google.zxing.BarcodeFormat
import com.google.zxing.client.j2se.MatrixToImageWriter
import com.google.zxing.qrcode.QRCodeWriter
import com.kasukusakura.tcrs.client.AutoReconnectClientConnection
import com.kasukusakura.tcrs.server.TCRSServerChannelInitializer
import io.ktor.util.network.*
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.EventLoopGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import kotlinx.coroutines.*
import net.mamoe.mirai.Bot
import net.mamoe.mirai.utils.LoginSolver
import net.mamoe.mirai.utils.MiraiLogger
import net.mamoe.mirai.utils.debug
import net.mamoe.mirai.utils.verbose
import net.miginfocom.swing.MigLayout
import java.awt.*
import java.awt.event.*
import java.awt.image.BufferedImage
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.io.ByteArrayInputStream
import java.net.*
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.function.Supplier
import javax.imageio.ImageIO
import javax.swing.*
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Suppress("MemberVisibilityCanBePrivate")
object TxCaptchaGUILoginSolver : LoginSolver() {
    override suspend fun onSolvePicCaptcha(bot: Bot, data: ByteArray): String? {
        val img = runInterruptible(Dispatchers.IO) {
            val usingCache = ImageIO.getUseCache()
            try {
                ImageIO.setUseCache(false)
                ImageIO.read(ByteArrayInputStream(data))
            } finally {
                if (usingCache) ImageIO.setUseCache(true)
            }
        }
        return onSolvePicCaptcha(bot.id, img)
    }

    override suspend fun onSolveSliderCaptcha(bot: Bot, url: String): String? {
        return onSolveSliderCaptcha(bot.id, url, bot.logger)
    }

    override suspend fun onSolveUnsafeDeviceLoginVerify(bot: Bot, url: String): String? {
        TODO("Not yet implemented")
    }


    internal suspend fun onSolvePicCaptcha(botid: Long, img: BufferedImage): String? {
        return openWindowCommon(JFrame(), isTopLevel = true, title = "PicCaptcha($botid)") {
            appendFillX(JLabel(ImageIcon(img)))
            optionPane.options = arrayOf(
                BTN_OK.attachToTextField(filledTextField("", "")).asInitialValue(),
                BTN_CALCEL.withValue(WindowResult.Cancelled),
            )
        }.valueAsString
    }

    internal suspend fun onSolveSliderCaptcha(botid: Long, captchaUrl: String, logger: MiraiLogger): String? {
        return openWindowCommon(JFrame(), isTopLevel = true, title = "SliderCaptcha($botid)") {
            fun alertError(msg: String) {
                JOptionPane.showMessageDialog(
                    parentWindow,
                    msg,
                    "Error[Slider Captcha]($botid)",
                    JOptionPane.ERROR_MESSAGE
                )
            }
            filledTextField("url", captchaUrl)
            optionPane.options = arrayOf(
                JButton("Use Captcha Server").withActionBlocking {
                    val selfHosted = WindowResult.ConfirmedAnything()
                    val serverMode = openWindowCommon(
                        window = parentWindow, isTopLevel = false,
                        blockingDisplay = true,
                        title = "Captcha Exchange Server Select($botid)"
                    ) {
                        optionPane.options = arrayOf(
                            JButton("Use Self Hosted").withValue(selfHosted),
                            BTN_OK.attachToTextField(filledTextField("server", "")).asInitialValue(),
                            BTN_CALCEL.withValue(WindowResult.Cancelled),
                        )
                    }
                    if (serverMode.cancelled) {
                        return@withActionBlocking
                    }
                    val eventLoopGroup: EventLoopGroup
                    val remoteAddress: SocketAddress
                    val captchaExchangeServerResponse = CompletableDeferred<WindowResult>()
                    if (serverMode === selfHosted) {
                        eventLoopGroup = NioEventLoopGroup()
                        val serverChannel = ServerBootstrap()
                            .channel(NioServerSocketChannel::class.java)
                            .group(eventLoopGroup)
                            .childHandler(object : TCRSServerChannelInitializer() {
                                override fun debugMsg(ctx: ChannelHandlerContext, msg: Supplier<String>) {
                                    logger.debug { ctx.channel().toString() + " || " + msg.get() }
                                }

                                override fun debugMsgVerbose(ctx: ChannelHandlerContext, msg: Supplier<String>) {
                                    logger.verbose { ctx.channel().toString() + " || " + msg.get() }
                                }

                                override fun logError(error: Throwable?) {
                                    logger.warning("Server error: ${error?.localizedMessage}", error)
                                }
                            })
                            .bind(0)
                            .await()
                            .channel()
                        captchaExchangeServerResponse.invokeOnCompletion { serverChannel.close() }

                        remoteAddress = serverChannel.localAddress()
                    } else {
                        val remoteServer = serverMode.valueAsString?.trim() ?: return@withActionBlocking
                        if (remoteServer.isBlank()) {
                            alertError("No remote server provided.")
                            return@withActionBlocking
                        }
                        if ((remoteServer[0] == '[' && remoteServer.last() == ']')
                            || !remoteServer.contains(':')
                        ) {
                            alertError("Invalid remote server address: $remoteServer: no port")
                            return@withActionBlocking
                        }
                        remoteAddress = try {
                            InetSocketAddress.createUnresolved(
                                remoteServer.substringBeforeLast(':'),
                                remoteServer.substringAfterLast(':').toInt(),
                            )
                        } catch (err: Throwable) {
                            logger.warning(err)
                            alertError(err.localizedMessage)
                            return@withActionBlocking
                        }
                        eventLoopGroup = NioEventLoopGroup()
                    }

                    openWindowCommon(
                        window = parentWindow,
                        isTopLevel = false,
                        title = "Captcha Server Exchange($botid, $remoteAddress)",
                        overrideResponse = captchaExchangeServerResponse,
                        blockingDisplay = true,
                    ) {

                        val fastCodeView = JLabel("<waiting....>")
                        appendFillWithLabel("fastcode", fastCodeView)

                        if (serverMode === selfHosted) {
                            val addresses = NetworkInterface.getNetworkInterfaces().asSequence().filter {
                                !it.isLoopback
                            }.flatMap { itf ->
                                itf.inetAddresses.asSequence()
                            }.toMutableList()
                            addresses.sortWith(kotlin.Comparator { i1, i2 ->
                                if (i1 is Inet4Address && i2 is Inet6Address) {
                                    return@Comparator -1
                                }
                                if (i2 is Inet4Address && i1 is Inet6Address) {
                                    return@Comparator 1
                                }
                                return@Comparator i1.toString().compareTo(i2.toString())
                            })
                            val chooser = JComboBox<String>()
                            addresses.forEach { chooser.addItem(it.toString()) }
                            appendFillWithLabel("Hosted Server InetAddress", chooser)

                            val qrcode = JLabel()
                            val listener = object : PropertyChangeListener, ItemListener {
                                override fun propertyChange(evt: PropertyChangeEvent?) {
                                    update()
                                }

                                override fun itemStateChanged(e: ItemEvent?) {
                                    update()
                                }

                                fun update() {
                                    val v = chooser.selectedItem
                                    if (v != null) {
                                        // <txcaptcha>:=/captcha?fastcode=......&server=.......&serverport=.....
                                        val fasturl = "<txcaptcha>:=/captcha?fastcode=${fastCodeView.text}&server=${
                                            URLEncoder.encode(
                                                v.toString(),
                                                "UTF-8"
                                            )
                                        }&port=${remoteAddress.port}"
                                        logger.debug { fasturl }

                                        val bitMatrix = QRCodeWriter().encode(
                                            fasturl,
                                            BarcodeFormat.QR_CODE,
                                            500,
                                            500
                                        )

                                        val img = MatrixToImageWriter.toBufferedImage(bitMatrix)
                                        qrcode.icon = ImageIcon(img)
                                    }
                                }
                            }

                            chooser.addPropertyChangeListener(listener)
                            chooser.addItemListener(listener)
                            fastCodeView.addPropertyChangeListener(listener)
                            appendFillX(qrcode)

                            captchaExchangeServerResponse.invokeOnCompletion {
                                fastCodeView.removePropertyChangeListener(listener)
                                chooser.removeItemListener(listener)
                                chooser.removePropertyChangeListener(listener)
                            }
                        }

                        val msgOutput = JLabel()
                        appendFillX(msgOutput)

                        val connection = object : AutoReconnectClientConnection() {
                            override fun logError(throwable: Throwable?, ctx: ChannelHandlerContext?) {
                                msgOutput.text = throwable.toString()
                                logger.debug("Client Error:${throwable?.localizedMessage}", throwable)
                            }

                            init {
                                this.eventLoopGroup = eventLoopGroup
                            }

                            override fun onFastCodeReceived(fastcode: ByteArray) {
                                fastCodeView.text = String(fastcode)
                                sendProcessCodeInfoUpdate(CAPTCHA_TYPE_SLIDER, captchaUrl.toByteArray(), fastcode)

                                eventLoopGroup.scheduleWithFixedDelay({
                                    sendProcessCodeInfoUpdate(CAPTCHA_TYPE_SLIDER, captchaUrl.toByteArray(), fastcode)
                                }, 30, 30, TimeUnit.SECONDS)
                                eventLoopGroup.scheduleWithFixedDelay({
                                    sendProcessCodeRefresh(fastcode)
                                }, 10, 10, TimeUnit.SECONDS)

                                eventLoopGroup.scheduleWithFixedDelay({
                                    sendTicketQueryRequest(fastcode)
                                }, 1, 1, TimeUnit.SECONDS)
                            }

                            override fun onTickReceived(ticket: ByteArray?, fastcode: ByteArray?) {
                                if (ticket != null) {
                                    captchaExchangeServerResponse.complete(WindowResult.Confirmed(String(ticket)))
                                }
                            }

                            override fun bindConnection(channel: Channel?) {
                                super.bindConnection(channel)
                                msgOutput.text = ""
                            }
                        }
                        connection.connect(remoteAddress)

                        captchaExchangeServerResponse.invokeOnCompletion {
                            connection.disconnect()
                            eventLoopGroup.shutdownGracefully()
                        }
                        connection.sendNewFastCodeReq()
                    }

                    val captchaExchangeResponse = captchaExchangeServerResponse.await()
                    logger.debug { "Response from CaptchaExchange: $captchaExchangeResponse" }
                    if (captchaExchangeResponse.cancelled) return@withActionBlocking
                    response.complete(captchaExchangeResponse)
                },
                BTN_OK.attachToTextField(filledTextField("ticket", "")).asInitialValue(),
                BTN_CALCEL.withValue(WindowResult.Cancelled),
            )
        }.also { rsp ->
            logger.debug { "Response from TopLevel: $rsp" }
        }.valueAsString
    }
}

internal sealed class WindowResult {
    abstract val cancelled: Boolean
    abstract val valueAsString: String?
    abstract val value: Any?

    internal object Cancelled : WindowResult() {
        override val valueAsString: String? get() = null
        override val value: Any? get() = null
        override val cancelled: Boolean get() = true

        override fun toString(): String {
            return "WindowResult.Cancelled"
        }
    }

    internal object WindowClosed : WindowResult() {
        override val valueAsString: String? get() = null
        override val value: Any? get() = null
        override val cancelled: Boolean get() = true

        override fun toString(): String {
            return "WindowResult.WindowClosed"
        }
    }

    internal object SelectedOK : WindowResult() {
        override val valueAsString: String get() = "true"
        override val value: Any get() = true
        override val cancelled: Boolean get() = false

        override fun toString(): String {
            return "WindowResult.SelectedOK"
        }
    }

    internal class Confirmed(private val data: String) : WindowResult() {
        override fun toString(): String {
            return "WindowResult.Confirmed($data)"
        }

        override val valueAsString: String get() = data
        override val value: Any get() = data
        override val cancelled: Boolean get() = false
    }

    internal class ConfirmedAnything(override val value: Any? = null) : WindowResult() {
        override val valueAsString: String?
            get() = value?.toString()

        override val cancelled: Boolean get() = false

        override fun toString(): String {
            return "WindowResult.ConfirmedAnything(value=$value)@${hashCode()}"
        }
    }
}

@Suppress("PropertyName")
internal class WindowsOptions(
    val layout: MigLayout,
    val contentPane: Container,
    val optionPane: JOptionPane,
    val response: CompletableDeferred<WindowResult>,
    val parentWindow: Window,
) {
    var width: Int = 3

    fun appendFillX(sub: Component) {
        contentPane.add(sub, "spanx $width,growx,wrap")
    }

    fun appendFillWithLabel(name: String, comp: Component): JLabel {
        val label = JLabel(name)
        contentPane.add(label)
        contentPane.add(comp.also { label.labelFor = it }, "spanx ${width - 1},growx,wrap")
        return label
    }

    fun filledTextField(name: String, value: String): JTextField {
        val field = JTextField(value)
        if (name.isEmpty()) {
            appendFillX(field)
        } else {
            appendFillWithLabel(name, field)
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
            null, -1
        )
    }
    val BTN_NO by lazy {
        ButtonFactory(
            UIManager.getString("OptionPane.noButtonText", l),
            getMnemonic("OptionPane.noButtonMnemonic", l),
            null, -1
        )
    }
    val BTN_OK by lazy {
        ButtonFactory(
            UIManager.getString("OptionPane.okButtonText", l),
            getMnemonic("OptionPane.okButtonMnemonic", l),
            null, -1
        )
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

    fun JButton.withValue(v: WindowResult): JButton = withAction {
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

    fun <T : Any> T.asInitialValue(): T {
        optionPane.initialValue = this@asInitialValue
        return this@asInitialValue
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
    title: String,
    isTopLevel: Boolean = true,
    blockingDisplay: Boolean = false,
    overrideResponse: CompletableDeferred<WindowResult>? = null,
    action: WindowsOptions.() -> Unit,
): WindowResult {
    val response = overrideResponse ?: CompletableDeferred()

    val optionPane = JOptionPane()
    optionPane.messageType = JOptionPane.PLAIN_MESSAGE
    optionPane.optionType = JOptionPane.OK_CANCEL_OPTION

    val contentPane = JPanel()
    val migLayout = MigLayout("", "[][][fill,grow]", "")
    contentPane.layout = migLayout
    optionPane.message = contentPane


    val realWindow = if (isTopLevel) {
        (window as JFrame).title = title
        window
    } else {
        JDialog(window, title, Dialog.ModalityType.APPLICATION_MODAL)
    }

    action.invoke(
        WindowsOptions(
            layout = migLayout,
            contentPane = contentPane,
            optionPane = optionPane,
            response = response,
            parentWindow = realWindow,
        )
    )

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
    if (blockingDisplay) {
        realWindow.isVisible = true
    } else {
        SwingUtilities.invokeLater { realWindow.isVisible = true }
    }

    return response.await()
}

internal fun JButton.withAction(action: ActionListener): JButton = apply {
    addActionListener(action)
}

internal object SwingxDispatcher : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        if (SwingUtilities.isEventDispatchThread()) block.run()
        else SwingUtilities.invokeLater(block)
    }

}

internal fun JButton.withActionBlocking(action: suspend CoroutineScope.() -> Unit): JButton = withAction {
    runBlocking(SwingxDispatcher, block = action)
}

suspend fun ChannelFuture.awaitKotlin(): ChannelFuture {
    if (isDone) return this
    suspendCoroutine<Unit> { cont ->
        addListener { cont.resume(Unit) }
    }
    return this
}
