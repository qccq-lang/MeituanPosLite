package com.pos.lite.print

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.*
import android.os.Build
import android.util.Log
import com.pos.lite.data.Order
import com.pos.lite.data.OrderItem
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*

object PosPrinterHelper {

    private const val ACTION_USB_PERMISSION = "com.pos.lite.USB_PERMISSION"

    private val ESC_INIT = byteArrayOf(0x1B, 0x40)
    private val ESC_ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0x00)
    private val ESC_ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01)
    private val ESC_DOUBLE_HEIGHT = byteArrayOf(0x1D, 0x21, 0x01)
    private val ESC_DOUBLE_SIZE = byteArrayOf(0x1D, 0x21, 0x11)
    private val ESC_NORMAL = byteArrayOf(0x1D, 0x21, 0x00)
    private val ESC_CUT_PAPER = byteArrayOf(0x1D, 0x56, 0x42, 0x00)
    private val DRAWER_KICK = byteArrayOf(0x1B, 0x70, 0x00, 0x1E, (0xFF).toByte())

    /**
     * 补打小票或直接打印
     */
    fun printReceipt(context: Context, order: Order, items: List<OrderItem>) {
        executePrintAction(context, order, items, needPrint = true, needKickDrawer = false)
    }

    /**
     * 结账按需打印与弹箱
     */
    fun executePrintAction(
        context: Context,
        order: Order,
        items: List<OrderItem>,
        needPrint: Boolean,
        needKickDrawer: Boolean
    ) {
        if (!needPrint && !needKickDrawer) {
            Log.d("PosPrinter", "跳过打印与弹箱")
            return
        }

        if (!needPrint && needKickDrawer) {
            val kickStream = ByteArrayOutputStream().apply {
                write(ESC_INIT)
                write(DRAWER_KICK)
            }
            printViaUsb(context, kickStream.toByteArray())
            return
        }

        val bytes = buildReceiptBytes(order, items, needKickDrawer)
        printViaUsb(context, bytes)
    }

    fun printViaUsb(context: Context, data: ByteArray): Boolean {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return false
        val deviceList = usbManager.deviceList

        for (device in deviceList.values) {
            var isPrinter = false
            for (i in 0 until device.interfaceCount) {
                val iface = device.getInterface(i)
                if (iface.interfaceClass == UsbConstants.USB_CLASS_PRINTER || iface.interfaceClass == 7) {
                    isPrinter = true
                    break
                }
            }

            if (isPrinter || device.interfaceCount > 0) {
                if (!usbManager.hasPermission(device)) {
                    val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
                    val permissionIntent = PendingIntent.getBroadcast(context, 0, Intent(ACTION_USB_PERMISSION), flags)
                    usbManager.requestPermission(device, permissionIntent)
                    return false
                }

                val connection = usbManager.openDevice(device) ?: continue
                for (i in 0 until device.interfaceCount) {
                    val usbInterface = device.getInterface(i)
                    connection.claimInterface(usbInterface, true)

                    for (j in 0 until usbInterface.endpointCount) {
                        val endpoint = usbInterface.getEndpoint(j)
                        if (endpoint.direction == UsbConstants.USB_DIR_OUT) {
                            val transferred = connection.bulkTransfer(endpoint, data, data.size, 5000)
                            connection.releaseInterface(usbInterface)
                            connection.close()
                            return transferred > 0
                        }
                    }
                    connection.releaseInterface(usbInterface)
                }
                connection.close()
            }
        }
        return false
    }

    private fun buildReceiptBytes(order: Order, items: List<OrderItem>, kickDrawer: Boolean): ByteArray {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
        val timeStr = sdf.format(Date(order.timestamp))

        val stream = ByteArrayOutputStream()

        stream.write(ESC_INIT)
        if (kickDrawer) {
            stream.write(DRAWER_KICK)
        }

        stream.write(ESC_ALIGN_CENTER)
        stream.write(ESC_DOUBLE_SIZE)
        stream.write("六猫餐饮\n".toByteArray(charset("GBK")))
        stream.write(ESC_NORMAL)
        stream.write("--- 结账收款小票 ---\n\n".toByteArray(charset("GBK")))

        stream.write(ESC_ALIGN_LEFT)
        stream.write("单号: ${order.orderNo}\n".toByteArray(charset("GBK")))
        stream.write("场景/桌号: ${order.tableName}\n".toByteArray(charset("GBK")))
        stream.write("收款账号: ${order.cashierName}\n".toByteArray(charset("GBK")))
        stream.write("结账时间: $timeStr\n".toByteArray(charset("GBK")))
        stream.write("--------------------------------\n".toByteArray(charset("GBK")))

        stream.write(String.format("%-14s %-4s %-6s\n", "品名", "数量", "金额").toByteArray(charset("GBK")))
        for (item in items) {
            val name = if (item.productName.length > 7) item.productName.substring(0, 6) + ".." else item.productName
            stream.write(String.format("%-14s %-4d ￥%-6.2f\n", name, item.quantity, item.price * item.quantity).toByteArray(charset("GBK")))
            if (item.discountNote.isNotEmpty()) {
                stream.write("  ↳ 优惠: ${item.discountNote}\n".toByteArray(charset("GBK")))
            }
        }

        stream.write("--------------------------------\n".toByteArray(charset("GBK")))

        if (order.discountAmount > 0) {
            stream.write("原价总计: ￥${String.format("%.2f", order.originalAmount)}\n".toByteArray(charset("GBK")))
            stream.write("优惠让利: -￥${String.format("%.2f", order.discountAmount)} (${order.discountNote})\n".toByteArray(charset("GBK")))
        }

        stream.write(ESC_DOUBLE_HEIGHT)
        stream.write("实收金额: ￥${String.format("%.2f", order.totalAmount)}\n".toByteArray(charset("GBK")))
        stream.write(ESC_NORMAL)

        stream.write("支付方式: ${order.payType}\n".toByteArray(charset("GBK")))
        stream.write("================================\n".toByteArray(charset("GBK")))

        stream.write(ESC_ALIGN_CENTER)
        stream.write("谢谢惠顾，欢迎再次光临！\n\n\n\n".toByteArray(charset("GBK")))
        stream.write(ESC_CUT_PAPER)

        return stream.toByteArray()
    }

    fun buildTestReceiptBytes(): ByteArray {
        val testOrder = Order(
            orderNo = "TEST" + System.currentTimeMillis().toString().takeLast(6),
            originalAmount = 38.0,
            discountAmount = 3.8,
            totalAmount = 34.2,
            payType = "测试现金",
            cashierName = "管理员",
            tableName = "测试A01桌",
            discountNote = "9折测试"
        )
        val testItems = listOf(
            OrderItem(orderId = 0, productId = 1, productName = "六猫招牌炒肉", originalPrice = 38.0, price = 34.2, quantity = 1, discountNote = "9折")
        )
        return buildReceiptBytes(testOrder, testItems, kickDrawer = true)
    }
}