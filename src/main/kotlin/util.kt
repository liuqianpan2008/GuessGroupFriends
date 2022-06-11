package org.fenglin

import java.text.SimpleDateFormat
import java.util.Date


fun Long.formatSecondAsDuration (): String {
    var t = this
    var s = ""
    var temp = t % 60
    if (temp != 0L) s = "${temp}秒"
    t /= 60
    if (t == 0L) return s
    temp = t % 60
    if (temp != 0L) s = "${temp}分$s"
    t /= 60
    if (t == 0L) return s
    temp = t % 24
    if (temp != 0L) s = "${temp}时$s"
    t /= 24
    if (t == 0L) return s
    s = "${t}天$s"
    t /= 30
    if (t == 0L) return s
    s = "${t}月$s"
    return s
}

private val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm:ss")
fun Int.fromNowSecondly() = (System.currentTimeMillis() / 1000 - this).formatSecondAsDuration()
fun Int.formatAsDate() = times(1000L).formatAsDate()
fun Long.formatAsDate() = sdf.format(Date(this))!!