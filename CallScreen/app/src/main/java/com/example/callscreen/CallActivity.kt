package com.example.callscreen

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.io.File
import kotlin.math.hypot

class CallActivity : Activity() {

    private val pulseAnimators = mutableListOf<ObjectAnimator>()
    private lateinit var answerSlider: View
    private lateinit var declineSlider: View

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun circle(color: Int, strokeColor: Int = Color.TRANSPARENT, strokeWidthDp: Int = 0) =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            if (strokeWidthDp > 0) {
                setStroke(dp(strokeWidthDp), strokeColor)
            }
        }

    private fun text(t: String, sp: Float, bold: Boolean = false, color: Int = Color.WHITE) =
        TextView(this).apply {
            text = t
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            setTextColor(color)
            gravity = Gravity.CENTER
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CallHolder.activity = this

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        val caller = intent.getStringExtra("caller") ?: ""
        val subtitle = intent.getStringExtra("subtitle") ?: "Incoming call"
        val app = intent.getStringExtra("app") ?: "Incoming call"
        val pkg = intent.getStringExtra("pkg") ?: ""
        val isTest = intent.getBooleanExtra("isTest", false)

        val root = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
            setBackgroundColor(Color.parseColor("#0E1116"))
        }

        // Chosen background (set in MainActivity)
        val bgFile = File(filesDir, "bg.jpg")
        if (bgFile.exists()) {
            root.addView(
                ImageView(this).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setImageBitmap(BitmapFactory.decodeFile(bgFile.path))
                },
                FrameLayout.LayoutParams(-1, -1)
            )
            root.addView(
                View(this).apply { setBackgroundColor(Color.parseColor("#33000000")) },
                FrameLayout.LayoutParams(-1, -1)
            )
        }

        // Top: [app icon] Incoming call / App name, then caller number/name, then subtitle
        val icon = try {
            if (pkg.isNotEmpty()) packageManager.getApplicationIcon(pkg) else null
        } catch (_: Exception) {
            null
        }
        val appRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            if (icon != null) {
                addView(
                    ImageView(context).apply { setImageDrawable(icon) },
                    LinearLayout.LayoutParams(dp(22), dp(22)).apply { rightMargin = dp(8) }
                )
            }
            addView(text("$app • Incoming call", 15f, false, Color.parseColor("#E6FFFFFF")))
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(60), dp(24), 0)
            addView(appRow)
            addView(text(caller, 38f, true).apply { setPadding(0, dp(18), 0, 0) })
            addView(text(subtitle, 16f, false, Color.parseColor("#CCFFFFFF")).apply {
                setPadding(0, dp(6), 0, 0)
            })
        }
        root.addView(top, FrameLayout.LayoutParams(-1, -2))

        // Exact Samsung One UI Sliders:
        // 1. Passive white halo appears from button edge, expands outward, and disperses (RESTART).
        // 2. Green and Red buttons stay FIXED in place.
        // 3. On touch down, opposite button fades to alpha = 0.32f, outer 250dp target circle appears,
        //    and inner darker circle expands with finger distance.
        // 4. As soon as the finger passes the outer target circle (even without releasing),
        //    Answer / Decline triggers immediately!
        answerSlider = oneUiStationarySlider(
            buttonColor = Color.parseColor("#22A559"),
            iconRotation = 0f,
            otherButtonProvider = { declineSlider },
            onTrigger = { answerCall(isTest) }
        )
        declineSlider = oneUiStationarySlider(
            buttonColor = Color.parseColor("#DE3B33"),
            iconRotation = 135f,
            otherButtonProvider = { answerSlider },
            onTrigger = { declineCall(isTest) }
        )

        val boxSize = dp(250)
        root.addView(
            answerSlider,
            FrameLayout.LayoutParams(boxSize, boxSize, Gravity.BOTTOM or Gravity.START).apply {
                leftMargin = -dp(32)
                bottomMargin = dp(8)
            }
        )
        root.addView(
            declineSlider,
            FrameLayout.LayoutParams(boxSize, boxSize, Gravity.BOTTOM or Gravity.END).apply {
                rightMargin = -dp(32)
                bottomMargin = dp(8)
            }
        )

        setContentView(root)
    }

    private fun oneUiStationarySlider(
        buttonColor: Int,
        iconRotation: Float,
        otherButtonProvider: () -> View,
        onTrigger: () -> Unit
    ): FrameLayout {
        val targetDiameter = dp(250)
        val idleDiameter = dp(112)
        val buttonDiameter = dp(72)
        val minInnerScale = buttonDiameter.toFloat() / targetDiameter.toFloat()

        val box = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
        }

        // 1. Passive white ring: emerges from button edge (0.64f), expands outward (1.22f), and disperses (alpha -> 0f)
        val idleHalo = View(this).apply {
            background = circle(Color.parseColor("#55FFFFFF"))
        }
        box.addView(idleHalo, FrameLayout.LayoutParams(idleDiameter, idleDiameter, Gravity.CENTER))

        val pulse = ObjectAnimator.ofPropertyValuesHolder(
            idleHalo,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 0.64f, 1.22f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.64f, 1.22f),
            PropertyValuesHolder.ofFloat(View.ALPHA, 0.78f, 0f)
        ).apply {
            duration = 1550
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            start()
        }
        pulseAnimators.add(pulse)

        // 2. Large outer target boundary circle (250dp, hidden until touched)
        val outerTargetCircle = View(this).apply {
            background = circle(Color.parseColor("#36FFFFFF"), Color.parseColor("#80FFFFFF"), 1)
            alpha = 0f
            scaleX = 0.45f
            scaleY = 0.45f
        }
        box.addView(outerTargetCircle, FrameLayout.LayoutParams(targetDiameter, targetDiameter, Gravity.CENTER))

        // 3. Concentric inner darker ring that expands with finger drag distance
        val innerExpandingRing = View(this).apply {
            background = circle(Color.parseColor("#4D1A1A1A"))
            alpha = 0f
            scaleX = minInnerScale
            scaleY = minInnerScale
        }
        box.addView(innerExpandingRing, FrameLayout.LayoutParams(targetDiameter, targetDiameter, Gravity.CENTER))

        // 4. Stationary colored button (72dp) with crisp white phone icon
        val handle = FrameLayout(this).apply {
            background = circle(buttonColor)
            elevation = dp(4).toFloat()
            val phoneIcon = ImageView(context).apply {
                setImageResource(android.R.drawable.sym_action_call)
                setColorFilter(Color.WHITE)
                rotation = iconRotation
            }
            addView(phoneIcon, FrameLayout.LayoutParams(dp(34), dp(34), Gravity.CENTER))
        }
        box.addView(handle, FrameLayout.LayoutParams(buttonDiameter, buttonDiameter, Gravity.CENTER))

        val triggerRadius = dp(112).toFloat()
        var centerX = 0f
        var centerY = 0f
        var isDragging = false

        handle.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val loc = IntArray(2)
                    v.getLocationOnScreen(loc)
                    centerX = loc[0] + v.width / 2f
                    centerY = loc[1] + v.height / 2f
                    isDragging = true

                    pulse.pause()
                    idleHalo.animate().alpha(0f).setDuration(80).start()

                    // Fade out the opposite button
                    otherButtonProvider().animate().alpha(0.32f).setDuration(150).start()

                    // Expand the large outer target circle
                    outerTargetCircle.animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(180)
                        .start()

                    // Show the inner expanding ring starting around the button
                    innerExpandingRing.animate().cancel()
                    innerExpandingRing.alpha = 1f
                    innerExpandingRing.scaleX = minInnerScale
                    innerExpandingRing.scaleY = minInnerScale

                    vibrateShort(15)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!isDragging) return@setOnTouchListener true
                    val d = hypot(e.rawX - centerX, e.rawY - centerY)
                    val progress = (d / triggerRadius).coerceIn(0f, 1f)
                    val curScale = minInnerScale + progress * (1f - minInnerScale)
                    innerExpandingRing.scaleX = curScale
                    innerExpandingRing.scaleY = curScale

                    // Auto-trigger immediately when finger passes the circle boundary without needing to release
                    if (d >= triggerRadius) {
                        isDragging = false
                        vibrateShort(35)
                        onTrigger()
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!isDragging) return@setOnTouchListener true
                    isDragging = false

                    // Smoothly collapse rings and restore opposite button if released before passing the circle
                    outerTargetCircle.animate()
                        .alpha(0f)
                        .scaleX(0.45f)
                        .scaleY(0.45f)
                        .setDuration(180)
                        .start()
                    innerExpandingRing.animate()
                        .alpha(0f)
                        .scaleX(minInnerScale)
                        .scaleY(minInnerScale)
                        .setDuration(180)
                        .start()
                    otherButtonProvider().animate().alpha(1f).setDuration(180).start()
                    idleHalo.animate().alpha(1f).setDuration(180).withEndAction {
                        pulse.resume()
                    }.start()
                    true
                }
                else -> false
            }
        }
        return box
    }

    private fun vibrateShort(ms: Long) {
        try {
            val vib = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(ms)
            }
        } catch (_: Exception) {
        }
    }

    private fun answerCall(isTest: Boolean) {
        try {
            getSystemService(KeyguardManager::class.java)?.requestDismissKeyguard(this, null)
        } catch (_: Exception) {
        }
        if (isTest) {
            Toast.makeText(this, "Փորձնական զանգն ընդունվեց", Toast.LENGTH_SHORT).show()
        } else {
            try {
                (CallHolder.answer ?: CallHolder.fallbackOpen)?.send()
            } catch (_: Exception) {
                try {
                    CallHolder.fallbackOpen?.send()
                } catch (_: Exception) {
                }
            }
        }
        finish()
    }

    private fun declineCall(isTest: Boolean) {
        if (isTest) {
            Toast.makeText(this, "Փորձնական զանգը մերժվեց", Toast.LENGTH_SHORT).show()
        } else {
            try {
                CallHolder.decline?.send()
            } catch (_: Exception) {
            }
        }
        finish()
    }

    override fun onDestroy() {
        pulseAnimators.forEach { it.cancel() }
        pulseAnimators.clear()
        if (CallHolder.activity === this) CallHolder.activity = null
        super.onDestroy()
    }
}
