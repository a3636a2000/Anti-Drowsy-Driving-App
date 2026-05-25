package world.saloris.donoff.wear

import android.animation.*
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.*
import android.view.animation.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import world.saloris.donoff.wear.databinding.ActivityMainWatchBinding

/**
 * 메인 화면 애니메이션:
 *  1. 진입: 타이틀 위→아래 슬라이드 + 콘텐츠 스케일 업 + 페이드 인
 *  2. 심박수 수신마다: 심장 스케일 박동 (scale 1→1.3→1)
 *  3. BPM 숫자: ValueAnimator로 이전 값→새 값 부드럽게 카운팅
 *  4. 졸음 감지 시:
 *     - flash_overlay 노란색 반짝임 (alpha 0.7→0)
 *     - 텍스트·아이콘 색상 ArgbEvaluator 부드러운 전환 (흰색↔노란색)
 *     - 졸음 상태 동안 심장 아이콘이 빠르게 박동
 */
class MainWatchActivity : AppCompatActivity(), SensorEventListener {

    companion object {
        const val EXTRA_INITIAL_HR = "initial_hr"
        private const val MAX_WINDOW = 20
        private const val MIN_READINGS = 5
        private const val DROWSY_RATIO = 0.15f
        private const val ALERT_COOLDOWN_MS = 30_000L

        private const val PULSE_NORMAL_DURATION = 160L
        private const val PULSE_DROWSY_DURATION = 100L   // 졸음 시 더 빠른 박동
    }

    private lateinit var binding: ActivityMainWatchBinding
    private lateinit var sensorManager: SensorManager
    private var heartRateSensor: Sensor? = null

    private val hrWindow = mutableListOf<Float>()
    private var lastAlertMs = 0L
    private var isDrowsy = false
    private var displayedBpm = 0

    private val uiHandler = Handler(Looper.getMainLooper())
    private val toneGenerator by lazy { ToneGenerator(AudioManager.STREAM_ALARM, 100) }
    private val vibrator: Vibrator by lazy { resolveVibrator() }

    private var colorAnimator: ValueAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainWatchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)

        playEntryAnimation()

        val initialHr = intent.getIntExtra(EXTRA_INITIAL_HR, 0)
        if (initialHr > 0) {
            addReading(initialHr.toFloat())
            uiHandler.postDelayed({ updateDisplay(initialHr, false) }, 600)
        }
    }

    override fun onResume() {
        super.onResume()
        heartRateSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onDestroy() {
        colorAnimator?.cancel()
        toneGenerator.release()
        super.onDestroy()
    }

    // ── 진입 애니메이션 ───────────────────────────────────────────────────────

    private fun playEntryAnimation() {
        // 타이틀: 위→아래 슬라이드
        binding.tvScreenTitle.animate()
            .alpha(1f).translationY(0f)
            .setDuration(450)
            .setStartDelay(100)
            .setInterpolator(DecelerateInterpolator())
            .start()

        // BPM 콘텐츠: 스케일 업 + 페이드
        binding.contentContainer.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(500)
            .setStartDelay(250)
            .setInterpolator(OvershootInterpolator(1.1f))
            .start()
    }

    // ── 센서 콜백 ─────────────────────────────────────────────────────────────

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_HEART_RATE) return
        val hr = event.values[0].toInt()
        if (hr <= 0) return

        addReading(event.values[0])
        val drowsy = checkDrowsiness(event.values[0])
        uiHandler.post { updateDisplay(hr, drowsy) }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // ── 심박수 로직 ───────────────────────────────────────────────────────────

    private fun addReading(hr: Float) {
        hrWindow.add(hr)
        if (hrWindow.size > MAX_WINDOW) hrWindow.removeAt(0)
    }

    private fun checkDrowsiness(current: Float): Boolean {
        if (hrWindow.size < MIN_READINGS) return false
        val avg = hrWindow.average().toFloat()
        if (current < avg * (1f - DROWSY_RATIO)) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastAlertMs >= ALERT_COOLDOWN_MS) {
                lastAlertMs = now
                triggerAlert()
            }
            return true
        }
        return false
    }

    // ── 경보 ─────────────────────────────────────────────────────────────────

    private fun triggerAlert() {
        vibrator.vibrate(
            VibrationEffect.createWaveform(longArrayOf(0, 400, 150, 400, 150, 600), -1)
        )
        toneGenerator.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 1500)
        uiHandler.post { flashScreen() }
    }

    // ── UI 업데이트 ───────────────────────────────────────────────────────────

    private fun updateDisplay(hr: Int, drowsy: Boolean) {
        animateBpmChange(hr)

        if (drowsy != isDrowsy) {
            isDrowsy = drowsy
            animateColorTransition(drowsy)
        }

        animateHeartBeat(if (isDrowsy) PULSE_DROWSY_DURATION else PULSE_NORMAL_DURATION)
    }

    /**
     * BPM 숫자를 이전 값에서 새 값으로 부드럽게 카운팅합니다.
     */
    private fun animateBpmChange(to: Int) {
        val from = displayedBpm
        if (from == 0) {
            binding.tvBpm.text = to.toString()
            displayedBpm = to
            return
        }
        if (from == to) return

        ValueAnimator.ofInt(from, to).apply {
            duration = if (kotlin.math.abs(to - from) > 8) 450L else 250L
            interpolator = DecelerateInterpolator()
            addUpdateListener { binding.tvBpm.text = it.animatedValue.toString() }
            start()
        }
        displayedBpm = to
    }

    /**
     * 심장 아이콘을 scale 1→scaleTo→1로 박동시킵니다.
     */
    private fun animateHeartBeat(durationMs: Long) {
        binding.ivHeart.clearAnimation()
        val pulse = ScaleAnimation(
            1f, 1.28f, 1f, 1.28f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = durationMs
            repeatCount = 1
            repeatMode = Animation.REVERSE
            interpolator = DecelerateInterpolator()
        }
        binding.ivHeart.startAnimation(pulse)
    }

    /**
     * 텍스트·아이콘 색상을 ArgbEvaluator로 부드럽게 전환합니다.
     * 졸음: 흰색→노란색 / 회복: 노란색→흰색
     */
    private fun animateColorTransition(toDrowsy: Boolean) {
        val white  = Color.WHITE
        val yellow = ContextCompat.getColor(this, R.color.drowsy_yellow)
        val red    = ContextCompat.getColor(this, R.color.heart_red)

        val fromText = if (toDrowsy) white  else yellow
        val toText   = if (toDrowsy) yellow else white
        val fromHeart = if (toDrowsy) red   else yellow
        val toHeart   = if (toDrowsy) yellow else red

        colorAnimator?.cancel()
        colorAnimator = ValueAnimator.ofObject(ArgbEvaluator(), fromText, toText).apply {
            duration = 400
            addUpdateListener { anim ->
                val color = anim.animatedValue as Int
                binding.tvBpm.setTextColor(color)
                binding.tvBpmUnit.setTextColor(color)
            }
            start()
        }

        // 심장 색상은 ColorFilter 애니메이션
        ValueAnimator.ofObject(ArgbEvaluator(), fromHeart, toHeart).apply {
            duration = 400
            addUpdateListener { anim ->
                binding.ivHeart.setColorFilter(anim.animatedValue as Int)
            }
            start()
        }
    }

    /**
     * 경보 발생 시 화면 전체에 노란색이 반짝였다 사라집니다.
     */
    private fun flashScreen() {
        binding.flashOverlay.apply {
            animate().cancel()
            alpha = 0.75f
            animate()
                .alpha(0f)
                .setDuration(700)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────────

    @Suppress("DEPRECATION")
    private fun resolveVibrator(): Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
}
