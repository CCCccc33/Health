package com.example.cardviewtest

class Conversion {
    /**
     * ByteArray 转十六进制字符串（模仿 TypeConversion.bytes2HexString）
     * @param bytes 要转换的字节数组（不可空）
     * @param length 要转换的长度（通常传 bytes.size，即转换全部字节）
     * @param withSpace 是否带空格分隔（默认 true，比如 "01 0A FF"；false 则为 "010AFF"）
     * @return 十六进制字符串
     */
    fun bytes2HexString(bytes: ByteArray, length: Int, withSpace: Boolean = true): String {
        if (bytes.isEmpty() || length <= 0) return ""

        val sb = StringBuilder()
        val endIndex = minOf(length, bytes.size) // 避免 length 超过数组长度

        for (i in 0 until endIndex) {
            val hex = bytes[i].toUInt().toString(16)
            if (hex.length == 1) sb.append("0")
            sb.append(hex.uppercase())
            if (withSpace && i != endIndex - 1) {
                sb.append(" ")
            }
        }
        return sb.toString()
    }

    /**
     * 十六进制字符串转 ByteArray（与 bytes2HexString 完全互逆）
     * @param hexString 输入的十六进制字符串（支持带空格/不带空格、大小写）
     * @return 转换后的 ByteArray，转换失败返回空数组
     */
    fun hexString2Bytes(hexString: String): ByteArray {
        // 1. 预处理：去除字符串中的所有空格，转为大写（兼容大小写输入）
        val cleanHex = hexString.replace(" ", "").uppercase()

        // 2. 边界校验：空字符串或长度为奇数（十六进制必须两两成对）直接返回空数组
        if (cleanHex.isEmpty() || cleanHex.length % 2 != 0) {
            return ByteArray(0)
        }

        val byteArray = ByteArray(cleanHex.length / 2)
        for (i in byteArray.indices) {
            // 截取当前字节对应的两位十六进制字符（如 "A3" 截取第0-1位、2-3位...）
            val hexChunk = cleanHex.substring(i * 2, (i + 1) * 2)
            // 转换为16进制整数，再转为字节（处理负数：toByte() 自动适配补码）
            byteArray[i] = hexChunk.toUInt(16).toByte()
        }

        return byteArray
    }
    /**
     * 从长度为 1 的 ByteArray 中获取单个 Byte
     * @param byteArray 输入的字节数组（需确保长度为 1）
     * @return 数组中的唯一 Byte，若数组长度≠1 则返回 null
     */
    fun byteArrayToSingleByte(byteArray: ByteArray): Byte? {
        // 校验数组长度，避免数组越界
        return if (byteArray.size == 1) {
            byteArray[0] // 直接取第 0 个元素
        } else {
            null // 长度不符时返回 null（或抛出异常，根据业务调整）
        }
    }
}