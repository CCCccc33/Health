package com.example.cardviewtest

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.service.credentials.Action
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class Fruit(val name: String, val description: String,val imageId: Int,val data: String) {

}
class FruitAdapter(val context: Context,val fruitList: List<Fruit>) :
    RecyclerView.Adapter<FruitAdapter.ViewHolder>(){
        companion object{
            const val HEART_RATE = 10
        }
    inner class ViewHolder(view: View): RecyclerView.ViewHolder(view){
        val fruitName: TextView = view.findViewById(R.id.fruitName)
        val fruitImage: ImageView = view.findViewById(R.id.fruitImage)
        val description: TextView = view.findViewById(R.id.description)
        val data: TextView = view.findViewById(R.id.measurement_data)
//        val enterIcon: ImageView = view.findViewById(R.id.enter)
        init {
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION){
                    val fruit = fruitList[position]
                    Toast.makeText(context,"你点击了：$position !", Toast.LENGTH_SHORT).show()
                    val intent = Intent("com.example.cardviewtest.HEARTRATE_START")
                    intent.putExtra("type",fruit.name)
                    context.startActivity(intent)
                }

            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.fruit_item,parent,false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val fruit = fruitList[position]
        holder.fruitName.text = fruit.name
        holder.description.text = fruit.description
        holder.data.text = fruit.data
//        holder.fruitImage.setImageResource(fruit.imageId)
        Glide.with(context).load(fruit.imageId).into(holder.fruitImage)
//        Glide.with(context).load(fruit.enterIcon).into(holder.enterIcon)
    }

    override fun getItemCount(): Int = fruitList.size
    }