package com.example.float_window

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import kotlinx.coroutines.*
import java.io.FileInputStream

class MainActivity : ComponentActivity() {

    private var serialFd: FileInputStream? = null
    private lateinit var wm: WindowManager
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var floatContainer: LinearLayout
    private lateinit var header: TextView
    private lateinit var pitchText: TextView
    private lateinit var yawText: TextView
    private lateinit var voltageText: TextView
    private lateinit var hexText: TextView
    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 检查悬浮窗权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = android.content.Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:$packageName")
            )
            startActivity(intent)
            return
        }

        setupFloatingWindow()
        startSerialReading()
    }

    private fun setupFloatingWindow() {
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // 主容器，圆角+半透明
        val backgroundDrawable = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = 24f // 圆角半径
            setColor(Color.parseColor("#99121212")) // 透明度降低的半透明黑
        }

        floatContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = backgroundDrawable
            elevation = 12f
            setPadding(0, 0, 0, 0)
        }

        // Header
        header = TextView(this).apply {
            text = "小域智能"
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(24, 16, 24, 16)
            setBackgroundColor(Color.parseColor("#33000000")) // 半透明深色
        }

        // Body 容器
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 16)
        }

        // 数据文本
        pitchText = TextView(this).apply { setTextColor(Color.GREEN) }
        yawText = TextView(this).apply { setTextColor(Color.GREEN) }
        voltageText = TextView(this).apply { setTextColor(Color.parseColor("#FFA500")) }
        hexText = TextView(this).apply { setTextColor(Color.LTGRAY); textSize = 12f }

        body.addView(pitchText)
        body.addView(yawText)
        body.addView(voltageText)
        body.addView(hexText)

        floatContainer.addView(header)
        floatContainer.addView(body)

        // WindowManager 参数
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 100
        }

        wm.addView(floatContainer, params)

        // 悬浮窗拖动，仅 header 可拖动
        var lastX = 0
        var lastY = 0
        header.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.rawX.toInt()
                    lastY = event.rawY.toInt()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX.toInt() - lastX
                    val dy = event.rawY.toInt() - lastY
                    params.x += dx
                    params.y += dy
                    wm.updateViewLayout(floatContainer, params)
                    lastX = event.rawX.toInt()
                    lastY = event.rawY.toInt()
                    true
                }
                else -> false
            }
        }
    }
    private fun startSerialReading() {
        mainScope.launch(Dispatchers.IO) {
            val fd = SerialPort.open("/dev/ttyHS3", 115200, 0)
            if (fd != null) {
                serialFd = FileInputStream(fd)
                val buffer = ByteArray(128)
                val frameBuffer = mutableListOf<Byte>()

                while (isActive) {
                    val n = serialFd?.read(buffer) ?: 0
                    if (n > 0) {
                        for (i in 0 until n) {
                            val b = buffer[i]
                            frameBuffer.add(b)

                            // 检测完整帧
                            if (frameBuffer.size >= 12 &&
                                frameBuffer[0] == 0xAA.toByte() &&
                                frameBuffer[11] == 0xFF.toByte()
                            ) {
                                val pitch = frameBuffer[1].toInt() and 0xFF
                                val yaw = frameBuffer[2].toInt() and 0xFF
                                val voltage = (frameBuffer[3].toInt() and 0xFF) / 10.0
                                val hex = frameBuffer.joinToString(" ") { "%02X".format(it) }
                                frameBuffer.clear()

                                withContext(Dispatchers.Main) {
                                    pitchText.text = "俯仰角: $pitch°"
                                    yawText.text = "旋转角: $yaw°"
                                    voltageText.text = "电压: %.1f V".format(voltage)
                                    hexText.text = hex
                                }
                            } else if (frameBuffer.size > 12 || frameBuffer[0] != 0xAA.toByte()) {
                                frameBuffer.removeAt(0)
                            }
                        }
                    } else {
                        delay(50)
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    pitchText.text = "无法打开串口"
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mainScope.cancel()
        try {
            wm.removeView(floatContainer)
        } catch (_: Exception) {}
        serialFd?.close()
        SerialPort.close()
    }
}