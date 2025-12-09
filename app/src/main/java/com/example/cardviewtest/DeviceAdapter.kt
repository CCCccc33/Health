package com.example.cardviewtest

import android.app.Notification
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import kotlin.concurrent.thread

class Device(val deviceName: String,var deviceState: String,val blueToothImage: Int){}

class DeviceAdapter(val context: Context,val deviceList: List<Device>,
    val onDeviceClicked:(Int, Int, BluetoothDevice)-> Unit) :
    RecyclerView.Adapter<DeviceAdapter.ViewHolder>(){
    val CONNECT_START = 10086
    val CONNECT_END = 10089
    private var mDeviceList :MutableList<BluetoothDevice> = mutableListOf<BluetoothDevice>()
    private lateinit var curDevice : BluetoothDevice
    /*
    * 清空设备列表
    * */
    fun clearList(){
        mDeviceList.clear()
        Log.d("DeviceAdapter","clearList成功")
        notifyDataSetChanged()
    }
    fun addDevice(bluetoothDevice: BluetoothDevice): Boolean{
        for (device in mDeviceList){
            if (device.address.equals(bluetoothDevice.address))
                return false
        }
        mDeviceList.add(bluetoothDevice)
        return true
    }
    inner class ViewHolder(view: View): RecyclerView.ViewHolder(view){
        val deviceName: TextView = view.findViewById(R.id.device_name)
        val deviceStatue: TextView = view.findViewById(R.id.device_statue)
        val blueToothIcon: ImageView = view.findViewById(R.id.bluetooth_icon)
        init {
            itemView.setOnClickListener {
                val position = adapterPosition  // 获取当前点击的item位置
                if (position == RecyclerView.NO_POSITION) return@setOnClickListener
                curDevice = mDeviceList[position]
                val device = deviceList[position]
                if (device.deviceState == "未连接"){
                    onDeviceClicked(CONNECT_START,position,mDeviceList[position])
                    Toast.makeText(context,"正在连接设备...", Toast.LENGTH_SHORT).show()
                }else{
                    onDeviceClicked(CONNECT_END,position,mDeviceList[position])
                    Toast.makeText(context,"设备已断开", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.device_item,parent,false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val device = deviceList[position]
        holder.deviceName.text = device.deviceName
        holder.deviceStatue.text = device.deviceState
        Glide.with(context).load(device.blueToothImage).into(holder.blueToothIcon)
    }

    override fun getItemCount(): Int = deviceList.size
}