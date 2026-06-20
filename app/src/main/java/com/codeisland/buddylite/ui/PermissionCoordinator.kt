package com.codeisland.buddylite.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

class PermissionCoordinator(private val context: Context) {

    data class PermissionStatus(
        val bluetoothScan: Boolean = false,
        val bluetoothConnect: Boolean = false,
        val bluetoothAdvertise: Boolean = false,
        val fineLocation: Boolean = false,
        val notifications: Boolean = false,
        val foregroundService: Boolean = true
    ) {
        val allGranted: Boolean
            get() = bluetoothScan && bluetoothConnect && bluetoothAdvertise && notifications &&
                (if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) fineLocation else true)
    }

    fun checkAll(): PermissionStatus {
        return PermissionStatus(
            bluetoothScan = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                hasPermission(Manifest.permission.BLUETOOTH_SCAN)
            } else true,
            bluetoothConnect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
            } else true,
            bluetoothAdvertise = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                hasPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
            } else true,
            fineLocation = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
                hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            } else true,
            notifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                hasPermission(Manifest.permission.POST_NOTIFICATIONS)
            } else true
        )
    }

    fun getRequiredPermissions(): List<String> {
        return buildList {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    fun getMissingPermissions(): List<String> {
        return getRequiredPermissions().filter { !hasPermission(it) }
    }

    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
        context.startActivity(intent)
    }

    fun openBatteryOptimizationSettings() {
        try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            context.startActivity(intent)
        } catch (_: Exception) {
            openAppSettings()
        }
    }

    fun openAutostartSettings() {
        // HyperOS specific: try to open autostart settings
        try {
            val intent = Intent().apply {
                setClassName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            // Fallback to app settings
            openAppSettings()
        }
    }

    fun isHyperOS(): Boolean {
        return try {
            val clazz = Class.forName("miui.os.Build")
            true
        } catch (_: ClassNotFoundException) {
            false
        }
    }

    fun permissionLabel(permission: String): String = when (permission) {
        Manifest.permission.BLUETOOTH_SCAN -> "蓝牙扫描"
        Manifest.permission.BLUETOOTH_CONNECT -> "蓝牙连接"
        Manifest.permission.BLUETOOTH_ADVERTISE -> "蓝牙广播"
        Manifest.permission.ACCESS_FINE_LOCATION -> "位置信息（蓝牙发现需要）"
        Manifest.permission.POST_NOTIFICATIONS -> "通知权限"
        else -> permission
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
    }
}
