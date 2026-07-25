package com.voicememos.aicalendar.ui.session

sealed interface VoiceTaskState {
    data object Idle : VoiceTaskState

    data object Listening : VoiceTaskState

    data object Transcribing : VoiceTaskState

    data object TranscriptReady : VoiceTaskState

    data object Parsing : VoiceTaskState

    data object NeedsClarification : VoiceTaskState

    data object Preview : VoiceTaskState

    data object Saving : VoiceTaskState

    data object Success : VoiceTaskState

    data object PartialSuccess : VoiceTaskState

    data object Failure : VoiceTaskState
}
