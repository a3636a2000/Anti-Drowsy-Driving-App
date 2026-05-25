package world.saloris.donoff.util.graph

import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.*

class TimeAxisValueFormat : IndexAxisValueFormatter() {
    private val baseTimestamp =
        localDateTimeToTimestamp(LocalDateTime.of(LocalDate.now(), LocalTime.of(0, 0, 0)))

    override fun getFormattedValue(value: Float): String {
        val localDateTime = timestampToLocalDateTime(baseTimestamp!! + value.toLong())

        val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        val formatterWithDate = DateTimeFormatter.ofPattern("HH:mm:ss\nyy-MM-dd")

        return if (localDateTime!!.toLocalTime() == LocalTime.MIDNIGHT)
            formatterWithDate.format(localDateTime)
        else formatter.format(localDateTime)
    }

    fun getSpecificFormattedValue(value: Float): String {
        val localDateTime = timestampToLocalDateTime(baseTimestamp!! + value.toLong())

        val formatter = DateTimeFormatter.ofPattern("yyyy. MM. dd.\nHH:mm:ss")

        return formatter.format(localDateTime)
    }

    fun getSpecificFormattedValueInLine(value: Float): String {
        val localDateTime = timestampToLocalDateTime(baseTimestamp!! + value.toLong())

        val formatter = DateTimeFormatter.ofPattern("yyyy. MM. dd. HH:mm:ss")

        return formatter.format(localDateTime)
    }

    private fun timestampToLocalDateTime(timestamp: Long?): LocalDateTime? {
        return timestamp?.let {
            LocalDateTime.ofInstant(
                Instant.ofEpochSecond(it),
                TimeZone.getDefault().toZoneId()
            )
        }
    }

    private fun localDateTimeToTimestamp(localDateTime: LocalDateTime?): Long? {
        val zoneId = ZoneId.systemDefault()
        return localDateTime?.atZone(zoneId)?.toEpochSecond()
    }
}