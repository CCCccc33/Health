package com.example.cardviewtest

object DbConstants {
    // 数据库信息
    const val DB_NAME = "HealthData.db"
    const val DB_VERSION = 1
    // 健康数据表
    const val TABLE_HEALTH = "health_data"
    // 字段名
    const val COL_ID = "data_id" // 主键（自增）
    const val COL_YEAR = "year" // 年（2025）
    const val COL_MONTH = "month" // 月（1-12）
    const val COL_WEEK = "week"
    const val COL_DAY = "day" // 日（1-31）
    const val COL_UPLOAD_TIME = "upload_time" // 精确时间（yyyy-MM-dd HH:mm:ss）
    const val COL_DATA_TYPE = "data_type" // 数据类型：1=血压，2=血糖，3=体重
    const val COL_VALUE1 = "value1" // 核心指标1（如收缩压/血糖值）
    const val COL_VALUE2 = "value2" // 核心指标2（如舒张压/体脂率）
    const val COL_VALUE3 = "value3" // 核心指标3（如心率/肌肉量）
    const val COL_REMARK = "remark" // 备注

    // 索引名
    const val IDX_TIME = "idx_health_time" // 年+月+日联合索引
    const val IDX_TIME_WITH_WEEK = "idx_health_time_week"
    const val IDX_DATA_TYPE = "idx_health_type" // 数据类型索引
}