package com.example.callscreen

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

object CallHolder {
    var answer: PendingIntent? = null
    var decline: PendingIntent? = null
    var fallbackOpen: PendingIntent? = null
    var key: String? = null
    var activity: CallActivity? = null
}

class CallListenerService : NotificationListenerService() {

    private val apps = mapOf(
        "com.whatsapp" to "WhatsApp",
        "com.whatsapp.w4b" to "WhatsApp Business",
        "com.viber.voip" to "Viber",
        "org.telegram.messenger" to "Telegram",
        "org.telegram.messenger.web" to "Telegram",
        "com.instagram.android" to "Instagram",
        "com.facebook.orca" to "Messenger"
    )

    private val answerRegex = Regex(
        "answer|accept|pick|join|ընդունել|պատասխանել|միանալ|ответ|принять",
        RegexOption.IGNORE_CASE
    )
    private val declineRegex = Regex(
        "decline|reject|dismiss|hang|ignore|end|մերժել|անջատել|отклон|сброс|заверш",
        RegexOption.IGNORE_CASE
    )

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val appName = apps[sbn.packageName] ?: return
        val n = sbn.notification
        if (n.category != Notification.CATEGORY_CALL) return

        var answer: PendingIntent? = null
        var decline: PendingIntent? = null
        n.actions?.forEach { a ->
            val t = a.title?.toString() ?: return@forEach
            if (answer == null && answerRegex.containsMatchIn(t)) {
                answer = a.actionIntent
            } else if (decline == null && declineRegex.containsMatchIn(t)) {
                decline = a.actionIntent
            }
        }

        // Only incoming (ringing) calls: they have a full-screen intent or an Answer button
        if (n.fullScreenIntent == null && answer == null) return

        val caller = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?.takeIf { it.isNotBlank() } ?: "Unknown"
        val subtitle = n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?.takeIf { it.isNotBlank() } ?: "Incoming call"

        val fallbackOpen = n.fullScreenIntent ?: n.contentIntent

        CallHolder.answer = answer ?: fallbackOpen
        CallHolder.decline = decline
        CallHolder.fallbackOpen = fallbackOpen
        CallHolder.key = sbn.key

        startActivity(
            Intent(this, CallActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra("caller", caller)
                .putExtra("subtitle", subtitle)
                .putExtra("app", appName)
                .putExtra("pkg", sbn.packageName)
                .putExtra("isTest", false)
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.key == CallHolder.key) {
            CallHolder.activity?.finish()
            CallHolder.key = null
        }
    }
}
