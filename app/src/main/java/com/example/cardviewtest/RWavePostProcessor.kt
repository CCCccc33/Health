package com.example.cardviewtest

class RWavePostProcessor(
    private val sampleRate: Int,
    private val windowSizeMs: Int = 100, // 200ms窗口验证R波
    private val maxDuplicateIntervalMs: Int = 500 // 100ms内重复点去重
) {

    /**
     * 后处理：保留真实R波，过滤误检
     */
    fun postProcessRWaveIndices(rawRWaveIndices: List<Int>): List<Int> {
        if (rawRWaveIndices.size < 2) return rawRWaveIndices

        val windowSize = (sampleRate * windowSizeMs / 1000.0).toInt()
        val maxDuplicateInterval = (sampleRate * maxDuplicateIntervalMs / 1000.0).toInt()

        val validRPeaks = mutableListOf<Int>()

        for (idx in rawRWaveIndices) {
            // 1. 去重：跳过100ms内的重复点
            if (validRPeaks.isNotEmpty() && idx - validRPeaks.last() < maxDuplicateInterval) {
                continue
            }

            // 2. 局部窗口验证：当前点是窗口内的最大值（确保是真实R波）
            val start = maxOf(0, idx - windowSize / 2)
            val end = minOf(rawRWaveIndices.last(), idx + windowSize / 2)
            val localPeaks = rawRWaveIndices.filter { it in start..end }
            if (localPeaks.isNotEmpty() && idx == localPeaks.maxOrNull()) {
                validRPeaks.add(idx)
            }
        }

        println("后处理完成：原始R波${rawRWaveIndices.size}个 → 有效R波${validRPeaks.size}个")
        return validRPeaks
    }

    /**
     * 计算心率（不限制RR间期，适配你的数据）
     */
    fun calculateHeartRate(validRWaveIndices: List<Int>): Double? {
        if (validRWaveIndices.size < 2) return null

        // 计算所有RR间期
        val rrIntervals = validRWaveIndices.windowed(2, 1) {
            (it[1] - it[0]) / sampleRate.toDouble()
        }
        // 取正常范围的RR间期（放宽到0.3~2.0秒）
        val validIntervals = rrIntervals.filter { it in 0.3..2.0 }
        if (validIntervals.isEmpty()) return null

        val avgRR = validIntervals.average()
        return String.format("%.1f", 60.0 / avgRR).toDouble()
    }
}
