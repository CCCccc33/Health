package com.example.cardviewtest

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class
PermissionRequest: Activity() {
    companion object{
        const val TAG = "PermissionRequest"
        const val REQUEST_PERMISSION_CODE = 1
    }
    /**
     * 根据蓝牙操作获取最小权限集合（完全遵循 Android 官方规范）
     * @param action 蓝牙操作类型（BluetoothAction 枚举）
     * @return 对应操作所需的权限数组
     */
    fun getRequiredPermissions(action: BluetoothAction): Array<String> {
        return when (action) {
            // 1. 开启/关闭蓝牙
            BluetoothAction.TURN_ON,
            BluetoothAction.TURN_OFF -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    arrayOf(Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.BLUETOOTH_SCAN)
                } else {
                    emptyArray()
                }
            }
            // 2. 扫描蓝牙设备（最核心场景，严格按官方区分）
            BluetoothAction.SCAN -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // Android 12+：仅需 BLUETOOTH_SCAN（若不推导位置，清单需加 neverForLocation）
                    arrayOf(Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.ACCESS_COARSE_LOCATION)  //
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Android 10-11：需要 BLUETOOTH_ADMIN + ACCESS_FINE_LOCATION + 可选后台定位
                    arrayOf(
                        Manifest.permission.BLUETOOTH_ADMIN,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.BLUETOOTH_SCAN
                        // 若需后台扫描，需额外添加 Manifest.permission.ACCESS_BACKGROUND_LOCATION
                    )
                } else {
                    // Android 9及以下：BLUETOOTH_ADMIN + ACCESS_FINE_LOCATION
                    arrayOf(
                        Manifest.permission.BLUETOOTH_ADMIN,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )
                }
            }
            // 3. 使设备可被发现（开启可见性）
            BluetoothAction.MAKE_DISCOVERABLE -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // Android 12+：需要 BLUETOOTH_ADVERTISE 权限（官方新增）
                    arrayOf(Manifest.permission.BLUETOOTH_ADVERTISE)
                } else {
                    // Android 11及以下：无需额外权限（通过系统Intent申请可见性）
                    emptyArray()
                }
            }
            // 4. 获取已配对设备、配对/取消配对、连接已配对设备
            BluetoothAction.GET_BONDED,
            BluetoothAction.PAIR,
            BluetoothAction.UNPAIR,
            BluetoothAction.CONNECT -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // Android 12+：统一需要 BLUETOOTH_CONNECT 权限
                    arrayOf(Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN)
                } else {
                    // Android 11及以下：需要 BLUETOOTH 权限
                    arrayOf(Manifest.permission.BLUETOOTH)
                }
            }
            // 5. BLE 广播（外设模式，若应用需作为 BLE 外设时使用）
            BluetoothAction.ADVERTISE -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // Android 12+：需要 BLUETOOTH_ADVERTISE 权限
                    arrayOf(Manifest.permission.BLUETOOTH_ADVERTISE)
                } else {
                    // Android 11及以下：无需额外权限（BLE 广播默认支持）
                    emptyArray()
                }
            }
            BluetoothAction.INIT -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // Android 12+：扫描 + 连接
                    arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION,

                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.ACCESS_WIFI_STATE,
                        Manifest.permission.INTERNET)
                } else {
                    // Android 12 以下：蓝牙 + 位置
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N){
                        arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                    }
                    else arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.ACCESS_FINE_LOCATION)
                }
            }

        }
    }
}