package com.example.cardviewtest

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {
    val fruits = mutableListOf(
        Fruit("心率","bpm",R.drawable.ic_action_heart_rate,"--"),
    Fruit("血压","mmHg",R.drawable.ic_action_blood_pressure,"-/-"),
    Fruit("体温","℃",R.drawable.ic_action_temperture,"--"),
    Fruit("血氧","%",R.drawable.ic_action_blood_oxygen,"--"),
    Fruit("皮肤状态","",R.drawable.ic_action_skin,"--"),
    Fruit("肺活量","mL",R.drawable.ic_vital_capacity,"--")
    )
    private val fruitList = ArrayList<Fruit>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        fruitInit()
        val layoutManager = GridLayoutManager(this,2)
        val recyclerView : RecyclerView = findViewById(R.id.recycleView)
        recyclerView.layoutManager = layoutManager
        val adapter = FruitAdapter(this,fruitList)
        recyclerView.adapter = adapter
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main,menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.addDevice){
            Toast.makeText(this,"开始搜索设备!", Toast.LENGTH_SHORT).show()
            val intent = Intent("com.example.cardviewtest.DEVICESCAN_START")
            startActivity(intent)
        }
        return super.onOptionsItemSelected(item)
    }

    private fun fruitInit(){
        fruitList.clear()
//        repeat(14){
//            val index = (0 until fruits.size).random()
//            fruitList.add(fruits[index])
//        }
        for (i in 0 until fruits.size){
            fruitList.add(fruits[i])
        }
    }
}