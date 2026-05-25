package world.saloris.donoff.database.driverState

import android.util.Log
import com.influxdb.client.InfluxDBClientFactory
import com.influxdb.client.domain.WritePrecision
import com.influxdb.client.kotlin.InfluxDBClientKotlinFactory
import com.influxdb.exceptions.InfluxException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.filter
import world.saloris.donoff.util.BUCKET
import world.saloris.donoff.util.ORGANIZATION
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId

class DriverStateDao {
    companion object {
        const val bucket = BUCKET
        const val org = ORGANIZATION
        const val measurement = "DriverStateB"
    }

    suspend fun getByUserAndPeriod(
        user: String, start: Instant, stop: Instant
    ): ArrayList<DriverState> {
        val driverStates = ArrayList<DriverState>()

        val fluxQuery = ("from(bucket: \"$bucket\")"
                + " |> range(start: $start, stop: $stop)"
                + " |> filter(fn: (r) => (r[\"_measurement\"] == \"$measurement\"))"
                + " |> filter(fn: (r) => r[\"_user\"] == \"$user\")"
                + " |> pivot(rowKey: [\"_time\"], columnKey: [\"_field\"], valueColumn: \"_value\")")

        val client = InfluxDBClientKotlinFactory.create()
        client.use {
            val results = client.getQueryKotlinApi().query(fluxQuery)
            results
                .consumeAsFlow()
                .filter { measurement == it.measurement }
                .catch { Log.e("InfluxException", "Query: ${it.cause}") }
                .collect {
                    val driverState =
                        DriverState(
                            user,
                            (it.values["blink"] as Long).toInt(),
                            (it.values["totalBlink"] as Long).toInt(),
                            it.values["rightEye"] as String,
                            it.values["leftEye"] as String,
                            (it.values["ear"] as Double).toFloat(),
                            (it.values["mar"] as Double).toFloat(),
                            (it.values["moe"] as Double).toFloat(),
                            it.values["face"] as String,
                            it.time!!
                        )
                    driverStates.add(driverState)
                }
        }

        return driverStates
    }

    suspend fun insert(driverState: DriverState): Boolean {
        val client = InfluxDBClientKotlinFactory.create()

        client.use {
            val writeApi = client.getWriteKotlinApi()
            try {
                writeApi.writeMeasurement(driverState, WritePrecision.NS)
            } catch (ie: InfluxException) {
                Log.e("InfluxException", "Insert: ${ie.cause}")
                return false
            }
        }
        return true
    }

    fun deleteAllByUser(uid: String): Boolean {
        val client = InfluxDBClientFactory.create()

        val start = OffsetDateTime.ofInstant(Instant.ofEpochSecond(0), ZoneId.of("UTC"))
        val stop = OffsetDateTime.now()

        client.use {
            val deleteApi = client.deleteApi
            try {
                deleteApi.delete(
                    start, stop, "_measurement=\"$measurement\" AND _user=\"$uid\"", bucket, org
                )
            } catch (ie: InfluxException) {
                Log.e("InfluxException", "Delete: ${ie.cause}")
                return false
            }
        }
        return true
    }
}