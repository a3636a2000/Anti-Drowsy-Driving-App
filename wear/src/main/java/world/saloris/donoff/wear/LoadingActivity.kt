package world.saloris.donoff.wear

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.AnimatedVectorDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import world.saloris.donoff.wear.databinding.ActivityLoadingBinding

/**
 * 로딩 화면 애니메이션:
 *  1. 진입: 타이틀 페이드 인 → ECG 이미지 페이드 인 → 힌트 텍스트 페이드 인
 *  2. ECG: AnimatedVectorDrawable (ic_ecg_anim) 시작 — 파형이 좌→우로 이동
 *  3. 힌트 텍스트: "연결 중" → "연결 중." → "연결 중.." → "연결 중..." 반복
 *  4. 센서에서 유효한 심박수 수신 시 MainWatchActivity 로 이동
 */
class LoadingActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var binding: ActivityLoadingBinding
    private lateinit var sensorManager: SensorManager
    private var heartRateSensor: Sensor? = null
    private var navigated = false

    // 도트 애니메이션
    private val dotFrames = arrayOf("연결 중", "연결 중.", "연결 중..", "연결 중...")
    private var dotIdx = 0
    private val dotHandler = Handler(Looper.getMainLooper())
    private val dotRunnable = object : Runnable {
        override fun run() {
            binding.tvLoadingHint.text = dotFrames[dotIdx++ % dotFrames.size]
            dotHandler.postDelayed(this, 480)
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) registerSensor()
        else {
            Toast.makeText(this, getString(R.string.permission_denied), Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoadingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)

        if (heartRateSensor == null) {
            Toast.makeText(this, getString(R.string.sensor_not_found), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        playEntryAnimation()
        checkPermissionAndStart()
    }

    // ── 진입 애니메이션 ───────────────────────────────────────────────────────

    private fun playEntryAnimation() {
        // 타이틀: 페이드 인
        binding.tvTitle.animate()
            .alpha(1f)
            .setDuration(400)
            .setStartDelay(80)
            .start()

        // ECG 파형: 페이드 인 후 AnimatedVectorDrawable 시작
        binding.ivEcg.animate()
            .alpha(1f)
            .setDuration(350)
            .setStartDelay(280)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction { startEcgAnimation() }
            .start()

        // 힌트 텍스트: 페이드 인 후 도트 애니메이션 시작
        binding.tvLoadingHint.animate()
            .alpha(1f)
            .setDuration(350)
            .setStartDelay(480)
            .withEndAction { dotHandler.post(dotRunnable) }
            .start()
    }

    // ── ECG AnimatedVectorDrawable ────────────────────────────────────────────

    private fun startEcgAnimation() {
        (binding.ivEcg.drawable as? AnimatedVectorDrawable)?.start()
    }

    // ── 센서 초기화 ───────────────────────────────────────────────────────────

    private fun checkPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            registerSensor()
        } else {
            permissionLauncher.launch(Manifest.permission.BODY_SENSORS)
        }
    }

    private fun registerSensor() {
        heartRateSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    // ── 센서 콜백 ─────────────────────────────────────────────────────────────

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_HEART_RATE) return
        val hr = event.values[0].toInt()
        if (hr > 0 && !navigated) {
            navigated = true
            navigateToMain(hr)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun navigateToMain(hr: Int) {
        // 화면 전환 시 ECG 페이드아웃 후 이동
        binding.ivEcg.animate()
            .alpha(0f).scaleX(1.3f).scaleY(1.3f)
            .setDuration(300)
            .withEndAction {
                startActivity(
                    Intent(this, MainWatchActivity::class.java)
                        .putExtra(MainWatchActivity.EXTRA_INITIAL_HR, hr)
                )
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                finish()
            }
            .start()
    }

    // ── 생명주기 ──────────────────────────────────────────────────────────────

    override fun onPause() {
        super.onPause()
        dotHandler.removeCallbacks(dotRunnable)
        sensorManager.unregisterListener(this)
    }
}
