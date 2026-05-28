package com.example.float_window

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.*
import java.io.FileInputStream

class MainActivity : ComponentActivity() {

    private var serialFd: FileInputStream? = null
    private lateinit var wm: WindowManager
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var composeView: ComposeView

    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var pitch by mutableStateOf(0)
    private var yaw by mutableStateOf(0)
    private var voltage by mutableStateOf(0.0)
    private var hex by mutableStateOf("")

    private var lastUpdateTime = 0L

    private var isFloatingVisible by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !Settings.canDrawOverlays(this)
        ) {
            val intent = android.content.Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:$packageName")
            )
            startActivity(intent)
            return
        }

        setupFloatingWindow()
        startSerialReading()

        // =========================
        // App 内控制按钮（中间）
        // =========================
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {

                    FloatingToggleButton(
                        isOn = isFloatingVisible,
                        onToggle = {
                            isFloatingVisible = !isFloatingVisible

                            if (isFloatingVisible) {
                                showFloatingWindow()
                            } else {
                                hideFloatingWindow()
                            }
                        }
                    )
                }
            }
        }
    }

    // =========================
    // 悬浮窗控制
    // =========================
    private fun showFloatingWindow() {
        try {
            wm.addView(composeView, params)
        } catch (_: Exception) {}
    }

    private fun hideFloatingWindow() {
        try {
            wm.removeView(composeView)
        } catch (_: Exception) {}
    }

    // =========================
    // 初始化悬浮窗
    // =========================
    private fun setupFloatingWindow() {

        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        composeView = ComposeView(this).apply {

            setViewTreeLifecycleOwner(this@MainActivity)
            setViewTreeSavedStateRegistryOwner(this@MainActivity)
            setViewTreeViewModelStoreOwner(this@MainActivity as ViewModelStoreOwner)

            setContent {

                MaterialTheme(colorScheme = darkColorScheme()) {

                    FloatWindowContent { dx, dy ->

                        val now = System.currentTimeMillis()

                        params.x += dx.toInt()
                        params.y += dy.toInt()

                        if (now - lastUpdateTime > 16) {
                            wm.updateViewLayout(composeView, params)
                            lastUpdateTime = now
                        }
                    }
                }
            }
        }

        val metrics = resources.displayMetrics
        val screenWidth = metrics.widthPixels

        val marginX = (screenWidth * 0.05f).toInt()
        val marginY = (resources.displayMetrics.heightPixels * 0.05f).toInt()

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

            x = screenWidth - marginX - 300
            y = marginY
        }

        // 默认开启
        showFloatingWindow()
    }

    // =========================
    // 悬浮窗 UI
    // =========================
    @Composable
    fun FloatWindowContent(
        onDrag: (dx: Float, dy: Float) -> Unit
    ) {

        Surface(color = androidx.compose.ui.graphics.Color.Transparent) {

            Box(
                modifier = Modifier
                    .width(200.dp)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount.x, dragAmount.y)
                        }
                    }
            ) {

                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor =
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                    )
                ) {

                    Column {

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.primary)
                                .padding(10.dp)
                        ) {
                            Text(
                                "小域智能",
                                color = androidx.compose.ui.graphics.Color.White
                            )
                        }

                        Column(modifier = Modifier.padding(14.dp)) {

                            Text("俯仰角: $pitch°", color = MaterialTheme.colorScheme.onSurface)
                            Text("旋转角: $yaw°", color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                "电压: %.1f V".format(voltage),
                                color = MaterialTheme.colorScheme.tertiary
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = hex,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    // =========================
    // 中间开关按钮
    // =========================
    @Composable
    fun FloatingToggleButton(
        isOn: Boolean,
        onToggle: () -> Unit
    ) {

        FloatingActionButton(
            onClick = onToggle,
            containerColor =
                if (isOn) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
            shape = androidx.compose.foundation.shape.CircleShape
        ) {
            Text(
                text = if (isOn) "ON" else "OFF",
                color = androidx.compose.ui.graphics.Color.White
            )
        }
    }

    // =========================
    // 串口读取
    // =========================
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

                            if (frameBuffer.size >= 12 &&
                                frameBuffer[0] == 0xAA.toByte() &&
                                frameBuffer[11] == 0xFF.toByte()
                            ) {

                                pitch = frameBuffer[1].toInt() and 0xFF
                                yaw = frameBuffer[2].toInt() and 0xFF
                                voltage = (frameBuffer[3].toInt() and 0xFF) / 10.0

                                hex = frameBuffer.joinToString(" ") {
                                    "%02X".format(it)
                                }

                                frameBuffer.clear()

                            } else if (frameBuffer.size > 12 ||
                                frameBuffer[0] != 0xAA.toByte()
                            ) {
                                frameBuffer.removeAt(0)
                            }
                        }

                    } else {
                        delay(50)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        mainScope.cancel()

        try {
            wm.removeView(composeView)
        } catch (_: Exception) {}

        serialFd?.close()
        SerialPort.close()
    }
}