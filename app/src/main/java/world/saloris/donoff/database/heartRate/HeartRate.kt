package world.saloris.donoff.database.heartRate

import com.influxdb.annotations.Column
import com.influxdb.annotations.Measurement
import java.time.Instant

@Measurement(name = "heart_rate")
data class HeartRate(
    @Column(tag = true) val _user: String,
    @Column val value: Int,
    @Column(timestamp = true) val time: Instant
) {
    constructor(_user: String, value: Int) : this(_user, value, Instant.now())
}