package com.hyper.assistant.network

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log

/**
 * 迷雾通 (Geph) 专属联动控制器与系统 VPN 状态感知器
 * 支持：
 * 1. 自动检测「迷雾通」客户端是否安装 (io.geph.android / io.geph.gephgui)
 * 2. 毫秒级感知系统 VPN 连接状态 (TRANSPORT_VPN)
 * 3. 一键呼出 / 联动唤起迷雾通应用开启外网通道
 */
object GephVpnController {

    private const val TAG = "GephVpnController"
    val GEPH_PACKAGES = listOf("io.geph.android", "io.geph.gephgui")

    /**
     * 检查手机是否安装了迷雾通
     */
    fun isGephInstalled(context: Context): Boolean {
        val pm = context.packageManager
        for (pkg in GEPH_PACKAGES) {
            try {
                pm.getPackageInfo(pkg, 0)
                return true
            } catch (e: PackageManager.NameNotFoundException) {
                // 忽略未安装
            }
        }
        return false
    }

    /**
     * 获取安装的迷雾通包名
     */
    fun getInstalledGephPackage(context: Context): String? {
        val pm = context.packageManager
        for (pkg in GEPH_PACKAGES) {
            try {
                pm.getPackageInfo(pkg, 0)
                return pkg
            } catch (e: PackageManager.NameNotFoundException) {
                // ignore
            }
        }
        return null
    }

    /**
     * 唤起手机上的迷雾通客户端
     */
    fun launchGeph(context: Context): Boolean {
        val pm = context.packageManager
        val pkg = getInstalledGephPackage(context) ?: GEPH_PACKAGES[0]
        val intent = pm.getLaunchIntentForPackage(pkg)
        return if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Log.d(TAG, "成功拉起迷雾通应用: $pkg")
            true
        } else {
            Log.w(TAG, "未找到迷雾通客户端，无法启动")
            false
        }
    }

    /**
     * 检测当前系统是否已处于 VPN 连接状态 (迷雾通或任意开启中的系统VPN)
     */
    fun isVpnConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }

    /**
     * 注册全局 VPN 状态变化回调监听
     */
    fun registerVpnCallback(context: Context, onStatusChanged: (isConnected: Boolean) -> Unit): ConnectivityManager.NetworkCallback? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return null
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "VPN 已接入/已连接")
                onStatusChanged(true)
            }

            override fun onLost(network: Network) {
                Log.d(TAG, "VPN 已断开")
                onStatusChanged(false)
            }
        }

        try {
            cm.registerNetworkCallback(request, callback)
            return callback
        } catch (e: Exception) {
            Log.e(TAG, "注册 VPN 监听异常: ${e.message}")
            return null
        }
    }

    fun unregisterCallback(context: Context, callback: ConnectivityManager.NetworkCallback?) {
        if (callback == null) return
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        try {
            cm.unregisterNetworkCallback(callback)
        } catch (e: Exception) {
            // ignore
        }
    }
}
