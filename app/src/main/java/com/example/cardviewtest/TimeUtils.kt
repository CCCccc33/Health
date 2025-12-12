package com.example.cardviewtest

import java.text.SimpleDateFormat
import java.util.*

object TimeUtils {
    private val sdf by lazy {
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
        format.timeZone = TimeZone.getDefault()
        format
    }
    // 获取当前时间戳（毫秒级）
    fun getCurrentTimestamp(): Long = System.currentTimeMillis()

    // 时间戳转格式化字符串（yyyy-MM-dd HH:mm:ss）
    fun timestampToFormat(timestamp: Long): String = sdf.format(Date(timestamp))

    // 解析时间字符串为时间戳（反向操作，可选）
    fun formatToTimestamp(timeStr: String): Long = sdf.parse(timeStr).time

    // 从时间戳解析年/月/周/日（填充HealthData的year/month/week/day）
    fun parseTimeFields(timestamp: Long): TimeFields {
        val calendar = Calendar.getInstance().apply {
            time = Date(timestamp)
        }
        return TimeFields(
            year = calendar.get(Calendar.YEAR),
            month = calendar.get(Calendar.MONTH) + 1, // 月份从0开始，+1转为1-12
            week = calendar.get(Calendar.WEEK_OF_MONTH), // 当月第几周
            day = calendar.get(Calendar.DAY_OF_MONTH), // 当月第几天
            hour = calendar.get(Calendar.HOUR_OF_DAY), // 24小时制的小时（0-23）
            minute = calendar.get(Calendar.MINUTE) // 分钟（0-59）
        )
    }

    // 封装年/月/周/日的实体
    data class TimeFields(
        val year: Int,
        val month: Int,
        val week: Int,
        val day: Int,
        val hour: Int,
        val minute: Int
    )
}