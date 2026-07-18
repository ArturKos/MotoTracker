package com.mototracker.data.sms

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import com.mototracker.core.sms.SmsMessage
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Abstraction over Android's [SmsManager] for sending location SMS messages.
 *
 * Extracted behind an interface so [com.mototracker.service.RecordingService] can be tested
 * with a fake without a real telephony subsystem.
 */
interface SmsSender {
    /**
     * Sends [message] as an SMS to [SmsMessage.number].
     *
     * Implementations must never throw — send failures must be caught internally so
     * a lost-coverage event does not crash the active ride.
     */
    fun send(message: SmsMessage)
}

/**
 * Production implementation that delegates to [SmsManager].
 *
 * Long message bodies are automatically split via [SmsManager.divideMessage] and sent as a
 * multipart message so they arrive as a single thread on the recipient's device.
 *
 * All send calls are wrapped in `try/catch` — a `SecurityException` (SEND_SMS not granted) or
 * any telephony error is swallowed so the recording session is never interrupted.
 *
 * @param ctx Application context used to obtain [SmsManager] on API 31+.
 */
@Singleton
class AndroidSmsSender @Inject constructor(
    @ApplicationContext private val ctx: Context,
) : SmsSender {

    @SuppressLint("MissingPermission")
    override fun send(message: SmsMessage) {
        try {
            val manager = smsManager()
            val parts = manager.divideMessage(message.text)
            if (parts.size == 1) {
                manager.sendTextMessage(message.number, null, message.text, null, null)
            } else {
                manager.sendMultipartTextMessage(message.number, null, parts, null, null)
            }
        } catch (_: Exception) {
            // Security, Illegal-argument, or telephony failures must not crash the ride.
        }
    }

    @Suppress("DEPRECATION")
    private fun smsManager(): SmsManager =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ctx.getSystemService(SmsManager::class.java)
        } else {
            SmsManager.getDefault()
        }
}
