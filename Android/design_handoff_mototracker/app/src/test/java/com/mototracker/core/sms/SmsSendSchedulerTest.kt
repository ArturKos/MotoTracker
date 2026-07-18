package com.mototracker.core.sms

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsSendSchedulerTest {

    private val now = 1_000_000L
    private val interval = 15 // minutes

    // ── Positive: should send ─────────────────────────────────────────────────

    @Test
    fun `enabled fix present interval elapsed returns true`() {
        val lastSent = now - interval * 60_000L
        assertTrue(
            SmsSendScheduler.shouldSend(
                enabled = true,
                recipientCount = 1,
                hasFix = true,
                lastSentMs = lastSent,
                nowMs = now,
                intervalMinutes = interval,
            )
        )
    }

    @Test
    fun `first tick lastSentMs null with fix returns true`() {
        assertTrue(
            SmsSendScheduler.shouldSend(
                enabled = true,
                recipientCount = 2,
                hasFix = true,
                lastSentMs = null,
                nowMs = now,
                intervalMinutes = interval,
            )
        )
    }

    @Test
    fun `interval exactly elapsed boundary returns true`() {
        val lastSent = now - interval * 60_000L
        assertTrue(
            SmsSendScheduler.shouldSend(
                enabled = true,
                recipientCount = 1,
                hasFix = true,
                lastSentMs = lastSent,
                nowMs = now,
                intervalMinutes = interval,
            )
        )
    }

    // ── Negative: should not send ─────────────────────────────────────────────

    @Test
    fun `disabled returns false`() {
        assertFalse(
            SmsSendScheduler.shouldSend(
                enabled = false,
                recipientCount = 1,
                hasFix = true,
                lastSentMs = null,
                nowMs = now,
                intervalMinutes = interval,
            )
        )
    }

    @Test
    fun `no fix returns false`() {
        assertFalse(
            SmsSendScheduler.shouldSend(
                enabled = true,
                recipientCount = 1,
                hasFix = false,
                lastSentMs = null,
                nowMs = now,
                intervalMinutes = interval,
            )
        )
    }

    @Test
    fun `empty recipients returns false`() {
        assertFalse(
            SmsSendScheduler.shouldSend(
                enabled = true,
                recipientCount = 0,
                hasFix = true,
                lastSentMs = null,
                nowMs = now,
                intervalMinutes = interval,
            )
        )
    }

    @Test
    fun `interval not yet elapsed returns false`() {
        val lastSent = now - (interval * 60_000L - 1L) // 1 ms short
        assertFalse(
            SmsSendScheduler.shouldSend(
                enabled = true,
                recipientCount = 1,
                hasFix = true,
                lastSentMs = lastSent,
                nowMs = now,
                intervalMinutes = interval,
            )
        )
    }

    // ── Guard: interval clamped to minimum 1 minute ───────────────────────────

    @Test
    fun `zero intervalMinutes is clamped to 1 minute`() {
        val lastSent = now - 60_000L // exactly 1 min ago
        assertTrue(
            SmsSendScheduler.shouldSend(
                enabled = true,
                recipientCount = 1,
                hasFix = true,
                lastSentMs = lastSent,
                nowMs = now,
                intervalMinutes = 0,
            )
        )
    }

    @Test
    fun `negative intervalMinutes is clamped to 1 minute`() {
        val lastSent = now - 60_000L
        assertTrue(
            SmsSendScheduler.shouldSend(
                enabled = true,
                recipientCount = 1,
                hasFix = true,
                lastSentMs = lastSent,
                nowMs = now,
                intervalMinutes = -5,
            )
        )
    }

    // ── Combined: SmsSendScheduler + SmsLocationMessageBuilder ───────────────

    @Test
    fun `when shouldSend is true builder produces one message per recipient with maps URL`() {
        val recipients = listOf(
            SmsRecipient("Alice", "+48100000001"),
            SmsRecipient("Bob", "+48100000002"),
        )
        val shouldSend = SmsSendScheduler.shouldSend(
            enabled = true,
            recipientCount = recipients.size,
            hasFix = true,
            lastSentMs = null,
            nowMs = now,
            intervalMinutes = interval,
        )
        assertTrue(shouldSend)

        val messages = SmsLocationMessageBuilder.build(
            recipients = recipients,
            lat = 52.2297,
            lng = 21.0122,
            template = "MotoTracker: I'm here %s",
        )

        org.junit.Assert.assertEquals(2, messages.size)
        assertTrue(messages[0].text.contains("maps"))
        assertTrue(messages[1].text.contains("maps"))
        org.junit.Assert.assertEquals("+48100000001", messages[0].number)
        org.junit.Assert.assertEquals("+48100000002", messages[1].number)
    }
}
