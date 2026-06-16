package com.voicememos.aicalendar.ai

import java.time.Instant
import java.time.ZoneId

object DeepSeekPrompt {
    fun systemPrompt(): String =
        """
        You are a calendar parsing engine. Return only valid json.
        Parse Chinese or English natural language into one or more calendar events.
        A single transcript may contain multiple tasks. Split every distinct task, meeting, reminder, or errand into its own event when it has its own time or intent.
        Do not invent dates or times. If a required field is unclear, leave it null and add the field name to missing_fields.
        Required json shape:
        {
          "events": [
            {
              "title": "short event title",
              "notes": "optional notes or null",
              "start_local": "YYYY-MM-DDTHH:mm:ss or YYYY-MM-DD for all-day or null",
              "end_local": "YYYY-MM-DDTHH:mm:ss or YYYY-MM-DD for all-day or null",
              "all_day": false,
              "reminder_minutes": [10],
              "confidence": 0.0,
              "missing_fields": []
            }
          ],
          "warnings": []
        }
        """.trimIndent()

    fun userPrompt(
        transcript: String,
        now: Instant,
        zoneId: ZoneId,
        defaultDurationMinutes: Int,
        defaultReminderMinutes: Int,
    ): String =
        """
        Current time: ${now.atZone(zoneId)}
        Time zone: ${zoneId.id}
        Locale: zh-CN
        Default duration minutes: $defaultDurationMinutes
        Default reminder minutes: $defaultReminderMinutes
        User transcript: $transcript

        Return json only.
        """.trimIndent()
}
