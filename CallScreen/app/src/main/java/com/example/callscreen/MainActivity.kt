package com.example.callscreen

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.TypedValue
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File

class MainActivity : Activity() {

    private val pickCode = 42
    private lateinit var statusNotificationView: TextView
    private lateinit var statusOverlayView: TextView
    private lateinit var statusBatteryView: TextView
    private lateinit var bgPreviewImage: ImageView
    private lateinit var bgStatusLabel: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun roundedCard(bgColor: Int, radiusDp: Int = 16) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(radiusDp).toFloat()
        setColor(bgColor)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        fun styledBtn(label: String, bgHex: String = "#1E293B", action: () -> Unit) =
            Button(this).apply {
                text = label
                isAllCaps = false
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                background = roundedCard(Color.parseColor(bgHex), 12)
                setPadding(dp(16), dp(14), dp(16), dp(14))
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                    topMargin = dp(10)
                }
                setOnClickListener { action() }
            }

        statusNotificationView = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, dp(4), 0, dp(2))
        }
        statusOverlayView = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, dp(4), 0, dp(2))
        }
        statusBatteryView = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, dp(4), 0, dp(8))
        }

        bgPreviewImage = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = LinearLayout.LayoutParams(-1, dp(160)).apply {
                topMargin = dp(12)
            }
            background = roundedCard(Color.parseColor("#1E293B"), 12)
        }

        bgStatusLabel = TextView(this).apply {
            setTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, dp(6), 0, 0)
        }

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(36), dp(24), dp(36))

            addView(TextView(context).apply {
                text = "CallScreen Կարգավորումներ"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                typeface = Typeface.DEFAULT_BOLD
            })

            addView(TextView(context).apply {
                text = "Միացրեք 1–3 թույլտվությունները, որպեսզի մեսենջերների զանգերը բացվեն լիաէկրան One UI ոճով."
                setTextColor(Color.parseColor("#CBD5E1"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setPadding(0, dp(8), 0, dp(14))
            })

            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                background = roundedCard(Color.parseColor("#111827"), 14)
                setPadding(dp(16), dp(12), dp(16), dp(12))
                addView(statusNotificationView)
                addView(statusOverlayView)
                addView(statusBatteryView)
            })

            addView(styledBtn("1. Ծանուցումների հասանելիություն (Notification access)") {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            })

            addView(styledBtn("2. Ցուցադրել այլ ծրագրերի վրա (Display over other apps)") {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            })

            addView(styledBtn("3. Մարտկոց՝ Անսահմանափակ (Battery: Unrestricted)") {
                startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:$packageName")
                    )
                )
            })

            addView(TextView(context).apply {
                text = "Զանգի էկրանի հետնանկար"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, dp(24), 0, 0)
            })

            addView(bgPreviewImage)
            addView(bgStatusLabel)

            addView(styledBtn("Ընտրել զանգի հետնանկար (Choose background)", "#2563EB") {
                @Suppress("DEPRECATION")
                startActivityForResult(
                    Intent(Intent.ACTION_GET_CONTENT).setType("image/*"),
                    pickCode
                )
            })

            addView(styledBtn("Վերականգնել հիմնական հետնանկարը (Reset background)") {
                File(filesDir, "bg.jpg").delete()
                refreshUiState()
                Toast.makeText(context, "Հետնանկարը վերականգնվեց", Toast.LENGTH_SHORT).show()
            })

            addView(styledBtn("📞 Փորձարկել զանգի էկրանը (Test Call Screen)", "#22A559") {
                startActivity(
                    Intent(this@MainActivity, CallActivity::class.java)
                        .putExtra("caller", "033 144868")
                        .putExtra("subtitle", "Armenia")
                        .putExtra("app", "WhatsApp")
                        .putExtra("pkg", "com.whatsapp")
                        .putExtra("isTest", true)
                )
            })
        }

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0B0F19"))
            addView(col)
        }
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        refreshUiState()
    }

    private fun refreshUiState() {
        val enabledListeners = Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        ) ?: ""
        val hasNotif = enabledListeners.contains(packageName)
        val hasOverlay = Settings.canDrawOverlays(this)
        val pm = getSystemService(PowerManager::class.java)
        val hasBattery = pm?.isIgnoringBatteryOptimizations(packageName) == true

        statusNotificationView.text = if (hasNotif) {
            "✅ 1. Ծանուցումների հասանելիություն՝ Միացված է"
        } else {
            "⚠️ 1. Ծանուցումների հասանելիություն՝ Անջատված է"
        }
        statusNotificationView.setTextColor(
            Color.parseColor(if (hasNotif) "#34D399" else "#FBBF24")
        )

        statusOverlayView.text = if (hasOverlay) {
            "✅ 2. Ցուցադրել այլ ծրագրերի վրա՝ Միացված է"
        } else {
            "⚠️ 2. Ցուցադրել այլ ծրագրերի վրա՝ Անջատված է"
        }
        statusOverlayView.setTextColor(
            Color.parseColor(if (hasOverlay) "#34D399" else "#FBBF24")
        )

        statusBatteryView.text = if (hasBattery) {
            "✅ 3. Անսահմանափակ մարտկոց՝ Միացված է"
        } else {
            "ℹ️ 3. Մարտկոց՝ Սահմանեք Unrestricted (App Info -> Battery)"
        }
        statusBatteryView.setTextColor(
            Color.parseColor(if (hasBattery) "#34D399" else "#93C5FD")
        )

        val bgFile = File(filesDir, "bg.jpg")
        if (bgFile.exists()) {
            bgPreviewImage.setImageBitmap(BitmapFactory.decodeFile(bgFile.path))
            bgStatusLabel.text = "Ընտրված է հատուկ հետնանկար (bg.jpg)"
        } else {
            bgPreviewImage.setImageDrawable(null)
            bgStatusLabel.text = "Օգտագործվում է ստանդարտ մուգ One UI հետնանկարը"
        }
    }

    @Deprecated("Fine for this simple app")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        if (requestCode != pickCode || resultCode != RESULT_OK) return

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outHeight / sample > 2400) sample *= 2
        val bmp = contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(
                it,
                null,
                BitmapFactory.Options().apply { inSampleSize = sample }
            )
        } ?: return
        File(filesDir, "bg.jpg").outputStream().use {
            bmp.compress(Bitmap.CompressFormat.JPEG, 90, it)
        }
        refreshUiState()
        Toast.makeText(this, "Հետնանկարը պահպանվեց", Toast.LENGTH_SHORT).show()
    }
}
