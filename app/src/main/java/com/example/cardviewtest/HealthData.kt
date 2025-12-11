package com.example.cardviewtest

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Entity(tableName = "HealthData")
@Parcelize
data class HealthData(
    @PrimaryKey(autoGenerate = true) val id: Long = 0, // 主键（新增时传0，插入后返回自增ID）
    val year: Int,
    val month: Int,
    val week: Int,
    val day: Int,
    val uploadTime: String, // 格式：yyyy-MM-dd HH:mm:ss
    val dataType: String, // 1=血压，2=血糖，3=体重
    val value1: Float?,
    val value2: Float?,
    val value3: Float?,
    val remark: String? = null
): Parcelable
