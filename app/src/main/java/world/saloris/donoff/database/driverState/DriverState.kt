package world.saloris.donoff.database.driverState

import com.influxdb.annotations.Column
import com.influxdb.annotations.Measurement
import java.time.Instant

@Measurement(name = "DriverStateB")
data class DriverState(
    @Column(tag = true) val _user: String,
    @Column val blink: Int,
    @Column val totalBlink: Int, //
    @Column val rightEye: String,
    @Column val leftEye: String,
    @Column val ear: Float,
    @Column val mar: Float,
    @Column val moe: Float,
    @Column val face: String,
    @Column(timestamp = true) val time: Instant
) {
    constructor (
        _user: String,
        blink: Int,
        totalBlink: Int, //
        rightEye: String,
        leftEye: String,
        ear: Float,
        mar: Float,
        moe: Float,
        face: String
    ) : this(_user, blink, totalBlink, rightEye, leftEye, ear, mar, moe, face, Instant.now())
}