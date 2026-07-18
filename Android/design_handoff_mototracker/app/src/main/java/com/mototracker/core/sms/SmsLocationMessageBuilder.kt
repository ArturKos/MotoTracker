package com.mototracker.core.sms

import com.mototracker.core.format.CoordinateClipboard

/**
 * A person who should receive periodic SMS location updates (Y1).
 *
 * @param name   Display name from the phone's contacts.
 * @param number Phone number in any format accepted by SmsManager.
 */
data class SmsRecipient(val name: String, val number: String)

/**
 * A ready-to-send SMS payload produced by [SmsLocationMessageBuilder].
 *
 * @param number Phone number to send to.
 * @param text   Message body including the Google Maps URL.
 */
data class SmsMessage(val number: String, val text: String)

/**
 * Pure builder that converts a list of recipients and a GPS fix into ready-to-send SMS messages.
 *
 * No Android framework imports — entirely JVM-testable.
 */
object SmsLocationMessageBuilder {

    /**
     * Builds one [SmsMessage] per recipient.
     *
     * The Google Maps URL is obtained via [CoordinateClipboard.mapsUrl].  If [template] contains
     * the literal `%s` it is replaced by the URL; otherwise the URL is appended after a space.
     * Returns an empty list when [recipients] is empty.
     *
     * @param recipients Target recipients.
     * @param lat        WGS-84 latitude in decimal degrees.
     * @param lng        WGS-84 longitude in decimal degrees.
     * @param template   Message template; use `%s` as the URL placeholder.
     */
    fun build(
        recipients: List<SmsRecipient>,
        lat: Double,
        lng: Double,
        template: String,
    ): List<SmsMessage> {
        if (recipients.isEmpty()) return emptyList()
        val url = CoordinateClipboard.mapsUrl(lat, lng)
        val text = if (template.contains("%s")) template.replace("%s", url) else "$template $url"
        return recipients.map { SmsMessage(number = it.number, text = text) }
    }
}
