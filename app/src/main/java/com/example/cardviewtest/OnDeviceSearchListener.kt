package com.example.cardviewtest

import android.bluetooth.BluetoothDevice

interface OnDeviceSearchListener{
    fun onDiscoveryOutTime()
    fun onDeviceFound(device: BluetoothDevice)
}