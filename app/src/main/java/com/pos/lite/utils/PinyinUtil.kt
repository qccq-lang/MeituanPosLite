package com.pos.lite.utils

object PinyinUtil {
    private val SEC_POS_VALUE = intArrayOf(
        1601, 1637, 1833, 2078, 2274, 2302, 2433, 2594, 2787,
        3106, 3212, 3472, 3635, 3722, 3730, 3858, 4027, 4086,
        4390, 4558, 4684, 4925, 5249, 5600
    )
    private val FIRST_LETTER = charArrayOf(
        'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'j',
        'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's',
        't', 'w', 'x', 'y', 'z'
    )

    private fun getPinYinHeadChar(str: String): String {
        val convert = StringBuilder()
        for (c in str) {
            if (c.code in 0x4E00..0x9FA5) {
                convert.append(getFirstCharOfChinese(c))
            } else {
                convert.append(c)
            }
        }
        return convert.toString().lowercase()
    }

    private fun getFirstCharOfChinese(c: Char): Char {
        try {
            val bytes = c.toString().toByteArray(charset("GBK"))
            if (bytes.size < 2) return c
            val sect = bytes[0].toInt() and 0xFF
            val pos = bytes[1].toInt() and 0xFF
            val secPosValue = (sect - 0xA0) * 100 + (pos - 0xA0)
            for (i in 0 until 23) {
                if (secPosValue >= SEC_POS_VALUE[i] && secPosValue < SEC_POS_VALUE[i + 1]) {
                    return FIRST_LETTER[i]
                }
            }
        } catch (e: Exception) {
            // ignore
        }
        return c
    }

    // 匹配中文或拼音首字母 (如输入 "hmj" 匹配 "黄焖鸡米饭")
    fun matches(dishName: String, query: String): Boolean {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return true
        if (dishName.lowercase().contains(q)) return true
        val initials = getPinYinHeadChar(dishName)
        return initials.contains(q)
    }
}