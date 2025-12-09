package com.example.cardviewtest

enum class BluetoothAction {
    TURN_ON,         // 开启蓝牙
    TURN_OFF,        // 关闭蓝牙
    SCAN,            // 扫描蓝牙设备（经典蓝牙/BLE）
    MAKE_DISCOVERABLE, // 使设备可被发现（开启可见性）
    GET_BONDED,      // 获取已配对设备列表
    PAIR,            // 与设备配对
    UNPAIR,          // 取消设备配对
    CONNECT,         // 与已配对设备通信/连接
    ADVERTISE,        // 蓝牙广播（BLE 外设模式，可选）
    INIT   //初始
}