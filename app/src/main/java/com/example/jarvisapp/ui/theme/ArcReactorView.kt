package com.example.jarvisapp.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.animation.doOnEnd
import kotlin.math.cos
import kotlin.math.sin

class ArcReactorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class State {
        IDLE, LISTENING, THINKING, SPEAKING
    }

    private var currentState = State.IDLE

    // Paints
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Animation values
    private var pulseScale = 1f
    private var rotationAngle = 0f
    private var glowAlpha = 180
    private var ringProgress = 0f

    private var pulseAnimator: ValueAnimator? = null
    private var rotateAnimator: ValueAnimator? = null
    private var glowAnimator: ValueAnimator? = null

    // Colors
    private val colorIdle = Color.parseColor("#00D4FF")
    private val colorListening = Color.parseColor("#FF3D00")
    private val colorThinking = Color.parseColor("#7C4DFF")
    private val colorSpeaking = Color.parseColor("#00E676")

    private var currentColor = colorIdle

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null) // necesario para blur
        setupPaints()
        startIdleAnimation()
    }

    private fun setupPaints() {
        corePaint.style = Paint.Style.FILL
        corePaint.color = currentColor

        glowPaint.style = Paint.Style.FILL
        glowPaint.maskFilter = BlurMaskFilter(30f, BlurMaskFilter.Blur.NORMAL)

        ringPaint.style = Paint.Style.STROKE
        ringPaint.strokeWidth = 4f
        ringPaint.color = currentColor
        ringPaint.alpha = 180

        outerRingPaint.style = Paint.Style.STROKE
        outerRingPaint.strokeWidth = 2.5f
        outerRingPaint.color = currentColor
        outerRingPaint.alpha = 120

        particlePaint.style = Paint.Style.FILL
        particlePaint.color = currentColor
    }

    fun setState(newState: State) {
        if (currentState == newState) return
        currentState = newState

        when (newState) {
            State.IDLE -> {
                currentColor = colorIdle
                startIdleAnimation()
            }
            State.LISTENING -> {
                currentColor = colorListening
                startListeningAnimation()
            }
            State.THINKING -> {
                currentColor = colorThinking
                startThinkingAnimation()
            }
            State.SPEAKING -> {
                currentColor = colorSpeaking
                startSpeakingAnimation()
            }
        }
        updateColors()
        invalidate()
    }

    private fun updateColors() {
        corePaint.color = currentColor
        glowPaint.color = currentColor
        ringPaint.color = currentColor
        outerRingPaint.color = currentColor
        particlePaint.color = currentColor
    }

    private fun startIdleAnimation() {
        stopAllAnimations()

        pulseAnimator = ValueAnimator.ofFloat(0.92f, 1.08f).apply {
            duration = 1800
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                pulseScale = it.animatedValue as Float
                glowAlpha = (140 + 60 * ((pulseScale - 0.92f) / 0.16f)).toInt()
                invalidate()
            }
            start()
        }

        rotateAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 12000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                rotationAngle = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun startListeningAnimation() {
        stopAllAnimations()

        pulseAnimator = ValueAnimator.ofFloat(0.95f, 1.15f).apply {
            duration = 600
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                pulseScale = it.animatedValue as Float
                invalidate()
            }
            start()
        }

        rotateAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 3000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                rotationAngle = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun startThinkingAnimation() {
        stopAllAnimations()

        pulseAnimator = ValueAnimator.ofFloat(0.9f, 1.1f).apply {
            duration = 900
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                pulseScale = it.animatedValue as Float
                invalidate()
            }
            start()
        }

        rotateAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 1800
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                rotationAngle = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun startSpeakingAnimation() {
        stopAllAnimations()

        pulseAnimator = ValueAnimator.ofFloat(0.97f, 1.12f).apply {
            duration = 400
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                pulseScale = it.animatedValue as Float
                invalidate()
            }
            start()
        }

        rotateAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 5000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                rotationAngle = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun stopAllAnimations() {
        pulseAnimator?.cancel()
        rotateAnimator?.cancel()
        glowAnimator?.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val radius = (width.coerceAtMost(height) / 2f) * 0.42f * pulseScale

        // Glow exterior
        glowPaint.alpha = glowAlpha
        canvas.drawCircle(cx, cy, radius * 1.8f, glowPaint)

        // Anillo exterior giratorio
        canvas.save()
        canvas.rotate(rotationAngle, cx, cy)
        outerRingPaint.alpha = 100
        canvas.drawCircle(cx, cy, radius * 1.55f, outerRingPaint)

        // Pequeños puntos en el anillo exterior
        for (i in 0 until 8) {
            val angle = Math.toRadians((i * 45).toDouble())
            val px = cx + (radius * 1.55f) * cos(angle).toFloat()
            val py = cy + (radius * 1.55f) * sin(angle).toFloat()
            canvas.drawCircle(px, py, 3.5f, particlePaint)
        }
        canvas.restore()

        // Anillo medio
        canvas.save()
        canvas.rotate(-rotationAngle * 0.7f, cx, cy)
        ringPaint.alpha = 160
        canvas.drawCircle(cx, cy, radius * 1.25f, ringPaint)
        canvas.restore()

        // Núcleo
        corePaint.alpha = 255
        canvas.drawCircle(cx, cy, radius * 0.55f, corePaint)

        // Núcleo interno más brillante
        val innerCore = Paint(corePaint)
        innerCore.color = Color.WHITE
        innerCore.alpha = 180
        canvas.drawCircle(cx, cy, radius * 0.22f, innerCore)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAllAnimations()
    }
}