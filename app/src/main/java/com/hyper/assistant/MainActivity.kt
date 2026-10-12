package com.hyper.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.hyper.assistant.network.GephVpnController
import com.hyper.assistant.network.SmartProxyManager
import com.hyper.assistant.service.FloatingOverlayService
import kotlinx.coroutines.launch

/**
 * 超灵助手主配置页面 - 纯粹专精 Google Gemini 3.8 Flash 旗舰版
 */
class MainActivity : AppCompatActivity() {

    private lateinit var tvVpnStatus: TextView
    private lateinit var btnAccessibility: Button
    private lateinit var btnOverlayPermission: Button
    private lateinit var btnBatteryOptimization: Button
    private lateinit var btnLaunchGeph: Button
    private lateinit var btnTestConnectivity: Button
    private lateinit var btnSaveConfig: Button

    private lateinit var etModelName: TextInputEditText
    private lateinit var etApiKey: TextInputEditText
    private lateinit var etCustomProxyBase: TextInputEditText

    private var vpnCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        loadPreferences()
        setupListeners()
        updateVpnDisplay()
        requestRuntimePermissions()
    }

    private fun requestRuntimePermissions() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), 1001)
        }
    }

    private fun initViews() {
        tvVpnStatus = findViewById(R.id.tvVpnStatus)
        btnAccessibility = findViewById(R.id.btnAccessibility)
        btnOverlayPermission = findViewById(R.id.btnOverlayPermission)
        btnBatteryOptimization = findViewById(R.id.btnBatteryOptimization)
        btnLaunchGeph = findViewById(R.id.btnLaunchGeph)
        btnTestConnectivity = findViewById(R.id.btnTestConnectivity)
        btnSaveConfig = findViewById(R.id.btnSaveConfig)

        etModelName = findViewById(R.id.etModelName)
        etApiKey = findViewById(R.id.etApiKey)
        etCustomProxyBase = findViewById(R.id.etCustomProxyBase)
    }

    private fun loadPreferences() {
        val prefs = getSharedPreferences("hyper_assistant_config", Context.MODE_PRIVATE)
        val defaultModel = "gemini-3.8-flash"
        etModelName.setText(prefs.getString("gemini_model_name", prefs.getString("model_name", defaultModel)))
        
        val key = prefs.getString("gemini_api_key", prefs.getString("api_key", ""))
        etApiKey.setText(key)
        etCustomProxyBase.setText(SmartProxyManager.getSavedCustomProxyBase(this))
    }

    private fun setupListeners() {
        // 1. 无障碍权限跳转
        btnAccessibility.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "请在列表中找到「超灵助手」并开启", Toast.LENGTH_LONG).show()
        }

        // 2. 悬浮窗权限跳转 (兼容小米应用详情管理)
        btnOverlayPermission.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!Settings.canDrawOverlays(this)) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                    Toast.makeText(this, "请开启「允许显示在其他应用上层」", Toast.LENGTH_LONG).show()
                } else {
                    // 如果已开启标准悬浮窗，引导检查小米专属的“后台弹出界面”
                    openMiuiAppPermissions()
                }
            }
        }

        // 3. 电池优化与后台锁定
        btnBatteryOptimization.setOnClickListener {
            openMiuiAppPermissions()
        }

        // 4. 迷雾通启动联动
        btnLaunchGeph.setOnClickListener {
            val launched = GephVpnController.launchGeph(this)
            if (!launched) {
                Toast.makeText(this, "未检测到迷雾通客户端，请检查是否已安装", Toast.LENGTH_SHORT).show()
            }
        }

        // 5. 网络连通性测试 (Ping API)
        btnTestConnectivity.setOnClickListener {
            savePreferences()
            Toast.makeText(this, "正在探测 Gemini 网络路由连通性...", Toast.LENGTH_SHORT).show()
            lifecycleScope.launch {
                val result = SmartProxyManager.testConnection(this@MainActivity)
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("网络路由诊断报告")
                    .setMessage(result.second)
                    .setPositiveButton("知道了", null)
                    .show()
            }
        }

        // 6. 保存配置并立即测试
        btnSaveConfig.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "⚠️ 请先开启悬浮窗权限（第2个按钮），否则无法显示流光胶囊！", Toast.LENGTH_LONG).show()
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
                return@setOnClickListener
            }

            savePreferences()
            Toast.makeText(this, "配置已保存，正在启动超灵助手胶囊浮层...", Toast.LENGTH_SHORT).show()
            startTestOverlay()
        }
    }

    private fun openMiuiAppPermissions() {
        try {
            // 尝试直接进入小米系统权限详情页
            val intent = Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                putExtra("extra_pkgname", packageName)
            }
            startActivity(intent)
            Toast.makeText(this, "提示：请确保「显示悬浮窗」与「后台弹出界面」均已允许", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            Toast.makeText(this, "请在权限管理中允许「悬浮窗」与「自启动」", Toast.LENGTH_LONG).show()
        }
    }

    private fun savePreferences() {
        val modelName = etModelName.text?.toString()?.trim().orEmpty()
        val apiKey = etApiKey.text?.toString()?.trim().orEmpty()
        val customProxy = etCustomProxyBase.text?.toString()?.trim().orEmpty()

        val prefs = getSharedPreferences("hyper_assistant_config", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("use_gemini", true)
            .putString("gemini_model_name", if (modelName.isNotEmpty()) modelName else "gemini-3.8-flash")
            .putString("model_name", if (modelName.isNotEmpty()) modelName else "gemini-3.8-flash")
            .putString("gemini_api_key", apiKey)
            .putString("api_key", apiKey)
            .apply()

        SmartProxyManager.saveCustomProxyBase(this, customProxy)
    }

    private fun startTestOverlay() {
        val intent = Intent(this, FloatingOverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun updateVpnDisplay() {
        val isConnected = GephVpnController.isVpnConnected(this)
        val hasGeph = GephVpnController.isGephInstalled(this)

        val text = buildString {
            append("VPN 状态：")
            if (isConnected) {
                append("✅ 已连通外网通道")
            } else {
                append("⚠️ 未开启 (将使用免翻墙反代通道)")
            }
            if (hasGeph) {
                append(" (检测到迷雾通客户端)")
            }
        }
        tvVpnStatus.text = text
    }

    override fun onResume() {
        super.onResume()
        updateVpnDisplay()
        vpnCallback = GephVpnController.registerVpnCallback(this) {
            runOnUiThread { updateVpnDisplay() }
        }
    }

    override fun onPause() {
        super.onPause()
        GephVpnController.unregisterCallback(this, vpnCallback)
    }
}
