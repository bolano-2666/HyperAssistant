package com.hyper.assistant

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.hyper.assistant.network.GephVpnController
import com.hyper.assistant.network.SmartProxyManager
import com.hyper.assistant.service.FloatingOverlayService

class MainActivity : AppCompatActivity() {

    private lateinit var tvVpnStatus: TextView
    private lateinit var btnAccessibility: Button
    private lateinit var btnOverlayPermission: Button
    private lateinit var btnBatteryOptimization: Button
    private lateinit var btnLaunchGeph: Button
    private lateinit var btnSaveConfig: Button

    private lateinit var radioGroupModel: RadioGroup
    private lateinit var rbGemini: RadioButton
    private lateinit var rbOpenAi: RadioButton
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
    }

    private fun initViews() {
        tvVpnStatus = findViewById(R.id.tvVpnStatus)
        btnAccessibility = findViewById(R.id.btnAccessibility)
        btnOverlayPermission = findViewById(R.id.btnOverlayPermission)
        btnBatteryOptimization = findViewById(R.id.btnBatteryOptimization)
        btnLaunchGeph = findViewById(R.id.btnLaunchGeph)
        btnSaveConfig = findViewById(R.id.btnSaveConfig)

        radioGroupModel = findViewById(R.id.radioGroupModel)
        rbGemini = findViewById(R.id.rbGemini)
        rbOpenAi = findViewById(R.id.rbOpenAi)
        etModelName = findViewById(R.id.etModelName)
        etApiKey = findViewById(R.id.etApiKey)
        etCustomProxyBase = findViewById(R.id.etCustomProxyBase)
    }

    private fun loadPreferences() {
        val prefs = getSharedPreferences("hyper_assistant_config", Context.MODE_PRIVATE)
        val isGemini = prefs.getBoolean("use_gemini", true)
        rbGemini.isChecked = isGemini
        rbOpenAi.isChecked = !isGemini

        val defaultModel = if (isGemini) "gemini-3.1-pro" else "gpt-4o"
        etModelName.setText(prefs.getString("model_name", defaultModel))
        etApiKey.setText(prefs.getString("api_key", ""))
        etCustomProxyBase.setText(SmartProxyManager.getSavedCustomProxyBase(this))
    }

    private fun setupListeners() {
        radioGroupModel.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.rbGemini) {
                if (etModelName.text.toString().contains("gpt", ignoreCase = true)) {
                    etModelName.setText("gemini-3.1-pro")
                }
            } else {
                if (etModelName.text.toString().contains("gemini", ignoreCase = true)) {
                    etModelName.setText("gpt-4o")
                }
            }
        }

        // 1. 无障碍权限跳转
        btnAccessibility.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "请在列表中找到「超灵助手」并开启", Toast.LENGTH_LONG).show()
        }

        // 2. 悬浮窗权限跳转
        btnOverlayPermission.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!Settings.canDrawOverlays(this)) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                } else {
                    Toast.makeText(this, "悬浮窗权限已就绪！", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // 3. 电池优化与后台锁定
        btnBatteryOptimization.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                startActivity(intent)
                Toast.makeText(this, "建议将本应用设置为「无限制」以防 HyperOS 后台查杀", Toast.LENGTH_LONG).show()
            }
        }

        // 4. 迷雾通启动联动
        btnLaunchGeph.setOnClickListener {
            val launched = GephVpnController.launchGeph(this)
            if (!launched) {
                Toast.makeText(this, "未检测到迷雾通客户端，请检查是否已安装", Toast.LENGTH_SHORT).show()
            }
        }

        // 5. 保存配置并立即测试
        btnSaveConfig.setOnClickListener {
            savePreferences()
            Toast.makeText(this, "配置已保存，正在启动 Siri 浮层测试...", Toast.LENGTH_SHORT).show()
            startTestOverlay()
        }
    }

    private fun savePreferences() {
        val isGemini = rbGemini.isChecked
        val modelName = etModelName.text?.toString()?.trim().orEmpty()
        val apiKey = etApiKey.text?.toString()?.trim().orEmpty()
        val customProxy = etCustomProxyBase.text?.toString()?.trim().orEmpty()

        val prefs = getSharedPreferences("hyper_assistant_config", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("use_gemini", isGemini)
            .putString("model_name", modelName)
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
                append("⚠️ 未开启 (将自动使用免翻墙反向代理)")
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
