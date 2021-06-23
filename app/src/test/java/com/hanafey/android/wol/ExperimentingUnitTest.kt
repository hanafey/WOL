package com.hanafey.android.wol

import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExperimentingUnitTest {
    @Test
    fun short_date_time_formatting() {
        val now = Instant.now()
        val localDT = LocalDateTime.ofInstant(now, ZoneId.systemDefault())
        val formatter = DateTimeFormatter.ofPattern("hh:mm:ss a")
        println(formatter.format(localDT))
    }
}
