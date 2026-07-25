package com.voicememos.aicalendar.ai

import com.voicememos.aicalendar.domain.AiParseResult
import com.voicememos.aicalendar.domain.DraftCalendarEvent
import com.voicememos.aicalendar.domain.RepeatFrequency
import com.voicememos.aicalendar.domain.RepeatRule
import com.voicememos.aicalendar.domain.ScheduleItemPriority
import com.voicememos.aicalendar.domain.ScheduleItemType
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.util.Locale

object AiResponseMapper {
    fun map(
        response: AiCalendarResponse,
        transcript: String,
        zoneId: ZoneId,
        defaultDurationMinutes: Int,
        defaultReminderMinutes: Int,
    ): AiParseResult {
        val warnings = response.warnings
            .map(String::trim)
            .filter(String::isNotEmpty)
            .map { it.take(MAX_WARNING_LENGTH) }
            .toMutableList()
        if (response.items.isNotEmpty() && response.events.isNotEmpty()) {
            warnings += "AI 同时返回了 items 和旧版 events；已优先使用 items。"
        }

        val questions = response.clarificationQuestions
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
        val globalClarification = response.requiresClarification || questions.isNotEmpty()
        val drafts = response.effectiveItems.mapIndexedNotNull { index, item ->
            try {
                mapItem(
                    item = item,
                    itemNumber = index + 1,
                    legacyEvent = response.usesLegacyEvents,
                    transcript = transcript,
                    fallbackZoneId = zoneId,
                    defaultDurationMinutes = defaultDurationMinutes,
                    defaultReminderMinutes = defaultReminderMinutes,
                    globalClarification = globalClarification,
                    warnings = warnings,
                )
            } catch (error: ItemValidationException) {
                warnings += "第 ${index + 1} 项无效：${error.message}"
                null
            } catch (error: DateTimeException) {
                warnings += "第 ${index + 1} 项时间无效：${error.message ?: "无法解析"}"
                null
            } catch (error: IllegalArgumentException) {
                warnings += "第 ${index + 1} 项无效：${error.message ?: "字段格式错误"}"
                null
            }
        }
        val requiresClarification =
            globalClarification || drafts.any(DraftCalendarEvent::needsClarification)

        return AiParseResult(
            events = drafts,
            warnings = warnings.distinct(),
            // Voice-created items always remain user-confirmed.
            needsConfirmation = true,
            requiresClarification = requiresClarification,
            clarificationQuestions = questions,
        )
    }

    private fun mapItem(
        item: AiCalendarEvent,
        itemNumber: Int,
        legacyEvent: Boolean,
        transcript: String,
        fallbackZoneId: ZoneId,
        defaultDurationMinutes: Int,
        defaultReminderMinutes: Int,
        globalClarification: Boolean,
        warnings: MutableList<String>,
    ): DraftCalendarEvent? {
        val title = item.title.trim()
        if (title.isEmpty()) {
            warnings += "第 $itemNumber 项缺少标题，已忽略。"
            return null
        }

        val itemType = parseItemType(item.type, legacyEvent)
        val itemZoneId = parseZoneId(item.timeZone, fallbackZoneId)
        val startValue = uniqueValue(
            fieldName = "start",
            item.start,
            item.startDateTime,
            item.startLocal,
        )
        val endValue = uniqueValue(
            fieldName = "end",
            item.end,
            item.endDateTime,
            item.endLocal,
        )
        val triggerValue = uniqueValue(
            fieldName = "trigger",
            item.trigger,
            item.triggerDateTime,
            item.triggerLocal,
        )
        val dueValue = uniqueValue(
            fieldName = "due",
            item.due,
            item.dueDateTime,
            item.dueLocal,
        )
        val missing = normalizeFields(
            values = item.missingFields,
            fieldListName = "missing_fields",
            itemNumber = itemNumber,
            warnings = warnings,
        ).toMutableSet()
        val ambiguous = normalizeFields(
            values = item.ambiguousFields,
            fieldListName = "ambiguous_fields",
            itemNumber = itemNumber,
            warnings = warnings,
        ).toMutableSet()

        var startAt: Instant? = null
        var endAt: Instant? = null
        var triggerAt: Instant? = null
        var dueAt: Instant? = null
        var resolvedAllDay = item.allDay

        when (itemType) {
            ScheduleItemType.EVENT -> {
                if (triggerValue != null || dueValue != null) {
                    warnings += "第 $itemNumber 项是 EVENT，已忽略 trigger/due 字段。"
                }
                val parsedStart = startValue?.let {
                    parseTemporal(
                        value = it,
                        fieldName = "start",
                        zoneId = itemZoneId,
                        allowDateOnly = item.allDay,
                    )
                }
                val parsedEnd = endValue?.let {
                    parseTemporal(
                        value = it,
                        fieldName = "end",
                        zoneId = itemZoneId,
                        allowDateOnly = item.allDay,
                    )
                }
                startAt = parsedStart?.instant
                endAt = parsedEnd?.instant
                    ?: startAt
                        ?.takeIf { !item.allDay }
                        ?.plusSeconds(defaultDurationMinutes.coerceAtLeast(1) * 60L)
                if (startAt == null) {
                    missing += EVENT_START_FIELD
                } else if (EVENT_START_FIELD !in ambiguous) {
                    missing -= EVENT_START_FIELD
                }
                if (startAt != null && endAt != null && !endAt.isAfter(startAt)) {
                    throw ItemValidationException("end 必须晚于 start")
                }
            }

            ScheduleItemType.REMINDER -> {
                if (dueValue != null || endValue != null) {
                    warnings += "第 $itemNumber 项是 REMINDER，已忽略 due/end 字段。"
                }
                val effectiveTrigger = mergeCompatibleTimeAliases(
                    canonicalName = "trigger",
                    primary = triggerValue,
                    compatibilityValue = startValue,
                )
                val parsedTrigger = effectiveTrigger?.let {
                    parseTemporal(
                        value = it,
                        fieldName = "trigger",
                        zoneId = itemZoneId,
                        allowDateOnly = false,
                    )
                }
                triggerAt = parsedTrigger?.instant
                // Keep the legacy event pipeline safe until typed persistence lands.
                startAt = triggerAt
                if (triggerAt == null) {
                    missing += "trigger"
                } else if ("trigger" !in ambiguous) {
                    missing -= "trigger"
                }
            }

            ScheduleItemType.TODO -> {
                if (triggerValue != null || endValue != null) {
                    warnings += "第 $itemNumber 项是 TODO，已忽略 trigger/end 字段。"
                }
                val effectiveDue = mergeCompatibleTimeAliases(
                    canonicalName = "due",
                    primary = dueValue,
                    compatibilityValue = startValue,
                )
                val parsedDue = effectiveDue?.let {
                    parseTemporal(
                        value = it,
                        fieldName = "due",
                        zoneId = itemZoneId,
                        allowDateOnly = true,
                    )
                }
                dueAt = parsedDue?.instant
                resolvedAllDay = item.allDay || parsedDue?.dateOnly == true
                // Keep the legacy event pipeline safe until typed persistence lands.
                startAt = dueAt
                if (dueAt != null && "due" !in ambiguous) {
                    missing -= "due"
                }
            }
        }

        val priority = parsePriority(item.priority, itemType)
        if (item.priority != null && itemType != ScheduleItemType.TODO) {
            warnings += "第 $itemNumber 项不是 TODO，已忽略 priority。"
        }
        if (item.completed && itemType != ScheduleItemType.TODO) {
            warnings += "第 $itemNumber 项不是 TODO，已忽略 completed。"
        }
        val recurrence = parseRecurrence(item)
        val reminders = normalizeReminders(
            item = item,
            itemNumber = itemNumber,
            defaultReminderMinutes = defaultReminderMinutes,
            warnings = warnings,
        )
        val confidence = item.confidence
        if (!confidence.isFinite() || confidence !in 0.0..1.0) {
            throw ItemValidationException("confidence 必须是 0 到 1 之间的有限数字")
        }

        // Existing editor state only carries missingFields. Mirror ambiguity here
        // so an ambiguous key time cannot be saved silently before the UI phase.
        missing += ambiguous
        if (globalClarification && missing.isEmpty()) {
            missing += "clarification"
        }

        return DraftCalendarEvent(
            title = title,
            notes = item.notes?.trim()?.takeIf(String::isNotEmpty),
            startAt = startAt,
            endAt = endAt,
            allDay = resolvedAllDay,
            reminderMinutes = reminders,
            sourceTranscript = transcript,
            aiConfidence = confidence,
            missingFields = missing.sorted(),
            repeatRule = recurrence.repeatRule,
            itemType = itemType,
            triggerAt = triggerAt,
            dueAt = dueAt,
            location = item.location?.trim()?.takeIf(String::isNotEmpty),
            recurrenceRule = recurrence.canonicalRule,
            priority = priority,
            completed = itemType == ScheduleItemType.TODO && item.completed,
            ambiguousFields = ambiguous.sorted(),
            timeZoneId = itemZoneId.id,
        )
    }

    private fun parseItemType(rawType: String?, legacyEvent: Boolean): ScheduleItemType {
        if (rawType.isNullOrBlank()) {
            if (legacyEvent) return ScheduleItemType.EVENT
            throw ItemValidationException("type 缺失，必须是 EVENT、REMINDER 或 TODO")
        }
        return when (rawType.trim().uppercase(Locale.ROOT)) {
            "EVENT" -> ScheduleItemType.EVENT
            "REMINDER" -> ScheduleItemType.REMINDER
            "TODO" -> ScheduleItemType.TODO
            else -> throw ItemValidationException("未知 type：${rawType.trim()}")
        }
    }

    private fun parsePriority(
        rawPriority: String?,
        itemType: ScheduleItemType,
    ): ScheduleItemPriority? {
        if (itemType != ScheduleItemType.TODO) return null
        if (rawPriority.isNullOrBlank()) return ScheduleItemPriority.NORMAL
        return when (rawPriority.trim().uppercase(Locale.ROOT)) {
            "LOW" -> ScheduleItemPriority.LOW
            "NORMAL", "MEDIUM" -> ScheduleItemPriority.NORMAL
            "HIGH" -> ScheduleItemPriority.HIGH
            "URGENT" -> ScheduleItemPriority.URGENT
            else -> throw ItemValidationException("未知 priority：${rawPriority.trim()}")
        }
    }

    private fun parseZoneId(rawZoneId: String?, fallbackZoneId: ZoneId): ZoneId {
        val cleaned = rawZoneId?.trim()?.takeIf(String::isNotEmpty) ?: return fallbackZoneId
        return try {
            ZoneId.of(cleaned)
        } catch (_: DateTimeException) {
            throw ItemValidationException("time_zone 不是有效 IANA 时区：$cleaned")
        }
    }

    private fun parseTemporal(
        value: String,
        fieldName: String,
        zoneId: ZoneId,
        allowDateOnly: Boolean,
    ): ParsedTemporal {
        val cleaned = value.trim()
        if (cleaned.isEmpty()) {
            throw ItemValidationException("$fieldName 不能为空字符串")
        }

        try {
            val offsetDateTime = OffsetDateTime.parse(cleaned)
            val validOffsets = zoneId.rules.getValidOffsets(offsetDateTime.toLocalDateTime())
            if (offsetDateTime.offset !in validOffsets) {
                throw ItemValidationException(
                    "$fieldName 的 UTC 偏移 ${offsetDateTime.offset} 与时区 ${zoneId.id} 不一致",
                )
            }
            return ParsedTemporal(offsetDateTime.toInstant(), dateOnly = false)
        } catch (_: DateTimeParseException) {
            // Try a local date-time next.
        }

        try {
            val localDateTime = LocalDateTime.parse(cleaned)
            val validOffsets = zoneId.rules.getValidOffsets(localDateTime)
            if (validOffsets.size != 1) {
                val reason = if (validOffsets.isEmpty()) "不存在" else "有歧义"
                throw ItemValidationException(
                    "$fieldName 在时区 ${zoneId.id} 中$reason，请用户确认具体时间",
                )
            }
            return ParsedTemporal(
                instant = localDateTime.atOffset(validOffsets.single()).toInstant(),
                dateOnly = false,
            )
        } catch (_: DateTimeParseException) {
            // Try a date-only value if the item type allows it.
        }

        if (allowDateOnly) {
            try {
                val localDate = LocalDate.parse(cleaned)
                return ParsedTemporal(
                    instant = localDate.atStartOfDay(zoneId).toInstant(),
                    dateOnly = true,
                )
            } catch (_: DateTimeParseException) {
                // Fall through to the field-specific validation error.
            }
        }
        throw ItemValidationException(
            "$fieldName 不是有效 ISO-8601 ${if (allowDateOnly) "日期或日期时间" else "日期时间"}：$cleaned",
        )
    }

    private fun parseRecurrence(item: AiCalendarEvent): ParsedRecurrence {
        val rawRule = item.recurrenceRule?.trim()?.takeIf(String::isNotEmpty)
        if (rawRule != null) {
            val parts = rawRule.uppercase(Locale.ROOT).split(";")
            val tokens = mutableMapOf<String, String>()
            parts.forEach { part ->
                val keyValue = part.split("=", limit = 2)
                if (keyValue.size != 2 || keyValue[0].isBlank() || keyValue[1].isBlank()) {
                    throw ItemValidationException("recurrence_rule 不是有效 RRULE：$rawRule")
                }
                if (tokens.put(keyValue[0], keyValue[1]) != null) {
                    throw ItemValidationException("recurrence_rule 含重复字段：${keyValue[0]}")
                }
            }
            val unsupported = tokens.keys - SUPPORTED_RECURRENCE_KEYS
            if (unsupported.isNotEmpty()) {
                throw ItemValidationException(
                    "recurrence_rule 含暂不支持字段：${unsupported.sorted().joinToString()}",
                )
            }
            val frequency = parseRepeatFrequency(tokens["FREQ"])
            val count = tokens["COUNT"]?.toIntOrNull()
                ?: defaultRepeatCount(frequency)
            if (count !in MIN_REPEAT_COUNT..MAX_REPEAT_COUNT) {
                throw ItemValidationException(
                    "recurrence_rule 的 COUNT 必须在 $MIN_REPEAT_COUNT 到 $MAX_REPEAT_COUNT 之间",
                )
            }
            return ParsedRecurrence(
                canonicalRule = tokens.entries.joinToString(";") { (key, value) -> "$key=$value" },
                repeatRule = RepeatRule(frequency = frequency, count = count),
            )
        }

        val rawFrequency = item.repeatFrequency?.trim()?.takeIf(String::isNotEmpty)
            ?: return ParsedRecurrence(canonicalRule = null, repeatRule = null)
        val frequency = parseRepeatFrequency(rawFrequency)
        val count = item.repeatCount ?: defaultRepeatCount(frequency)
        if (count !in MIN_REPEAT_COUNT..MAX_REPEAT_COUNT) {
            throw ItemValidationException(
                "repeat_count 必须在 $MIN_REPEAT_COUNT 到 $MAX_REPEAT_COUNT 之间",
            )
        }
        return ParsedRecurrence(
            canonicalRule = "FREQ=${frequency.name};COUNT=$count",
            repeatRule = RepeatRule(frequency = frequency, count = count),
        )
    }

    private fun parseRepeatFrequency(rawFrequency: String?): RepeatFrequency =
        when (rawFrequency?.trim()?.uppercase(Locale.ROOT)) {
            "DAILY", "DAY", "每天", "每日" -> RepeatFrequency.DAILY
            "WEEKLY", "WEEK", "每周" -> RepeatFrequency.WEEKLY
            null, "" -> throw ItemValidationException("recurrence_rule 缺少 FREQ")
            else -> throw ItemValidationException("未知重复频率：${rawFrequency.orEmpty().trim()}")
        }

    private fun defaultRepeatCount(frequency: RepeatFrequency): Int =
        when (frequency) {
            RepeatFrequency.DAILY -> 7
            RepeatFrequency.WEEKLY -> 8
        }

    private fun normalizeReminders(
        item: AiCalendarEvent,
        itemNumber: Int,
        defaultReminderMinutes: Int,
        warnings: MutableList<String>,
    ): List<Int> {
        if (
            item.reminderOffsets.isNotEmpty() &&
            item.reminderMinutes.isNotEmpty() &&
            item.reminderOffsets != item.reminderMinutes
        ) {
            throw ItemValidationException("reminder_offsets 与 reminder_minutes 互相冲突")
        }
        val supplied = when {
            item.reminderOffsets.isNotEmpty() -> item.reminderOffsets
            item.reminderMinutes.isNotEmpty() -> item.reminderMinutes
            else -> listOf(defaultReminderMinutes)
        }
        val valid = supplied.filter { it in 0..MAX_REMINDER_OFFSET_MINUTES }
        if (valid.size != supplied.size) {
            warnings +=
                "第 $itemNumber 项含无效提醒偏移，已仅保留 0 到 $MAX_REMINDER_OFFSET_MINUTES 分钟。"
        }
        return valid
            .plus(0)
            .distinct()
            .sorted()
    }

    private fun normalizeFields(
        values: List<String>,
        fieldListName: String,
        itemNumber: Int,
        warnings: MutableList<String>,
    ): List<String> {
        val normalized = mutableListOf<String>()
        values.forEach { raw ->
            val field = FIELD_ALIASES[raw.trim().lowercase(Locale.ROOT)]
            if (field == null) {
                warnings += "第 $itemNumber 项的 $fieldListName 含未知字段“${raw.trim()}”，已忽略。"
            } else {
                normalized += field
            }
        }
        return normalized.distinct().sorted()
    }

    private fun uniqueValue(fieldName: String, vararg values: String?): String? {
        val distinct = values
            .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
            .distinct()
        if (distinct.size > 1) {
            throw ItemValidationException("$fieldName 的多个兼容字段互相冲突")
        }
        return distinct.firstOrNull()
    }

    private fun mergeCompatibleTimeAliases(
        canonicalName: String,
        primary: String?,
        compatibilityValue: String?,
    ): String? {
        if (primary != null && compatibilityValue != null && primary != compatibilityValue) {
            throw ItemValidationException("$canonicalName 与兼容 start 字段互相冲突")
        }
        return primary ?: compatibilityValue
    }

    private data class ParsedTemporal(
        val instant: Instant,
        val dateOnly: Boolean,
    )

    private data class ParsedRecurrence(
        val canonicalRule: String?,
        val repeatRule: RepeatRule?,
    )

    private class ItemValidationException(message: String) : IllegalArgumentException(message)

    private val FIELD_ALIASES = mapOf(
        "title" to "title",
        "start" to "start_local",
        "start_local" to "start_local",
        "startdatetime" to "start_local",
        "start_date_time" to "start_local",
        "end" to "end_local",
        "end_local" to "end_local",
        "enddatetime" to "end_local",
        "end_date_time" to "end_local",
        "trigger" to "trigger",
        "trigger_local" to "trigger",
        "triggerdatetime" to "trigger",
        "trigger_date_time" to "trigger",
        "due" to "due",
        "due_local" to "due",
        "duedatetime" to "due",
        "due_date_time" to "due",
        "time_zone" to "time_zone",
        "timezone" to "time_zone",
        "location" to "location",
        "recurrence_rule" to "recurrence_rule",
        "recurrencerule" to "recurrence_rule",
        "priority" to "priority",
        "reminder_offsets" to "reminder_offsets",
        "reminder_minutes" to "reminder_offsets",
        "clarification" to "clarification",
    )
    private val SUPPORTED_RECURRENCE_KEYS = setOf("FREQ", "COUNT", "INTERVAL", "BYDAY", "UNTIL")
    private const val MIN_REPEAT_COUNT = 2
    private const val MAX_REPEAT_COUNT = 60
    private const val MAX_REMINDER_OFFSET_MINUTES = 525_600
    private const val MAX_WARNING_LENGTH = 240
    private const val EVENT_START_FIELD = "start_local"
}
