package world.saloris.donoff.database.heartRate

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

class HeartRateDao {
    companion object {
        const val bucket = BUCKET
        const val org = ORGANIZATION
        const val measurement = "heart_rate"
    }

    suspend fun getByUserAndPeriod(
        user: String, start: Instant, stop: Instant
    ): ArrayList<HeartRate> {
        val heartRates = ArrayList<HeartRate>()

        val fluxQuery = ("from(bucket: \"$bucket\")"
                + " |> range(start: $start, stop: $stop)"
                + " |> filter(fn: (r) => (r[\"_measurement\"] == \"$measurement\"))"
                + " |> filter(fn: (r) => r[\"_user\"] == \"$user\")")

        val client = InfluxDBClientKotlinFactory.create()
        client.use {
            val results = client.getQueryKotlinApi().query(fluxQuery)
            results
                .consumeAsFlow()
                .filter { measurement == it.measurement }
                .catch {
                    Log.e("InfluxException", "Query: ${it.cause}")
                }
                .collect {
                    val heartRate = HeartRate(user, (it.value as Long).toInt(), it.time!!)
                    heartRates.add(heartRate)
                }
        }
        return heartRates
    }

    suspend fun insert(heartRate: HeartRate): Boolean {
        val client = InfluxDBClientKotlinFactory.create()

        client.use {
            val writeApi = client.getWriteKotlinApi()
            try {
                writeApi.writeMeasurement(heartRate, WritePrecision.NS)
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
            try {
                val deleteApi = client.deleteApi
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
