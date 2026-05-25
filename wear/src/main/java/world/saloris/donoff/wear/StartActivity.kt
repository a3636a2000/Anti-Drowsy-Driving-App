package world.saloris.donoff.wear

import android.animation.*
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.*
import androidx.appcompat.app.AppCompatActivity
import world.saloris.donoff.wear.databinding.ActivityStartBinding

/**
 * 시작 화면 애니메이션:
 *  1. 진입: 타이틀 페이드+슬라이드업 → 심장 오버슛 스케일 인 → 힌트 페이드 인
 *  2. 심장 뒤 파문(ripple) 3개: 확장·투명해지다가 반복 (각 600ms 간격)
 *  3. 심장: 연속 박동 scale 애니메이션
 *  4. 클릭: 심장이 살짝 작아졌다 돌아온 뒤 LoadingActivity 이동
 */
class StartActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStartBinding
    private val rippleAnimators = mutableListOf<AnimatorSet>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStartBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initViewsInvisible()
        playEntryAnimation()

        binding.ivHeart.setOnClickListener { onHeartClick() }
    }

    // ── 초기 상태 설정 ────────────────────────────────────────────────────────

    private fun initViewsInvisible() {
        binding.tvTitle.apply { alpha = 0f; translationY = 18f }
        binding.ivHeart.apply { alpha = 0f; scaleX = 0.4f; scaleY = 0.4f }
        binding.tvHint.apply { alpha = 0f }
    }

    // ── 진입 애니메이션 ───────────────────────────────────────────────────────

    private fun playEntryAnimation() {
        // 타이틀: 아래→위 슬라이드 + 페이드
        binding.tvTitle.animate()
            .alpha(1f).translationY(0f)
            .setDuration(450)
            .setStartDelay(80)
            .setInterpolator(DecelerateInterpolator())
            .start()

        // 심장: 오버슛 스케일 + 페이드
        binding.ivHeart.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(520)
            .setStartDelay(220)
            .setInterpolator(OvershootInterpolator(1.4f))
            .withEndAction {
                startHeartPulse()
                startRippleEffects()
            }
            .start()

        // 힌트: 페이드
        binding.tvHint.animate()
            .alpha(1f)
            .setDuration(400)
            .setStartDelay(560)
            .withEndAction { startHintBlink() }
            .start()
    }

    // ── 심장 박동 ─────────────────────────────────────────────────────────────

    private fun startHeartPulse() {
        val pulse = ScaleAnimation(
            1f, 1.13f, 1f, 1.13f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = 480
            repeatCount = Animation.INFINITE
            repeatMode = Animation.REVERSE
            interpolator = DecelerateInterpolator()
        }
        binding.ivHeart.startAnimation(pulse)
    }

    // ── 파문 이펙트 ───────────────────────────────────────────────────────────

    private fun startRippleEffects() {
        val ripples = listOf(binding.ripple1, binding.ripple2, binding.ripple3)
        ripples.forEachIndexed { i, view ->
            launchRipple(view, i * 600L)
        }
    }

    /**
     * 원형 View를 스케일 0.3→2.0, 알파 0.55→0으로 애니메이션하고
     * 끝나면 자기 자신을 다시 시작합니다 (무한 반복).
     */
    private fun launchRipple(view: View, initialDelay: Long) {
        val scaleX = ObjectAnimator.ofFloat(view, "scaleX", 0.3f, 2.0f)
        val scaleY = ObjectAnimator.ofFloat(view, "scaleY", 0.3f, 2.0f)
        val alpha  = ObjectAnimator.ofFloat(view, "alpha",  0.55f, 0f)
        val set = AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            duration = 1800
            startDelay = initialDelay
            interpolator = DecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    // 딜레이 없이 즉시 재시작
                    startDelay = 0
                    start()
                }
            })
        }
        rippleAnimators.add(set)
        set.start()
    }

    // ── 힌트 텍스트 깜빡임 ───────────────────────────────────────────────────

    private fun startHintBlink() {
        val blink = AlphaAnimation(1f, 0.25f).apply {
            duration = 900
            repeatCount = Animation.INFINITE
            repeatMode = Animation.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }
        binding.tvHint.startAnimation(blink)
    }

    // ── 클릭 처리 ─────────────────────────────────────────────────────────────

    private fun onHeartClick() {
        binding.ivHeart.clearAnimation()
        // 살짝 작아졌다가 원래 크기로 → 화면 전환
        binding.ivHeart.animate()
            .scaleX(0.82f).scaleY(0.82f)
            .setDuration(90)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                binding.ivHeart.animate()
                    .scaleX(1f).scaleY(1f)
                    .setDuration(140)
                    .setInterpolator(DecelerateInterpolator())
                    .withEndAction {
                        startActivity(Intent(this, LoadingActivity::class.java))
                        overridePendingTransition(
                            android.R.anim.fade_in,
                            android.R.anim.fade_out
                        )
                    }
                    .start()
            }
            .start()
    }

    // ── 생명주기 ──────────────────────────────────────────────────────────────

    override fun onDestroy() {
        rippleAnimators.forEach { it.cancel() }
        rippleAnimators.clear()
        super.onDestroy()
    }
}
