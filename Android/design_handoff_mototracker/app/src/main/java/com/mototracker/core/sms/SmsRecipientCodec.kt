package com.mototracker.core.sms

/**
 * Pure codec for serialising and deserialising a [List] of [SmsRecipient]s to/from a single String.
 *
 * **Format:** one recipient per line (`\n`); within each row, name and number are separated by an
 * unescaped `|`.  Characters that collide with the separators are escaped before writing:
 * - `\` → `\\`
 * - `|` → `\|`
 * - newline → `\n` (backslash-n literal)
 *
 * Malformed rows (no unescaped `|`, or too many fields) are silently skipped so a corrupt
 * DataStore entry degrades to an empty list rather than crashing.
 * No Android framework imports — entirely JVM-testable.
 */
object SmsRecipientCodec {

    private const val FIELD_SEP = '|'
    private const val ROW_SEP = '\n'

    /**
     * Encodes [recipients] to a multi-line string (one recipient per line).
     *
     * @param recipients List to serialise; may be empty.
     * @return Encoded string; empty string for an empty list.
     */
    fun encode(recipients: List<SmsRecipient>): String =
        recipients.joinToString(ROW_SEP.toString()) { r ->
            escapeField(r.name) + FIELD_SEP + escapeField(r.number)
        }

    /**
     * Decodes a string produced by [encode] back to a list of [SmsRecipient]s.
     *
     * Malformed or blank input yields an empty list.
     *
     * @param encoded Previously encoded string; may be blank or malformed.
     * @return Decoded list; never null.
     */
    fun decode(encoded: String): List<SmsRecipient> {
        if (encoded.isBlank()) return emptyList()
        return encoded.split(ROW_SEP).mapNotNull { row ->
            val parts = splitUnescaped(row)
            if (parts.size == 2) {
                SmsRecipient(name = unescapeField(parts[0]), number = unescapeField(parts[1]))
            } else {
                null
            }
        }
    }

    private fun escapeField(s: String): String = buildString {
        for (c in s) when (c) {
            '\\' -> append("\\\\")
            '|'  -> append("\\|")
            '\n' -> append("\\n")
            else -> append(c)
        }
    }

    private fun unescapeField(s: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            if (s[i] == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    '\\' -> { sb.append('\\'); i += 2 }
                    '|'  -> { sb.append('|');  i += 2 }
                    'n'  -> { sb.append('\n'); i += 2 }
                    else -> { sb.append(s[i]); i++ }
                }
            } else {
                sb.append(s[i])
                i++
            }
        }
        return sb.toString()
    }

    /** Splits [row] on the first unescaped [FIELD_SEP]; returns the two halves or the whole row as one element. */
    private fun splitUnescaped(row: String): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var i = 0
        while (i < row.length) {
            when {
                row[i] == '\\' && i + 1 < row.length -> {
                    current.append(row[i])
                    current.append(row[i + 1])
                    i += 2
                }
                row[i] == FIELD_SEP -> {
                    parts.add(current.toString())
                    current.clear()
                    i++
                }
                else -> {
                    current.append(row[i])
                    i++
                }
            }
        }
        parts.add(current.toString())
        return parts
    }
}
