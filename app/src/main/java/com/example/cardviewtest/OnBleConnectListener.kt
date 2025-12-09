package com.example.cardviewtest

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic

interface OnBleConnectListener {
    //连接
    fun onConnectFailure(gatt:BluetoothGatt?, bluetoothDevice: BluetoothDevice?, description: String,statues:Int)
    fun onConnectSuccess(gatt:BluetoothGatt?,bluetoothDevice: BluetoothDevice?,statues:Int)
    fun onDisConnectSuccess(gatt:BluetoothGatt?,bluetoothDevice: BluetoothDevice?,statues:Int)
    fun onDisConnecting(gatt:BluetoothGatt?,bluetoothDevice: BluetoothDevice?)
    fun onConnecting(gatt:BluetoothGatt?,bluetoothDevice: BluetoothDevice?)
    //获取服务
    fun onServiceDiscoverySucceed(gatt:BluetoothGatt?,bluetoothDevice: BluetoothDevice?,statues:Int)
    fun onServiceDiscoveryFailed(gatt:BluetoothGatt?,bluetoothDevice: BluetoothDevice?,statues: String)
    //写入
    fun onWriteSuccess(gatt: BluetoothGatt?,
                       device: BluetoothDevice?,
                       characteristic: BluetoothGattCharacteristic?,
                       bytes: String)
    fun onWriteFailure(gatt: BluetoothGatt?,
                       device: BluetoothDevice?,
                       characteristic: BluetoothGattCharacteristic?,
                       bytes: ByteArray?,
                       description: String)
    //接收
    fun onReceiveMessage(gatt: BluetoothGatt?,
                         device: BluetoothDevice?,
                         characteristic: BluetoothGattCharacteristic?,
                         bytes: ByteArray?)
    fun onReceiveError(error: Int)
    //修改Mtu值
    fun onMTUSetSuccess(mtu: Int)
    fun onMTUSetFailure(mtu: Int)
}