package com.voicememos.aicalendar.ui.session

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceScheduleSessionTest {
    @Test
    fun speechLifecycleStopsAtEditableTranscript() {
        val startedAt = Instant.parse("2026-07-25T08:00:00Z")
        val listeningAt = startedAt.plusSeconds(1)
        val partialAt = startedAt.plusSeconds(2)
        val transcribingAt = startedAt.plusSeconds(3)
        val readyAt = startedAt.plusSeconds(4)

        val session = VoiceScheduleSession.fresh(startedAt)
            .startListening(listeningAt)
            .updatePartialTranscript("明天下午", partialAt)
            .beginTranscribing(transcribingAt)
            .completeTranscript("明天下午三点开会", readyAt)

        assertEquals(VoiceTaskState.TranscriptReady, session.state)
        assertEquals("明天下午三点开会", session.rawTranscript)
        assertEquals("明天下午三点开会", session.editedTranscript)
        assertEquals("", session.partialTranscript)
        assertEquals(startedAt, session.startedAt)
        assertEquals(readyAt, session.updatedAt)
        assertFalse(session.isListening)
        assertTrue(session.canBeginParsing)
    }

    @Test
    fun editingAndFailurePreserveRawAndEditedText() {
        val session = VoiceScheduleSession.fresh(Instant.EPOCH)
            .completeTranscript("明天下午开会", Instant.EPOCH.plusSeconds(1))
            .updateEditedTranscript("明天下午三点开会", Instant.EPOCH.plusSeconds(2))
            .beginParsing(Instant.EPOCH.plusSeconds(3))
            .fail("网络不可用", Instant.EPOCH.plusSeconds(4))

        assertEquals(VoiceTaskState.Failure, session.state)
        assertEquals("明天下午开会", session.rawTranscript)
        assertEquals("明天下午三点开会", session.editedTranscript)
        assertEquals(listOf("网络不可用"), session.errors)
        assertTrue(session.canBeginParsing)
    }

    @Test
    fun recognitionFailurePreservesPartialText() {
        val session = VoiceScheduleSession.fresh(Instant.EPOCH)
            .startListening()
            .updatePartialTranscript("明天下午三点")
            .beginTranscribing()
            .fail("识别服务暂时不可用")

        assertEquals(VoiceTaskState.Failure, session.state)
        assertEquals("明天下午三点", session.partialTranscript)
        assertEquals(listOf("识别服务暂时不可用"), session.errors)
    }

    @Test
    fun clarificationPreviewAndSaveUseOneSession() {
        val prepared = VoiceScheduleSession.fresh(Instant.EPOCH)
            .prepareTranscript("下周找导师")
            .beginParsing()
            .needsClarification(listOf("缺少日期"))

        assertEquals(VoiceTaskState.NeedsClarification, prepared.state)
        assertEquals(listOf("缺少日期"), prepared.warnings)

        val saved = prepared
            .showPreview(emptyList())
            .beginSaving()
            .succeed(listOf("event-1", "event-2"))

        assertEquals(VoiceTaskState.Success, saved.state)
        assertEquals(listOf("event-1", "event-2"), saved.createdItemIds)
        assertEquals("下周找导师", saved.rawTranscript)
        assertEquals("下周找导师", saved.editedTranscript)
        assertTrue(saved.errors.isEmpty())
    }

    @Test
    fun partialSuccessRetainsCreatedIdsAndErrors() {
        val session = VoiceScheduleSession.fresh(Instant.EPOCH)
            .prepareTranscript("创建两项")
            .beginParsing()
            .showPreview(emptyList())
            .beginSaving()
            .partiallySucceed(
                createdIds = listOf("event-1"),
                errorMessages = listOf("第二项保存失败"),
            )

        assertEquals(VoiceTaskState.PartialSuccess, session.state)
        assertEquals(listOf("event-1"), session.createdItemIds)
        assertEquals(listOf("第二项保存失败"), session.errors)
        assertEquals("创建两项", session.editedTranscript)
    }

    @Test
    fun parsingCannotBeginBeforeTranscriptIsReady() {
        val session = VoiceScheduleSession.fresh(Instant.EPOCH)

        assertFalse(session.canBeginParsing)
        assertThrows(IllegalStateException::class.java) {
            session.beginParsing()
        }
    }

    @Test
    fun freshSessionHasUniqueIdentity() {
        val first = VoiceScheduleSession.fresh(Instant.EPOCH)
        val second = VoiceScheduleSession.fresh(Instant.EPOCH)

        assertNotEquals(first.sessionId, second.sessionId)
    }
}
