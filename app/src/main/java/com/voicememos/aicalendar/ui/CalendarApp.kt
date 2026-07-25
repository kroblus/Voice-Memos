package com.voicememos.aicalendar.ui

import android.Manifest
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardVoice
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.voicememos.aicalendar.R
import com.voicememos.aicalendar.data.exportEventsIcs
import com.voicememos.aicalendar.data.exportEventsJson
import com.voicememos.aicalendar.domain.CalendarEvent
import com.voicememos.aicalendar.domain.EventStatus
import com.voicememos.aicalendar.domain.ParseHistoryCreationStatus
import com.voicememos.aicalendar.domain.ParseHistoryItem
import com.voicememos.aicalendar.domain.RepeatFrequency
import com.voicememos.aicalendar.domain.ScheduleItemPriority
import com.voicememos.aicalendar.domain.ScheduleItemType
import com.voicememos.aicalendar.speech.SpeechInputController
import com.voicememos.aicalendar.ui.session.CreationBatchSummary
import com.voicememos.aicalendar.ui.session.CreationItemStatus
import com.voicememos.aicalendar.ui.session.VoiceTaskState
import com.voicememos.aicalendar.ui.theme.ShengliTheme
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private sealed interface PendingDraftSave {
    data object All : PendingDraftSave
    data class One(val draftId: String) : PendingDraftSave
}

private fun PendingDraftSave.toSavedValue(): String =
    when (this) {
        PendingDraftSave.All -> "all"
        is PendingDraftSave.One -> "one:$draftId"
    }

private fun String.toPendingDraftSave(): PendingDraftSave? =
    when {
        this == "all" -> PendingDraftSave.All
        startsWith("one:") -> removePrefix("one:")
            .takeIf(String::isNotBlank)
            ?.let(PendingDraftSave::One)
        else -> null
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarApp(viewModel: CalendarViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ShengliTheme(darkTheme = state.themeMode == AppThemeMode.DARK) {
        CalendarWorkbench(viewModel = viewModel, state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarWorkbench(
    viewModel: CalendarViewModel,
    state: CalendarUiState,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissionStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val speechController = remember {
        SpeechInputController(
            context = context,
            onListeningChanged = viewModel::onListeningChanged,
            onPartialResult = viewModel::onPartialTranscript,
            onFinalResult = viewModel::onFinalTranscript,
            onError = viewModel::showError,
        )
    }
    DisposableEffect(Unit) {
        onDispose { speechController.destroy() }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.successMessage) {
        state.successMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.consumeSuccessMessage()
        }
    }

    val microphonePermissionRequiredMessage = stringResource(R.string.voice_permission_required)
    val recordAudioLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            speechController.start()
        } else {
            viewModel.showError(microphonePermissionRequiredMessage)
        }
    }
    var pendingDraftSaveValue by rememberSaveable { mutableStateOf<String?>(null) }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        viewModel.refreshPermissionStatus()
        when (val pending = pendingDraftSaveValue?.toPendingDraftSave()) {
            PendingDraftSave.All -> viewModel.confirmAllDrafts()
            is PendingDraftSave.One -> viewModel.confirmDraft(pending.draftId)
            null -> Unit
        }
        pendingDraftSaveValue = null
    }
    val backupExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        uri?.let { viewModel.exportBackup(context.contentResolver, it) }
    }
    val backupImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { viewModel.importBackup(context.contentResolver, it) }
    }
    val toggleVoiceInput = {
        viewModel.openInputCurtain()
        if (state.isListening) {
            speechController.stop()
        } else {
            recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    val dismissInputCurtain = {
        if (state.isListening) speechController.stop()
        viewModel.closeInputCurtain()
    }
    val switchToTextInput = {
        speechController.cancel()
        viewModel.switchToTextInput()
    }
    val requestDraftSave: (PendingDraftSave) -> Unit = { pending ->
        val needsNotificationPermission =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        if (needsNotificationPermission) {
            pendingDraftSaveValue = pending.toSavedValue()
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            when (pending) {
                PendingDraftSave.All -> viewModel.confirmAllDrafts()
                is PendingDraftSave.One -> viewModel.confirmDraft(pending.draftId)
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp)
                    .padding(top = 14.dp, bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Header(
                    selectedDate = state.selectedDate,
                    themeMode = state.themeMode,
                    apiKeySaved = state.apiKeySaved,
                    onToday = viewModel::selectToday,
                    onThemeToggle = {
                        viewModel.setThemeMode(
                            if (state.themeMode == AppThemeMode.DARK) AppThemeMode.LIGHT else AppThemeMode.DARK,
                        )
                    },
                    onHistory = viewModel::openHistory,
                    onSettings = viewModel::openSettings,
                )
                ViewModeSelector(
                    selected = state.viewMode,
                    onSelect = viewModel::setViewMode,
                )
                CalendarSurface(
                    modifier = Modifier.weight(1f, fill = false),
                    state = state,
                    onDateSelected = viewModel::selectDate,
                    onMarkDone = viewModel::markDone,
                    onDelete = viewModel::deleteEvent,
                    onEventSelected = viewModel::openEventEditor,
                    onAgendaQueryChanged = viewModel::updateAgendaQuery,
                    onAgendaFilterSelected = viewModel::setAgendaFilter,
                    onClearCompleted = viewModel::clearCompletedEvents,
                )
                WarningsAndErrors(state)
                DraftReturnBar(
                    drafts = state.draftEditors,
                    visible = state.draftEditors.isNotEmpty() &&
                        !state.showDraftSheet &&
                        state.voiceTaskState !in setOf(
                            VoiceTaskState.Parsing,
                            VoiceTaskState.Saving,
                        ),
                    onOpen = viewModel::showDraftSheet,
                )
            }
            VoiceInputFab(
                isListening = state.isListening,
                isBusy = state.voiceTaskState in setOf(
                    VoiceTaskState.Transcribing,
                    VoiceTaskState.Parsing,
                    VoiceTaskState.Saving,
                ),
                onClick = toggleVoiceInput,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 18.dp),
            )
        }
    }

    if (state.showInputCurtain) {
        ModalBottomSheet(
            onDismissRequest = dismissInputCurtain,
            shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
        ) {
            VoiceInputCurtain(
                state = state,
                onTextChanged = viewModel::updateTextInput,
                onSubmit = viewModel::parseCurrentText,
                onVoiceInput = toggleVoiceInput,
                onTextInput = switchToTextInput,
                onOpenSettings = viewModel::openSettings,
                onDismiss = dismissInputCurtain,
            )
        }
    }

    if (state.showSettings) {
        ModalBottomSheet(
            onDismissRequest = viewModel::closeSettings,
            shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
        ) {
            SettingsSheet(
                state = state,
                onApiKeyChanged = viewModel::updateApiKeyInput,
                onDefaultReminderChanged = viewModel::updateDefaultReminderMinutesInput,
                onDefaultDurationChanged = viewModel::updateDefaultDurationMinutesInput,
                onExportJson = {
                    backupExportLauncher.launch("shengli-backup.json")
                },
                onImportJson = {
                    backupImportLauncher.launch(arrayOf("application/json", "text/*"))
                },
                onExportIcs = {
                    shareEvents(context, state.events, CalendarExportFormat.ICS, viewModel::showError)
                },
                onPrivacy = viewModel::showPrivacyDetails,
                onReset = viewModel::requestResetConfirmation,
                onDismissReset = viewModel::dismissResetConfirmation,
                onConfirmReset = viewModel::deleteAllLocalData,
                onOpenAppSettings = viewModel::openAppSettings,
                onOpenNotificationSettings = viewModel::openNotificationSettings,
                onOpenExactAlarmSettings = viewModel::openExactAlarmSettings,
                onRefreshPermissions = viewModel::refreshPermissionStatus,
                onTestConnection = viewModel::testAiConnection,
                onSave = viewModel::saveApiKey,
                onClearApiKey = viewModel::clearApiKey,
                onClose = viewModel::closeSettings,
            )
        }
    }

    if (state.showHistory) {
        ModalBottomSheet(
            onDismissRequest = viewModel::closeHistory,
            shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
        ) {
            RecognitionHistorySheet(
                history = state.parseHistory,
                onEditTranscript = viewModel::reuseTranscript,
                onReparse = viewModel::regenerateTranscript,
                onDelete = viewModel::deleteParseHistory,
                onClear = viewModel::clearParseHistory,
                onClose = viewModel::closeHistory,
            )
        }
    }

    if (!state.onboardingSeen) {
        ModalBottomSheet(
            onDismissRequest = {},
            shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
        ) {
            OnboardingSheet(
                state = state,
                onApiKeyChanged = viewModel::updateApiKeyInput,
                onDefaultReminderChanged = viewModel::updateDefaultReminderMinutesInput,
                onDefaultDurationChanged = viewModel::updateDefaultDurationMinutesInput,
                onComplete = viewModel::completeOnboarding,
                onOpenSystemSettings = viewModel::openAppSettings,
            )
        }
    }

    if (state.showPrivacyDetails) {
        ModalBottomSheet(
            onDismissRequest = viewModel::hidePrivacyDetails,
            shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
        ) {
            PrivacyDetailsSheet(onClose = viewModel::hidePrivacyDetails)
        }
    }

    if (state.draftEditors.isNotEmpty() && state.showDraftSheet) {
        ModalBottomSheet(
            onDismissRequest = viewModel::hideDraftSheet,
            shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
        ) {
            DraftPanel(
                state = state,
                onUpdate = viewModel::updateDraft,
                onConfirmAll = { requestDraftSave(PendingDraftSave.All) },
                onConfirm = { draftId -> requestDraftSave(PendingDraftSave.One(draftId)) },
                onAcceptRisk = viewModel::acceptDraftRisks,
                onDiscard = viewModel::discardDraft,
                onClear = viewModel::clearDrafts,
            )
        }
    }

    state.creationSummary
        ?.takeIf { state.showCreationResult }
        ?.let { summary ->
            ModalBottomSheet(
                onDismissRequest = viewModel::dismissCreationResult,
                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
            ) {
                CreationResultSheet(
                    summary = summary,
                    onViewSchedule = if (summary.createdCount > 0) {
                        viewModel::dismissCreationResult
                    } else {
                        viewModel::retryFailedCreation
                    },
                    onRetryFailed = viewModel::retryFailedCreation,
                    onUndo = viewModel::undoLastCreation,
                    onOpenSettings = viewModel::openSettingsFromCreationResult,
                    onClose = viewModel::dismissCreationResult,
                )
            }
        }

    state.eventEditor?.let { editor ->
        ModalBottomSheet(
            onDismissRequest = viewModel::closeEventEditor,
            shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
        ) {
            EventEditorSheet(
                editor = editor,
                onUpdate = viewModel::updateEventEditor,
                onSave = viewModel::saveEventEditor,
                onClose = viewModel::closeEventEditor,
            )
        }
    }
}

@Composable
private fun OnboardingSheet(
    state: CalendarUiState,
    onApiKeyChanged: (String) -> Unit,
    onDefaultReminderChanged: (String) -> Unit,
    onDefaultDurationChanged: (String) -> Unit,
    onComplete: () -> Unit,
    onOpenSystemSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .testTag("onboarding-sheet"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("欢迎来到声历", style = MaterialTheme.typography.headlineSmall)
        Text(
            "说一句话，把日程留在本机。生成时才会把文本发送给 DeepSeek。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OnboardingPoint("本地日历", "日程、草稿和历史保存在设备里，不需要账号。")
        OnboardingPoint("语音优先", "麦克风权限只在录音时请求，也可以直接输入文字。")
        OnboardingPoint("准点提醒", "保存事件时会保留 0 分钟提醒，并按需请求通知权限。")
        OutlinedTextField(
            value = state.apiKeyInput,
            onValueChange = onApiKeyChanged,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("onboarding-api-key-field"),
            label = { Text("DeepSeek API key（可稍后填写）") },
            singleLine = true,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = state.defaultDurationMinutesInput,
                onValueChange = onDefaultDurationChanged,
                modifier = Modifier.weight(1f),
                label = { Text("默认时长") },
                suffix = { Text("分钟") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OutlinedTextField(
                value = state.defaultReminderMinutesInput,
                onValueChange = onDefaultReminderChanged,
                modifier = Modifier.weight(1f),
                label = { Text("提前提醒") },
                suffix = { Text("分钟") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onOpenSystemSettings) {
                Text("系统权限")
            }
            Button(
                onClick = onComplete,
                modifier = Modifier.testTag("onboarding-complete-button"),
            ) {
                Text("开始使用")
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun OnboardingPoint(
    title: String,
    body: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PrivacyDetailsSheet(
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .testTag("privacy-details-sheet"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("隐私与数据", style = MaterialTheme.typography.headlineSmall)
        PrivacyLine("本地保存", "日程、提醒、解析历史保存在本机数据库。")
        PrivacyLine("按需联网", "只有点击生成时，输入文本才会发送给 DeepSeek 解析。")
        PrivacyLine("密钥加密", "DeepSeek API key 由 Android Keystore 加密保存。")
        PrivacyLine("不做画像", "没有账号、广告、分析 SDK，也不做云同步。")
        PrivacyLine("显式备份", "导出的备份只包含日程数据，不包含 API key。")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Button(onClick = onClose) { Text("知道了") }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PrivacyLine(
    title: String,
    body: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Header(
    selectedDate: LocalDate,
    themeMode: AppThemeMode,
    apiKeySaved: Boolean,
    onToday: () -> Unit,
    onThemeToggle: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text("声历", style = MaterialTheme.typography.displayMedium)
            Text(
                selectedDate.format(DateTimeFormatter.ofPattern("yyyy年M月d日 E", Locale.SIMPLIFIED_CHINESE)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onToday,
                modifier = Modifier.testTag("today-button"),
            ) {
                Icon(Icons.Rounded.Today, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("今天")
            }
            IconButton(
                onClick = onThemeToggle,
                modifier = Modifier.testTag("theme-toggle-button"),
            ) {
                Icon(
                    imageVector = if (themeMode == AppThemeMode.DARK) {
                        Icons.Rounded.LightMode
                    } else {
                        Icons.Rounded.DarkMode
                    },
                    contentDescription = if (themeMode == AppThemeMode.DARK) "切换浅色" else "切换深色",
                )
            }
            IconButton(
                onClick = onHistory,
                modifier = Modifier.testTag("history-button"),
            ) {
                Icon(
                    Icons.Rounded.History,
                    contentDescription = stringResource(R.string.history_title),
                )
            }
            IconButton(
                onClick = onSettings,
                modifier = Modifier.testTag("settings-button"),
            ) {
                Box {
                    Icon(
                        Icons.Rounded.Settings,
                        contentDescription = stringResource(R.string.settings_title),
                    )
                    if (!apiKeySaved) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondary),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ViewModeSelector(
    selected: CalendarViewMode,
    onSelect: (CalendarViewMode) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CalendarViewMode.entries.forEach { mode ->
            FilterChip(
                selected = selected == mode,
                onClick = { onSelect(mode) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("view-mode-${mode.name.lowercase()}"),
                label = { Text(mode.label) },
            )
        }
    }
}

@Composable
private fun CalendarSurface(
    modifier: Modifier = Modifier,
    state: CalendarUiState,
    onDateSelected: (LocalDate) -> Unit,
    onMarkDone: (String) -> Unit,
    onDelete: (String) -> Unit,
    onEventSelected: (String) -> Unit,
    onAgendaQueryChanged: (String) -> Unit,
    onAgendaFilterSelected: (AgendaFilter) -> Unit,
    onClearCompleted: () -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 240.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (state.viewMode) {
                CalendarViewMode.MONTH -> MonthGrid(
                    selectedDate = state.selectedDate,
                    events = state.events,
                    onDateSelected = onDateSelected,
                )
                CalendarViewMode.WEEK -> WeekStrip(
                    selectedDate = state.selectedDate,
                    events = state.events,
                    onDateSelected = onDateSelected,
                )
                CalendarViewMode.DAY -> {
                    DayHeading(state.selectedDate)
                    NextEventBanner(selectedDate = state.selectedDate, events = state.events)
                }
                CalendarViewMode.AGENDA -> AgendaControls(
                    query = state.agendaQuery,
                    filter = state.agendaFilter,
                    completedCount = state.events.count { it.status == EventStatus.DONE },
                    onQueryChanged = onAgendaQueryChanged,
                    onFilterSelected = onAgendaFilterSelected,
                    onClearCompleted = onClearCompleted,
                )
            }
            EventList(
                selectedDate = state.selectedDate,
                mode = state.viewMode,
                events = state.events,
                agendaQuery = state.agendaQuery,
                agendaFilter = state.agendaFilter,
                onMarkDone = onMarkDone,
                onDelete = onDelete,
                onEventSelected = onEventSelected,
            )
        }
    }
}

@Composable
private fun MonthGrid(
    selectedDate: LocalDate,
    events: List<CalendarEvent>,
    onDateSelected: (LocalDate) -> Unit,
) {
    val month = YearMonth.from(selectedDate)
    val firstDay = month.atDay(1)
    val leadingBlanks = firstDay.dayOfWeek.value % 7
    val days = buildList {
        repeat(leadingBlanks) { add(null) }
        for (day in 1..month.lengthOfMonth()) add(month.atDay(day))
    }
    val eventDates = events.map { it.startAt.atZone(ZoneId.systemDefault()).toLocalDate() }.toSet()

    LazyVerticalGrid(
        columns = GridCells.Fixed(7),
        modifier = Modifier.height(370.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(
            items = listOf("日", "一", "二", "三", "四", "五", "六").mapIndexed { index, label -> index to label },
            key = { it.first },
        ) { (index, label) ->
            Box(
                modifier = Modifier
                    .height(34.dp)
                    .testTag("weekday-$index"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(days) { date ->
            if (date == null) {
                Spacer(modifier = Modifier.height(48.dp))
            } else {
                DayCell(
                    date = date,
                    selected = date == selectedDate,
                    hasEvent = date in eventDates,
                    onClick = { onDateSelected(date) },
                )
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    selected: Boolean,
    hasEvent: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier = Modifier
            .height(48.dp)
            .testTag("day-${date.dayOfMonth}")
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .border(
                1.dp,
                if (date == LocalDate.now()) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape,
            )
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelLarge,
        )
        Box(
            modifier = Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(if (hasEvent) MaterialTheme.colorScheme.secondary else Color.Transparent),
        )
    }
}

@Composable
private fun WeekStrip(
    selectedDate: LocalDate,
    events: List<CalendarEvent>,
    onDateSelected: (LocalDate) -> Unit,
) {
    val start = selectedDate.minusDays((selectedDate.dayOfWeek.value % 7).toLong())
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        (0..6).map { start.plusDays(it.toLong()) }.forEach { date ->
            val hasEvent = events.any { it.startAt.atZone(ZoneId.systemDefault()).toLocalDate() == date }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (date == selectedDate) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.background
                        },
                    )
                    .clickable { onDateSelected(date) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.SIMPLIFIED_CHINESE),
                    color = if (date == selectedDate) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    date.dayOfMonth.toString(),
                    color = if (date == selectedDate) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    fontWeight = FontWeight.Bold,
                )
                if (hasEvent) {
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(
                                if (date == selectedDate) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.secondary
                                },
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayHeading(selectedDate: LocalDate) {
    Text(
        selectedDate.format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.SIMPLIFIED_CHINESE)),
        style = MaterialTheme.typography.headlineSmall,
    )
}

@Composable
private fun NextEventBanner(
    selectedDate: LocalDate,
    events: List<CalendarEvent>,
) {
    val zone = ZoneId.systemDefault()
    val now = java.time.Instant.now()
    val next = events
        .filter { event ->
            event.status == EventStatus.SCHEDULED &&
                event.startAt.atZone(zone).toLocalDate() == selectedDate &&
                (selectedDate != LocalDate.now(zone) || !event.startAt.isBefore(now))
        }
        .minByOrNull { it.startAt }
        ?: return

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("day-next-event-banner"),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.background,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Notifications, contentDescription = null)
            Text(
                "下一项 ${next.startAt.atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm"))} ${next.title}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun AgendaControls(
    query: String,
    filter: AgendaFilter,
    completedCount: Int,
    onQueryChanged: (String) -> Unit,
    onFilterSelected: (AgendaFilter) -> Unit,
    onClearCompleted: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("近期", style = MaterialTheme.typography.headlineSmall)
            if (completedCount > 0) {
                TextButton(
                    onClick = onClearCompleted,
                    modifier = Modifier.testTag("clear-completed-button"),
                ) {
                    Icon(Icons.Rounded.Delete, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("清理已完成")
                }
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChanged,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("agenda-search-field"),
            shape = RoundedCornerShape(8.dp),
            label = { Text("搜索日程") },
            singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AgendaFilter.entries.forEach { option ->
                FilterChip(
                    selected = filter == option,
                    onClick = { onFilterSelected(option) },
                    label = { Text(option.label) },
                )
            }
        }
    }
}

@Composable
private fun EventList(
    selectedDate: LocalDate,
    mode: CalendarViewMode,
    events: List<CalendarEvent>,
    agendaQuery: String,
    agendaFilter: AgendaFilter,
    onMarkDone: (String) -> Unit,
    onDelete: (String) -> Unit,
    onEventSelected: (String) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val visible = when (mode) {
        CalendarViewMode.AGENDA -> events
            .filter { event ->
                when (agendaFilter) {
                    AgendaFilter.ALL -> true
                    AgendaFilter.SCHEDULED -> event.status == EventStatus.SCHEDULED
                    AgendaFilter.DONE -> event.status == EventStatus.DONE
                }
            }
            .filter { event ->
                val query = agendaQuery.trim()
                query.isEmpty() ||
                    event.title.contains(query, ignoreCase = true) ||
                    event.notes.orEmpty().contains(query, ignoreCase = true)
            }
            .sortedBy { it.startAt }
            .take(50)
        else -> events
            .filter { it.startAt.atZone(zone).toLocalDate() == selectedDate }
            .sortedBy { it.startAt }
    }
    if (visible.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                if (selectedDate == LocalDate.now(zone)) {
                    "今天 · ${selectedDate.format(DateTimeFormatter.ofPattern("M月d日"))}"
                } else {
                    selectedDate.format(DateTimeFormatter.ofPattern("M月d日 E", Locale.SIMPLIFIED_CHINESE))
                },
                style = MaterialTheme.typography.titleMedium,
            )
            Text("暂无安排", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "试着说：\n“今晚八点提醒我跑步”\n“下周一上午十点开项目会”",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val nextEventId = if (mode == CalendarViewMode.DAY) {
        visible.nextEventId(selectedDate, zone)
    } else {
        null
    }

    LazyColumn(
        modifier = Modifier.testTag("event-list"),
        contentPadding = PaddingValues(bottom = 84.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (mode == CalendarViewMode.AGENDA) {
            visible.groupBy { it.startAt.atZone(zone).toLocalDate() }.forEach { (date, dailyEvents) ->
                item(key = "date-$date") {
                    Text(
                        date.format(DateTimeFormatter.ofPattern("M月d日 E", Locale.SIMPLIFIED_CHINESE)),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(dailyEvents, key = { it.id }) { event ->
                    EventRow(
                        event = event,
                        onMarkDone = onMarkDone,
                        onDelete = onDelete,
                        onEventSelected = onEventSelected,
                    )
                }
            }
        } else if (mode == CalendarViewMode.DAY) {
            items(visible, key = { it.id }) { event ->
                TimelineEventRow(
                    event = event,
                    highlighted = event.id == nextEventId,
                    onMarkDone = onMarkDone,
                    onDelete = onDelete,
                    onEventSelected = onEventSelected,
                )
            }
        } else {
            items(visible, key = { it.id }) { event ->
                EventRow(
                    event = event,
                    onMarkDone = onMarkDone,
                    onDelete = onDelete,
                    onEventSelected = onEventSelected,
                )
            }
        }
    }
}

private fun List<CalendarEvent>.nextEventId(selectedDate: LocalDate, zone: ZoneId): String? {
    val today = LocalDate.now(zone)
    val now = java.time.Instant.now()
    return filter { event ->
        event.status == EventStatus.SCHEDULED &&
            when {
                selectedDate.isBefore(today) -> false
                selectedDate == today -> !event.startAt.isBefore(now)
                else -> true
            }
    }.minByOrNull { it.startAt }?.id
}

@Composable
private fun TimelineEventRow(
    event: CalendarEvent,
    highlighted: Boolean,
    onMarkDone: (String) -> Unit,
    onDelete: (String) -> Unit,
    onEventSelected: (String) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val shape = RoundedCornerShape(8.dp)
    val muted = event.status == EventStatus.DONE
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("day-timeline-row"),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier
                .width(58.dp)
                .padding(top = 10.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                event.timelineStartLabel(zone),
                style = MaterialTheme.typography.labelLarge,
                color = if (highlighted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            event.timelineEndLabel(zone)?.let { end ->
                Text(
                    end,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box(
            modifier = Modifier
                .padding(top = 16.dp)
                .size(if (highlighted) 12.dp else 9.dp)
                .clip(CircleShape)
                .background(
                    when {
                        muted -> MaterialTheme.colorScheme.outlineVariant
                        highlighted -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.secondary
                    },
                ),
        )
        Surface(
            modifier = Modifier
                .weight(1f)
                .clip(shape)
                .clickable { onEventSelected(event.id) },
            shape = shape,
            color = if (highlighted) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.background
            },
            tonalElevation = if (highlighted) 2.dp else 0.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (highlighted) {
                        Text(
                            "下一个提醒",
                            modifier = Modifier.testTag("day-next-reminder-label"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        event.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    event.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                        Text(
                            notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(onClick = { onMarkDone(event.id) }) {
                    Icon(Icons.Rounded.Check, contentDescription = "完成")
                }
                IconButton(onClick = { onDelete(event.id) }) {
                    Icon(Icons.Rounded.Delete, contentDescription = "删除")
                }
            }
        }
    }
}

private fun CalendarEvent.timelineStartLabel(zone: ZoneId): String =
    if (allDay) {
        "全天"
    } else {
        startAt.atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm"))
    }

private fun CalendarEvent.timelineEndLabel(zone: ZoneId): String? =
    if (allDay) {
        null
    } else {
        endAt?.atZone(zone)?.format(DateTimeFormatter.ofPattern("HH:mm"))
    }

@Composable
private fun EventRow(
    event: CalendarEvent,
    onMarkDone: (String) -> Unit,
    onDelete: (String) -> Unit,
    onEventSelected: (String) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.background)
            .clickable { onEventSelected(event.id) }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(42.dp)
                .clip(CircleShape)
                .background(
                    if (event.status == EventStatus.DONE) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        when (event.itemType) {
                            ScheduleItemType.EVENT -> MaterialTheme.colorScheme.primary
                            ScheduleItemType.REMINDER -> MaterialTheme.colorScheme.secondary
                            ScheduleItemType.TODO -> MaterialTheme.colorScheme.tertiary
                        }
                    },
                ),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                event.itemType.displayName(),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                event.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                event.listTimeLabel(zone),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        IconButton(onClick = { onMarkDone(event.id) }) {
            Icon(Icons.Rounded.Check, contentDescription = "完成")
        }
        IconButton(onClick = { onDelete(event.id) }) {
            Icon(Icons.Rounded.Delete, contentDescription = "删除")
        }
    }
}

@Composable
private fun VoiceInputFab(
    isListening: Boolean,
    isBusy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = when {
        isListening -> MaterialTheme.colorScheme.secondary
        isBusy -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    val contentColor = when {
        isListening -> MaterialTheme.colorScheme.onSecondary
        isBusy -> MaterialTheme.colorScheme.onTertiary
        else -> MaterialTheme.colorScheme.onPrimary
    }
    Surface(
        modifier = modifier
            .width(112.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .clickable(enabled = !isBusy, onClick = onClick)
            .testTag("voice-input-button"),
        shape = RoundedCornerShape(28.dp),
        color = containerColor,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.KeyboardVoice,
                contentDescription = null,
                tint = contentColor,
            )
            Text(
                when {
                    isListening -> stringResource(R.string.voice_fab_finish)
                    isBusy -> stringResource(R.string.voice_fab_organizing)
                    else -> stringResource(R.string.voice_fab_schedule)
                },
                color = contentColor,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun VoiceInputCurtain(
    state: CalendarUiState,
    onTextChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onVoiceInput: () -> Unit,
    onTextInput: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val taskState = state.voiceTaskState
    val transcript = state.partialTranscript.ifBlank { state.textInput }
    val listeningStartedAt = state.voiceSession.listeningStartedAt
    var clockMillis by remember(state.voiceSession.sessionId, listeningStartedAt) {
        mutableLongStateOf(System.currentTimeMillis())
    }
    LaunchedEffect(taskState, listeningStartedAt) {
        while (taskState == VoiceTaskState.Listening && listeningStartedAt != null) {
            clockMillis = System.currentTimeMillis()
            delay(250)
        }
    }
    val elapsedSeconds = listeningStartedAt
        ?.let { startedAt ->
            Duration.between(startedAt, Instant.ofEpochMilli(clockMillis))
                .seconds
                .coerceAtLeast(0)
        }
        ?: 0L
    val title = stringResource(
        when (taskState) {
            VoiceTaskState.Idle -> R.string.voice_title_idle
            VoiceTaskState.Listening -> R.string.voice_title_listening
            VoiceTaskState.Transcribing -> R.string.voice_title_transcribing
            VoiceTaskState.TranscriptReady -> R.string.voice_title_transcript_ready
            VoiceTaskState.Parsing -> R.string.voice_title_parsing
            VoiceTaskState.NeedsClarification -> R.string.voice_title_needs_clarification
            VoiceTaskState.Preview -> R.string.voice_title_preview
            VoiceTaskState.Saving -> R.string.voice_title_saving
            VoiceTaskState.Success -> R.string.voice_title_success
            VoiceTaskState.PartialSuccess -> R.string.voice_title_partial_success
            VoiceTaskState.Failure -> R.string.voice_title_failure
        },
    )
    val subtitle = stringResource(
        when (taskState) {
            VoiceTaskState.Idle -> R.string.voice_subtitle_idle
            VoiceTaskState.Listening -> R.string.voice_subtitle_listening
            VoiceTaskState.Transcribing -> R.string.voice_subtitle_transcribing
            VoiceTaskState.TranscriptReady -> R.string.voice_subtitle_transcript_ready
            VoiceTaskState.Parsing -> R.string.voice_subtitle_parsing
            VoiceTaskState.NeedsClarification -> R.string.voice_subtitle_needs_clarification
            VoiceTaskState.Preview -> R.string.voice_subtitle_preview
            VoiceTaskState.Saving -> R.string.voice_subtitle_saving
            VoiceTaskState.Success -> R.string.voice_subtitle_success
            VoiceTaskState.PartialSuccess -> R.string.voice_subtitle_partial_success
            VoiceTaskState.Failure -> R.string.voice_subtitle_failure
        },
    )
    val isBusy = taskState in setOf(
        VoiceTaskState.Transcribing,
        VoiceTaskState.Parsing,
        VoiceTaskState.Saving,
    )
    val transcriptEditable = taskState in setOf(
        VoiceTaskState.Idle,
        VoiceTaskState.TranscriptReady,
        VoiceTaskState.Failure,
    )
    val primaryLabel = when (taskState) {
        VoiceTaskState.Idle -> stringResource(
            if (transcript.isBlank()) {
                R.string.voice_action_start_speaking
            } else {
                R.string.voice_action_organize
            },
        )
        VoiceTaskState.Listening -> stringResource(R.string.voice_action_finish_recording)
        VoiceTaskState.Transcribing -> stringResource(R.string.voice_action_transcribing)
        VoiceTaskState.TranscriptReady -> stringResource(R.string.voice_action_organize)
        VoiceTaskState.Parsing -> stringResource(R.string.voice_action_understanding)
        VoiceTaskState.NeedsClarification -> stringResource(R.string.voice_action_back_to_preview)
        VoiceTaskState.Preview -> stringResource(R.string.voice_action_view_preview)
        VoiceTaskState.Saving -> stringResource(R.string.voice_action_creating)
        VoiceTaskState.Success -> stringResource(R.string.voice_action_done)
        VoiceTaskState.PartialSuccess -> stringResource(R.string.voice_action_view_result)
        VoiceTaskState.Failure -> stringResource(
            if (transcript.isBlank()) {
                R.string.voice_action_speak_again
            } else {
                R.string.voice_action_retry_organizing
            },
        )
    }
    val primaryEnabled = when (taskState) {
        VoiceTaskState.Transcribing,
        VoiceTaskState.Parsing,
        VoiceTaskState.Saving,
        -> false
        VoiceTaskState.TranscriptReady -> transcript.isNotBlank()
        else -> true
    }
    val disabledReason = when {
        taskState == VoiceTaskState.Transcribing ->
            stringResource(R.string.voice_disabled_transcribing)
        taskState == VoiceTaskState.Parsing ->
            stringResource(R.string.voice_disabled_parsing)
        taskState == VoiceTaskState.Saving ->
            stringResource(R.string.voice_disabled_saving)
        taskState == VoiceTaskState.TranscriptReady && transcript.isBlank() ->
            stringResource(R.string.voice_disabled_empty_transcript)
        else -> null
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 20.dp)
            .testTag("voice-input-curtain"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("voice-input-curtain-close-button"),
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.common_close),
                )
            }
        }

        if (taskState == VoiceTaskState.Listening) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ListeningWaveform(elapsedSeconds)
                Text(
                    text = "%02d:%02d".format(elapsedSeconds / 60, elapsedSeconds % 60),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        if (isBusy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 1.dp,
        ) {
            OutlinedTextField(
                value = transcript,
                onValueChange = onTextChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 104.dp, max = 168.dp)
                    .padding(10.dp)
                    .testTag("transcript-field"),
                shape = RoundedCornerShape(8.dp),
                label = {
                    Text(
                        when (taskState) {
                            VoiceTaskState.Listening ->
                                stringResource(R.string.voice_transcript_live_label)
                            VoiceTaskState.Transcribing ->
                                stringResource(R.string.voice_transcript_label)
                            else -> stringResource(R.string.voice_text_input_label)
                        },
                    )
                },
                placeholder = { Text(stringResource(R.string.voice_text_input_placeholder)) },
                minLines = 3,
                maxLines = 6,
                readOnly = !transcriptEditable,
            )
        }

        SheetMessages(
            errors = (listOfNotNull(state.errorMessage) + state.voiceSession.errors).distinct(),
            warnings = state.warnings.distinct(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledIconButton(
                onClick = onTextInput,
                enabled = !isBusy,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("curtain-text-input-button"),
            ) {
                Icon(
                    Icons.Rounded.Edit,
                    contentDescription = stringResource(R.string.voice_switch_to_text_description),
                )
            }
            FilledIconButton(
                onClick = onVoiceInput,
                enabled = !isBusy,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("curtain-voice-toggle-button"),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (state.isListening) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    contentColor = if (state.isListening) {
                        MaterialTheme.colorScheme.onSecondary
                    } else {
                        MaterialTheme.colorScheme.onPrimary
                    },
                ),
            ) {
                Icon(
                    Icons.Rounded.KeyboardVoice,
                    contentDescription = stringResource(
                        if (state.isListening) {
                            R.string.voice_stop_recording_description
                        } else {
                            R.string.voice_continue_description
                        },
                    ),
                )
            }
            Button(
                onClick = {
                    when (taskState) {
                        VoiceTaskState.Idle ->
                            if (transcript.isBlank()) onVoiceInput() else onSubmit()
                        VoiceTaskState.Listening -> onVoiceInput()
                        VoiceTaskState.TranscriptReady -> onSubmit()
                        VoiceTaskState.NeedsClarification,
                        VoiceTaskState.Preview,
                        VoiceTaskState.Success,
                        VoiceTaskState.PartialSuccess,
                        -> onDismiss()
                        VoiceTaskState.Failure ->
                            if (transcript.isBlank()) onVoiceInput() else onSubmit()
                        VoiceTaskState.Transcribing,
                        VoiceTaskState.Parsing,
                        VoiceTaskState.Saving,
                        -> Unit
                    }
                },
                enabled = primaryEnabled,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("generate-button"),
                shape = RoundedCornerShape(8.dp),
            ) {
                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(primaryLabel)
            }
        }
        disabledReason?.let { reason ->
            Text(
                text = reason,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.workflowError?.shouldOpenSettings == true) {
            TextButton(
                onClick = onOpenSettings,
                modifier = Modifier.testTag("voice-error-open-settings-button"),
            ) {
                Text(stringResource(R.string.common_open_settings))
            }
        }
    }
}

@Composable
private fun ListeningWaveform(elapsedSeconds: Long) {
    Row(
        modifier = Modifier.height(28.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(7) { index ->
            val phase = ((elapsedSeconds + index) % 5).toInt()
            val barHeight = when (phase) {
                0 -> 8.dp
                1, 4 -> 14.dp
                2 -> 24.dp
                else -> 18.dp
            }
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(barHeight)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSecondaryContainer),
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            stringResource(R.string.voice_listening_label),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun SheetMessages(
    errors: List<String>,
    warnings: List<String>,
) {
    errors.filter(String::isNotBlank).distinct().forEach { message ->
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Assertive },
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.errorContainer,
        ) {
            Text(
                text = message,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    warnings.filter(String::isNotBlank).distinct().forEach { message ->
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite },
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer,
        ) {
            Text(
                text = message,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun RecognitionHistorySheet(
    history: List<ParseHistoryItem>,
    onEditTranscript: (String) -> Unit,
    onReparse: (String) -> Unit,
    onDelete: (String) -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
) {
    var expandedHistoryId by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val unnamedItemLabel = stringResource(R.string.history_unnamed_item)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.history_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    stringResource(R.string.history_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.history_close_description),
                )
            }
        }
        if (history.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(
                    stringResource(R.string.history_empty),
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(history, key = { it.id }) { item ->
                    val expanded = expandedHistoryId == item.id
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("parse-history-row"),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 1.dp,
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                item.transcript,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = if (expanded) Int.MAX_VALUE else 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${item.metaLabel()} · ${item.creationStatus.displayName(item.isUndone)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (expanded) {
                                item.itemSummaries.forEach { scheduleItem ->
                                    val itemTitle = scheduleItem.title.ifBlank {
                                        unnamedItemLabel
                                    }
                                    val timeSuffix = if (scheduleItem.timeSummary == null) {
                                        ""
                                    } else {
                                        stringResource(
                                            R.string.history_item_time_suffix,
                                            scheduleItem.timeSummary,
                                        )
                                    }
                                    Text(
                                        stringResource(
                                            R.string.history_item_summary,
                                            scheduleItem.itemType.historyDisplayName(),
                                            itemTitle,
                                            timeSuffix,
                                        ),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                                item.failureReason?.let { reason ->
                                    Text(
                                        stringResource(R.string.history_failure_reason, reason),
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                TextButton(onClick = { onEditTranscript(item.transcript) }) {
                                    Text(stringResource(R.string.history_edit_transcript))
                                }
                                TextButton(onClick = { onReparse(item.transcript) }) {
                                    Text(stringResource(R.string.history_reparse))
                                }
                                TextButton(
                                    onClick = {
                                        expandedHistoryId = if (expanded) null else item.id
                                    },
                                ) {
                                    Text(
                                        stringResource(
                                            if (expanded) {
                                                R.string.history_collapse_result
                                            } else {
                                                R.string.history_expand_result
                                            },
                                        ),
                                    )
                                }
                                IconButton(onClick = { onDelete(item.id) }) {
                                    Icon(
                                        Icons.Rounded.Delete,
                                        contentDescription = stringResource(
                                            R.string.history_delete_description,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (confirmClear) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            stringResource(R.string.history_clear_confirmation),
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        TextButton(onClick = { confirmClear = false }) {
                            Text(stringResource(R.string.common_cancel))
                        }
                        TextButton(
                            onClick = {
                                onClear()
                                confirmClear = false
                            },
                        ) {
                            Text(stringResource(R.string.common_clear))
                        }
                    }
                }
            } else {
                TextButton(onClick = { confirmClear = true }) {
                    Text(stringResource(R.string.history_clear_all))
                }
            }
        }
    }
}

@Composable
private fun ParseHistoryCreationStatus.displayName(isUndone: Boolean): String {
    if (isUndone) return stringResource(R.string.history_status_undone)
    return stringResource(
        when (this) {
            ParseHistoryCreationStatus.NOT_ATTEMPTED -> R.string.history_status_not_attempted
            ParseHistoryCreationStatus.SUCCESS -> R.string.history_status_success
            ParseHistoryCreationStatus.FAILURE -> R.string.history_status_failure
            ParseHistoryCreationStatus.PARTIAL_SUCCESS -> R.string.history_status_partial_success
        },
    )
}

@Composable
private fun ParseHistoryItem.metaLabel(): String {
    val time = java.time.Instant.ofEpochMilli(createdAtEpochMillis)
        .atZone(ZoneId.systemDefault())
        .format(
            DateTimeFormatter.ofPattern(
                stringResource(R.string.history_date_pattern),
                Locale.SIMPLIFIED_CHINESE,
            ),
        )
    val count = if (draftCount > 0) {
        pluralStringResource(R.plurals.history_item_count, draftCount, draftCount)
    } else {
        stringResource(R.string.history_no_draft)
    }
    return stringResource(R.string.history_meta_format, time, count)
}

@Composable
private fun ScheduleItemType.historyDisplayName(): String =
    stringResource(
        when (this) {
            ScheduleItemType.EVENT -> R.string.schedule_type_event
            ScheduleItemType.REMINDER -> R.string.schedule_type_reminder
            ScheduleItemType.TODO -> R.string.schedule_type_todo
        },
    )

@Composable
private fun WarningsAndErrors(state: CalendarUiState) {
    AnimatedVisibility(state.errorMessage != null || state.warnings.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            state.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            state.warnings.forEach {
                Text(it, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun DraftReturnBar(
    drafts: List<DraftEditor>,
    visible: Boolean,
    onOpen: () -> Unit,
) {
    val unnamedDraftLabel = stringResource(R.string.draft_return_unnamed)
    AnimatedVisibility(visible) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("draft-return-bar")
                .clickable(onClick = onOpen),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        pluralStringResource(
                            R.plurals.draft_pending_count,
                            drafts.size,
                            drafts.size,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        drafts.joinToString("  ") {
                            it.title.ifBlank {
                                unnamedDraftLabel
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TextButton(onClick = onOpen) {
                    Text(stringResource(R.string.draft_return_continue))
                }
            }
        }
    }
}

@Composable
private fun CreationResultSheet(
    summary: CreationBatchSummary,
    onViewSchedule: () -> Unit,
    onRetryFailed: () -> Unit,
    onUndo: () -> Unit,
    onOpenSettings: () -> Unit,
    onClose: () -> Unit,
) {
    val unnamedItemLabel = stringResource(R.string.history_unnamed_item)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val resultTitle = when {
                    summary.createdCount == 0 -> stringResource(R.string.creation_result_incomplete)
                    summary.hasFailures -> pluralStringResource(
                        R.plurals.creation_result_with_failures,
                        summary.createdCount,
                        summary.createdCount,
                        summary.failedCount,
                    )
                    summary.hasWarnings -> pluralStringResource(
                        R.plurals.creation_result_with_warnings,
                        summary.createdCount,
                        summary.createdCount,
                    )
                    else -> pluralStringResource(
                        R.plurals.creation_result_success,
                        summary.createdCount,
                        summary.createdCount,
                    )
                }
                Text(
                    resultTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(R.string.creation_result_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.creation_result_close_description),
                )
            }
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(summary.outcomes, key = { it.draftId }) { outcome ->
                val isFailure = outcome.status == CreationItemStatus.FAILED
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = if (isFailure) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            if (isFailure) Icons.Rounded.Close else Icons.Rounded.Check,
                            contentDescription = null,
                            tint = if (isFailure) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            },
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                outcome.title.ifBlank {
                                    unnamedItemLabel
                                },
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                when (outcome.status) {
                                    CreationItemStatus.CREATED -> pluralStringResource(
                                        R.plurals.creation_outcome_created,
                                        outcome.createdCount,
                                        outcome.createdCount,
                                    )
                                    CreationItemStatus.CREATED_WITH_WARNING -> pluralStringResource(
                                        R.plurals.creation_outcome_created_with_warning,
                                        outcome.createdCount,
                                        outcome.createdCount,
                                    )
                                    CreationItemStatus.FAILED ->
                                        stringResource(R.string.creation_outcome_failed)
                                },
                                style = MaterialTheme.typography.bodySmall,
                            )
                            outcome.message?.let { message ->
                                Text(
                                    message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isFailure) {
                                        MaterialTheme.colorScheme.onErrorContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (summary.hasFailures) {
                    TextButton(
                        onClick = onRetryFailed,
                        modifier = Modifier.testTag("creation-retry-failed-button"),
                    ) {
                        Text(stringResource(R.string.creation_result_retry_failed))
                    }
                }
                if (summary.canUndo) {
                    TextButton(
                        onClick = onUndo,
                        modifier = Modifier.testTag("creation-undo-button"),
                    ) {
                        Text(stringResource(R.string.creation_result_undo))
                    }
                }
                if (summary.hasWarnings) {
                    TextButton(onClick = onOpenSettings) {
                        Text(stringResource(R.string.common_open_settings))
                    }
                }
            }
            Button(
                onClick = onViewSchedule,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("creation-view-schedule-button"),
            ) {
                Text(
                    stringResource(
                        if (summary.createdCount > 0) {
                            R.string.creation_result_view_schedule
                        } else {
                            R.string.creation_result_back_to_preview
                        },
                    ),
                )
            }
        }
    }
}

@Composable
private fun DraftPanel(
    state: CalendarUiState,
    onUpdate: (String, (DraftEditor) -> DraftEditor) -> Unit,
    onConfirmAll: () -> Unit,
    onConfirm: (String) -> Unit,
    onAcceptRisk: (String) -> Unit,
    onDiscard: (String) -> Unit,
    onClear: () -> Unit,
) {
    val drafts = state.draftEditors
    val zoneId = ZoneId.systemDefault()
    val blockingIssues = drafts
        .flatMap { draft -> draft.blockingIssueLabels(zoneId) }
        .distinct()
    val canCreateAll = drafts.isNotEmpty() &&
        drafts.all { draft -> state.canCreateDraft(draft, zoneId) } &&
        !state.isSaving
    AnimatedVisibility(drafts.isNotEmpty()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("draft-panel"),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            pluralStringResource(
                                R.plurals.draft_prepared_count,
                                drafts.size,
                                drafts.size,
                            ),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(R.string.draft_preview_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onClear, enabled = !state.isSaving) {
                            Text(stringResource(R.string.common_clear))
                        }
                        Spacer(Modifier.width(4.dp))
                        Button(
                            onClick = onConfirmAll,
                            enabled = canCreateAll,
                            modifier = Modifier.testTag("draft-save-all-button"),
                        ) {
                            Icon(Icons.Rounded.Save, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (state.isSaving) {
                                    stringResource(R.string.voice_action_creating)
                                } else {
                                    pluralStringResource(
                                        R.plurals.draft_create_count,
                                        drafts.size,
                                        drafts.size,
                                    )
                                },
                            )
                        }
                    }
                }
                SheetMessages(
                    errors = (listOfNotNull(state.errorMessage) + state.voiceSession.errors).distinct(),
                    warnings = state.warnings.distinct(),
                )
                if (blockingIssues.isNotEmpty()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { liveRegion = LiveRegionMode.Polite },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Text(
                            text = stringResource(
                                R.string.draft_preview_blocking_issues,
                                blockingIssues.joinToString("、"),
                            ),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Notifications, contentDescription = null)
                    Text(
                        stringResource(R.string.draft_preview_notification_permission),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(drafts, key = { it.draftId }) { draft ->
                        DraftEditorCard(
                            draft = draft,
                            onUpdate = { transform -> onUpdate(draft.draftId, transform) },
                            onConfirm = { onConfirm(draft.draftId) },
                            onAcceptRisk = { onAcceptRisk(draft.draftId) },
                            onDiscard = { onDiscard(draft.draftId) },
                            isSaving = state.isSaving,
                            zoneId = zoneId,
                            riskMessages = state.draftRiskMessages[draft.draftId].orEmpty(),
                            riskAccepted = draft.draftId in state.acceptedRiskDraftIds,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DraftEditorCard(
    draft: DraftEditor,
    onUpdate: ((DraftEditor) -> DraftEditor) -> Unit,
    onConfirm: () -> Unit,
    onAcceptRisk: () -> Unit,
    onDiscard: () -> Unit,
    isSaving: Boolean,
    zoneId: ZoneId,
    riskMessages: List<String>,
    riskAccepted: Boolean,
) {
    val issues = draft.blockingIssueLabels(zoneId)
    val canCreate = draft.canCreate(zoneId) &&
        (riskMessages.isEmpty() || riskAccepted) &&
        !isSaving
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("draft-item"),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    if (issues.isEmpty()) Icons.Rounded.RadioButtonUnchecked else Icons.Rounded.Close,
                    contentDescription = null,
                    tint = if (issues.isEmpty()) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        draft.itemType.displayName(),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        draft.title.ifBlank { "未命名项目" },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        draft.timeSummary(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    "${(draft.aiConfidence * 100).toInt()}%",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Text(
                "提醒：${draft.reminderSummary()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "重复：${draft.repeatSummary().ifBlank { "不重复" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "地点：${draft.location.ifBlank { "未填写" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "备注：${draft.notes.ifBlank { "未填写" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOf(
                    if (draft.allDay) "全天" else "非全天",
                    "时区 ${draft.timeZoneId}",
                    if (draft.itemType == ScheduleItemType.TODO) {
                        "优先级 ${draft.priority.displayName()}"
                    } else {
                        ""
                    },
                ).filter(String::isNotBlank).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (issues.isEmpty()) {
                    "置信度 ${(draft.aiConfidence * 100).toInt()}% · 关键信息已确认"
                } else {
                    "置信度 ${(draft.aiConfidence * 100).toInt()}% · 待确认 ${issues.joinToString("、")}"
                },
                modifier = if (issues.isEmpty()) {
                    Modifier
                } else {
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                },
                color = if (issues.isEmpty()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
                style = MaterialTheme.typography.bodySmall,
            )
            if (issues.isNotEmpty() &&
                (draft.ambiguousFields.isNotEmpty() || "clarification" in draft.missingFields)
            ) {
                TextButton(
                    onClick = {
                        onUpdate {
                            it.copy(
                                missingFields = emptyList(),
                                ambiguousFields = emptyList(),
                            )
                        }
                    },
                    enabled = !isSaving,
                    modifier = Modifier.testTag("draft-resolve-ambiguity-button"),
                ) {
                    Text(stringResource(R.string.draft_resolve_ambiguity))
                }
            }
            if (riskMessages.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            stringResource(R.string.draft_risk_title),
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        riskMessages.forEach { message ->
                            Text(
                                message,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        if (riskAccepted) {
                            Text(
                                stringResource(R.string.draft_risk_accepted),
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        } else {
                            TextButton(
                                onClick = onAcceptRisk,
                                enabled = !isSaving,
                                modifier = Modifier.testTag("draft-accept-risk-button"),
                            ) {
                                Text(stringResource(R.string.draft_risk_accept))
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { onUpdate { it.copy(expanded = !it.expanded) } },
                    enabled = !isSaving,
                    modifier = Modifier.testTag("draft-edit-button"),
                ) {
                    Icon(
                        if (draft.expanded) Icons.Rounded.ExpandLess else Icons.Rounded.Edit,
                        contentDescription = if (draft.expanded) "收起编辑" else "编辑",
                    )
                }
                IconButton(onClick = onDiscard, enabled = !isSaving) {
                    Icon(Icons.Rounded.Delete, contentDescription = "删除草稿")
                }
                FilledIconButton(
                    onClick = onConfirm,
                    enabled = canCreate,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("draft-save-button"),
                ) {
                    Icon(Icons.Rounded.Save, contentDescription = "保存")
                }
            }

            AnimatedVisibility(draft.expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("项目类型", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ScheduleItemType.entries) { type ->
                            FilterChip(
                                selected = draft.itemType == type,
                                onClick = { onUpdate { it.withItemType(type) } },
                                enabled = !isSaving,
                                label = { Text(type.displayName()) },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = draft.title,
                        onValueChange = { value ->
                            onUpdate { it.copy(title = value).resolveFields("title") }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        label = { Text("标题") },
                        singleLine = true,
                        enabled = !isSaving,
                    )
                    when (draft.itemType) {
                        ScheduleItemType.EVENT -> {
                            FilterChip(
                                selected = draft.allDay,
                                onClick = {
                                    onUpdate {
                                        it.copy(allDay = !it.allDay)
                                            .resolveFields("all_day", "allDay")
                                    }
                                },
                                enabled = !isSaving,
                                label = { Text("全天事件") },
                            )
                            OutlinedTextField(
                                value = draft.startLocal,
                                onValueChange = { value ->
                                    onUpdate {
                                        it.copy(startLocal = value)
                                            .resolveFields("start", "start_local", "startAt")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                label = {
                                    Text(if (draft.allDay) "日期 yyyy-MM-dd" else "开始 yyyy-MM-dd HH:mm")
                                },
                                singleLine = true,
                                enabled = !isSaving,
                            )
                            OutlinedTextField(
                                value = draft.endLocal,
                                onValueChange = { value ->
                                    onUpdate {
                                        it.copy(endLocal = value)
                                            .resolveFields("end", "end_local", "endAt")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                label = { Text(if (draft.allDay) "结束日期" else "结束时间") },
                                singleLine = true,
                                enabled = !isSaving,
                            )
                        }
                        ScheduleItemType.REMINDER -> {
                            OutlinedTextField(
                                value = draft.triggerLocal,
                                onValueChange = { value ->
                                    onUpdate {
                                        it.copy(triggerLocal = value)
                                            .resolveFields("trigger", "trigger_local", "triggerAt")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                label = { Text("提醒时间 yyyy-MM-dd HH:mm") },
                                singleLine = true,
                                enabled = !isSaving,
                            )
                        }
                        ScheduleItemType.TODO -> {
                            FilterChip(
                                selected = draft.allDay,
                                onClick = {
                                    onUpdate {
                                        it.copy(allDay = !it.allDay)
                                            .resolveFields("all_day", "allDay")
                                    }
                                },
                                enabled = !isSaving,
                                label = { Text("仅设置截止日期") },
                            )
                            OutlinedTextField(
                                value = draft.dueLocal,
                                onValueChange = { value ->
                                    onUpdate {
                                        it.copy(dueLocal = value)
                                            .resolveFields("due", "due_local", "dueAt")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                label = {
                                    Text(
                                        if (draft.allDay) {
                                            "截止日期（可选）yyyy-MM-dd"
                                        } else {
                                            "截止时间（可选）yyyy-MM-dd HH:mm"
                                        },
                                    )
                                },
                                singleLine = true,
                                enabled = !isSaving,
                            )
                            Text("优先级", style = MaterialTheme.typography.labelLarge)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(ScheduleItemPriority.entries) { priority ->
                                    FilterChip(
                                        selected = draft.priority == priority,
                                        onClick = {
                                            onUpdate {
                                                it.copy(priority = priority)
                                                    .resolveFields("priority")
                                            }
                                        },
                                        enabled = !isSaving,
                                        label = { Text(priority.displayName()) },
                                    )
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = draft.reminderMinutes,
                        onValueChange = { value ->
                            onUpdate {
                                it.copy(reminderMinutes = value)
                                    .resolveFields("reminder", "reminders", "reminder_minutes")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        label = { Text("提醒分钟，逗号分隔") },
                        singleLine = true,
                        enabled = !isSaving,
                    )
                    OutlinedTextField(
                        value = draft.location,
                        onValueChange = { value ->
                            onUpdate { it.copy(location = value).resolveFields("location") }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        label = { Text("地点") },
                        singleLine = true,
                        enabled = !isSaving,
                    )
                    OutlinedTextField(
                        value = draft.notes,
                        onValueChange = { value ->
                            onUpdate { it.copy(notes = value).resolveFields("notes") }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        label = { Text("备注") },
                        minLines = 2,
                        maxLines = 4,
                        enabled = !isSaving,
                    )
                    OutlinedTextField(
                        value = draft.timeZoneId,
                        onValueChange = { value ->
                            onUpdate {
                                it.copy(timeZoneId = value)
                                    .resolveFields("time_zone", "timezone", "timeZoneId")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        label = { Text("时区（例如 Asia/Shanghai）") },
                        singleLine = true,
                        enabled = !isSaving,
                    )
                    OutlinedTextField(
                        value = draft.recurrenceRule,
                        onValueChange = { value ->
                            onUpdate {
                                it.copy(
                                    recurrenceRule = value,
                                    repeatFrequency = null,
                                    repeatCount = "",
                                )
                                    .resolveFields("recurrence_rule", "recurrenceRule")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        label = { Text("重复规则（RRULE，可留空）") },
                        placeholder = { Text("FREQ=WEEKLY;COUNT=8") },
                        singleLine = true,
                        enabled = !isSaving,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = draft.repeatFrequency == null && draft.recurrenceRule.isBlank(),
                            onClick = {
                                onUpdate {
                                    it.copy(
                                        recurrenceRule = "",
                                        repeatFrequency = null,
                                        repeatCount = "",
                                    )
                                }
                            },
                            enabled = !isSaving,
                            label = { Text("不重复") },
                        )
                        RepeatFrequency.entries.forEach { frequency ->
                            FilterChip(
                                selected = draft.repeatFrequency == frequency,
                                onClick = {
                                    onUpdate {
                                        it.copy(
                                            recurrenceRule = "",
                                            repeatFrequency = frequency,
                                            repeatCount = it.repeatCount.ifBlank {
                                                when (frequency) {
                                                    RepeatFrequency.DAILY -> "7"
                                                    RepeatFrequency.WEEKLY -> "8"
                                                }
                                            },
                                        )
                                    }
                                },
                                enabled = !isSaving,
                                label = { Text(frequency.label) },
                            )
                        }
                    }
                    if (draft.repeatFrequency != null) {
                        OutlinedTextField(
                            value = draft.repeatCount,
                            onValueChange = { value -> onUpdate { it.copy(repeatCount = value.filter(Char::isDigit)) } },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            label = { Text("重复次数") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            enabled = !isSaving,
                        )
                    }
                }
            }
        }
    }
}

private fun DraftEditor.timeSummary(): String =
    when (itemType) {
        ScheduleItemType.EVENT -> when {
            startLocal.isBlank() -> "缺少开始时间"
            endLocal.isBlank() -> "开始 ${startLocal.replace("T", " ")}"
            else -> "${startLocal.replace("T", " ")} - ${endLocal.replace("T", " ")}"
        }
        ScheduleItemType.REMINDER ->
            triggerLocal.takeIf(String::isNotBlank)
                ?.let { "提醒 ${it.replace("T", " ")}" }
                ?: "缺少提醒时间"
        ScheduleItemType.TODO ->
            dueLocal.takeIf(String::isNotBlank)
                ?.let { "截止 ${it.replace("T", " ")}" }
                ?: "无截止时间"
    }

private fun DraftEditor.reminderSummary(): String {
    val minutes = reminderMinutes.split(",")
        .mapNotNull { it.trim().toIntOrNull() }
        .filter { it >= 0 }
        .ifEmpty { listOf(10) }
        .plus(0)
        .distinct()
        .sorted()
    return minutes.joinToString(" / ") { minute ->
        if (minute == 0) "到点" else "提前${minute}分钟"
    }
}

private fun DraftEditor.repeatSummary(): String =
    repeatFrequency?.let { frequency ->
        val count = repeatCount.toIntOrNull()?.coerceIn(2, 60) ?: when (frequency) {
            RepeatFrequency.DAILY -> 7
            RepeatFrequency.WEEKLY -> 8
        }
        "${frequency.label}，共 $count 次"
    } ?: recurrenceRule

private fun ScheduleItemType.displayName(): String =
    when (this) {
        ScheduleItemType.EVENT -> "事件"
        ScheduleItemType.REMINDER -> "提醒"
        ScheduleItemType.TODO -> "待办"
    }

private fun CalendarEvent.listTimeLabel(zoneId: ZoneId): String {
    val formatter = DateTimeFormatter.ofPattern("M月d日 HH:mm", Locale.SIMPLIFIED_CHINESE)
    return when (itemType) {
        ScheduleItemType.EVENT -> startAt.atZone(zoneId).format(formatter)
        ScheduleItemType.REMINDER ->
            (triggerAt ?: startAt).atZone(zoneId).format(formatter)
        ScheduleItemType.TODO ->
            dueAt?.atZone(zoneId)?.format(formatter)?.let { "截止 $it" } ?: "无截止时间"
    }
}

private fun ScheduleItemPriority.displayName(): String =
    when (this) {
        ScheduleItemPriority.LOW -> "低"
        ScheduleItemPriority.NORMAL -> "普通"
        ScheduleItemPriority.HIGH -> "高"
        ScheduleItemPriority.URGENT -> "紧急"
    }

@Composable
private fun EventEditorSheet(
    editor: EventEditor,
    onUpdate: ((EventEditor) -> EventEditor) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
            .verticalScroll(rememberScrollState())
            .testTag("event-editor-sheet"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("编辑事件", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = editor.title,
            onValueChange = { value -> onUpdate { it.copy(title = value) } },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            label = { Text("标题") },
            singleLine = true,
        )
        OutlinedTextField(
            value = editor.startLocal,
            onValueChange = { value -> onUpdate { it.copy(startLocal = value) } },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            label = { Text(if (editor.allDay) "日期 yyyy-MM-dd" else "开始 yyyy-MM-dd HH:mm") },
            singleLine = true,
        )
        OutlinedTextField(
            value = editor.endLocal,
            onValueChange = { value -> onUpdate { it.copy(endLocal = value) } },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            label = { Text(if (editor.allDay) "结束日期" else "结束时间") },
            singleLine = true,
        )
        OutlinedTextField(
            value = editor.reminderMinutes,
            onValueChange = { value -> onUpdate { it.copy(reminderMinutes = value) } },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            label = { Text("提醒分钟，逗号分隔") },
            singleLine = true,
        )
        OutlinedTextField(
            value = editor.notes,
            onValueChange = { value -> onUpdate { it.copy(notes = value) } },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            label = { Text("备注") },
            minLines = 2,
            maxLines = 4,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onClose) { Text("关闭") }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onSave,
                enabled = editor.title.isNotBlank() && editor.startLocal.isNotBlank(),
                modifier = Modifier.testTag("event-editor-save-button"),
            ) {
                Icon(Icons.Rounded.Save, contentDescription = null)
                Text("保存")
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun CommercialSettingsContent(
    state: CalendarUiState,
    onApiKeyChanged: (String) -> Unit,
    onDefaultReminderChanged: (String) -> Unit,
    onDefaultDurationChanged: (String) -> Unit,
    onExportJson: () -> Unit,
    onImportJson: () -> Unit,
    onExportIcs: () -> Unit,
    onPrivacy: () -> Unit,
    onReset: () -> Unit,
    onDismissReset: () -> Unit,
    onConfirmReset: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onRefreshPermissions: () -> Unit,
    onTestConnection: () -> Unit,
    onSave: () -> Unit,
    onClearApiKey: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var revealApiKey by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .testTag("settings-commercial-sheet"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall)
        SettingsSection(title = stringResource(R.string.settings_section_ai_service)) {
            Text(
                stringResource(R.string.settings_ai_service_name),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                if (state.apiKeySaved) {
                    stringResource(R.string.settings_api_key_saved)
                } else {
                    stringResource(R.string.settings_api_key_needed)
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = state.apiKeyInput,
                onValueChange = onApiKeyChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("api-key-field"),
                label = { Text(stringResource(R.string.settings_api_key_label)) },
                placeholder = {
                    if (state.apiKeySaved && state.apiKeyInput.isBlank()) {
                        Text(stringResource(R.string.settings_api_key_placeholder))
                    }
                },
                singleLine = true,
                visualTransformation = if (revealApiKey) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = { revealApiKey = !revealApiKey }) {
                        Icon(
                            if (revealApiKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = stringResource(
                                if (revealApiKey) {
                                    R.string.settings_api_key_hide_description
                                } else {
                                    R.string.settings_api_key_show_description
                                },
                            ),
                        )
                    }
                },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextButton(
                    onClick = {
                        val clipboard = context.getSystemService(ClipboardManager::class.java)
                        clipboard?.primaryClip
                            ?.takeIf { it.itemCount > 0 }
                            ?.getItemAt(0)
                            ?.coerceToText(context)
                            ?.toString()
                            ?.let(onApiKeyChanged)
                    },
                ) {
                    Text(stringResource(R.string.settings_action_paste))
                }
                TextButton(
                    onClick = onTestConnection,
                    enabled = state.aiConnectionState != AiConnectionState.CHECKING,
                    modifier = Modifier.testTag("settings-test-ai-button"),
                ) {
                    Text(
                        stringResource(
                            if (state.aiConnectionState == AiConnectionState.CHECKING) {
                                R.string.settings_action_testing
                            } else {
                                R.string.settings_action_test_connection
                            },
                        ),
                    )
                }
                if (state.apiKeySaved) {
                    TextButton(
                        onClick = onClearApiKey,
                        modifier = Modifier.testTag("settings-clear-key-button"),
                    ) {
                        Text(stringResource(R.string.settings_action_clear_key))
                    }
                }
            }
            Text(
                stringResource(
                    R.string.settings_connection_status,
                    state.aiConnectionState.localizedLabel(),
                ),
                color = when (state.aiConnectionState) {
                    AiConnectionState.CONNECTED -> MaterialTheme.colorScheme.primary
                    AiConnectionState.FAILED -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            state.aiConnectionMessage?.let { message ->
                Text(
                    message,
                    color = if (state.aiConnectionState == AiConnectionState.FAILED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        SettingsSection(title = stringResource(R.string.settings_section_defaults)) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = state.defaultDurationMinutesInput,
                    onValueChange = onDefaultDurationChanged,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("default-duration-field"),
                    label = { Text(stringResource(R.string.settings_default_duration)) },
                    suffix = { Text(stringResource(R.string.settings_minutes)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = state.defaultReminderMinutesInput,
                    onValueChange = onDefaultReminderChanged,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("default-reminder-field"),
                    label = { Text(stringResource(R.string.settings_default_reminder)) },
                    suffix = { Text(stringResource(R.string.settings_minutes)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Text(
                    stringResource(R.string.settings_default_calendar),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(
                        R.string.settings_default_timezone,
                        ZoneId.systemDefault().id,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SettingsSection(title = stringResource(R.string.settings_section_permissions)) {
            PermissionRow(
                title = stringResource(R.string.settings_permission_microphone),
                status = state.microphonePermission,
                action = stringResource(R.string.common_open_settings),
                onAction = onOpenAppSettings,
            )
            PermissionRow(
                title = stringResource(R.string.settings_permission_notifications),
                status = state.notificationPermission,
                action = stringResource(R.string.settings_action_notification_settings),
                onAction = onOpenNotificationSettings,
            )
            PermissionRow(
                title = stringResource(R.string.settings_permission_exact_alarm),
                status = state.exactAlarmPermission,
                action = stringResource(R.string.settings_action_reminder_settings),
                onAction = onOpenExactAlarmSettings,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.settings_calendar_write),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        stringResource(R.string.settings_calendar_write_description),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    stringResource(R.string.settings_available),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            TextButton(onClick = onRefreshPermissions) {
                Icon(Icons.Rounded.Today, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.settings_action_refresh_status))
            }
        }

        SettingsSection(title = stringResource(R.string.settings_section_data)) {
            SettingsActionRow(
                title = stringResource(R.string.settings_backup_title),
                description = stringResource(R.string.settings_backup_description),
                enabled = state.events.isNotEmpty(),
                testTag = "settings-export-json-button",
                onClick = onExportJson,
            )
            SettingsActionRow(
                title = stringResource(R.string.settings_import_title),
                description = stringResource(R.string.settings_import_description),
                testTag = "settings-import-json-button",
                onClick = onImportJson,
            )
            SettingsActionRow(
                title = stringResource(R.string.settings_export_ics_title),
                description = stringResource(R.string.settings_export_ics_description),
                enabled = state.events.isNotEmpty(),
                testTag = "settings-export-ics-button",
                onClick = onExportIcs,
            )
            state.backupMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SettingsActionRow(
                title = stringResource(R.string.settings_privacy_title),
                description = stringResource(R.string.settings_privacy_description),
                testTag = "settings-privacy-button",
                onClick = onPrivacy,
            )
        }

        SettingsSection(title = stringResource(R.string.settings_section_danger)) {
            if (state.showResetConfirmation) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            stringResource(R.string.settings_reset_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(R.string.settings_reset_description),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(onClick = onDismissReset) {
                                Text(stringResource(R.string.common_cancel))
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = onConfirmReset,
                                modifier = Modifier.testTag("settings-confirm-reset-button"),
                            ) {
                                Text(stringResource(R.string.settings_reset_confirm))
                            }
                        }
                    }
                }
            } else {
                TextButton(
                    onClick = onReset,
                    modifier = Modifier.testTag("settings-reset-button"),
                ) {
                    Icon(Icons.Rounded.Delete, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.settings_reset_action))
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onClose,
                modifier = Modifier.testTag("settings-cancel-button"),
            ) {
                Text(stringResource(R.string.common_cancel))
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onSave,
                modifier = Modifier.testTag("settings-save-button"),
            ) { Text(stringResource(R.string.common_save)) }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun SettingsActionRow(
    title: String,
    description: String,
    testTag: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onBackground
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AiConnectionState.localizedLabel(): String =
    stringResource(
        when (this) {
            AiConnectionState.UNVERIFIED -> R.string.settings_ai_state_unverified
            AiConnectionState.CHECKING -> R.string.settings_ai_state_checking
            AiConnectionState.CONNECTED -> R.string.settings_ai_state_connected
            AiConnectionState.FAILED -> R.string.settings_ai_state_failed
        },
    )

@Composable
private fun PermissionHealth.localizedLabel(): String =
    stringResource(
        when (this) {
            PermissionHealth.READY -> R.string.settings_permission_ready
            PermissionHealth.NEEDS_ACTION -> R.string.settings_permission_needs_action
            PermissionHealth.UNAVAILABLE -> R.string.settings_permission_unavailable
        },
    )

@Composable
private fun PermissionRow(
    title: String,
    status: PermissionHealth,
    action: String,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        if (status == PermissionHealth.READY) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.secondary
                        },
                    ),
            )
            Column {
                Text(title, style = MaterialTheme.typography.bodyMedium)
                Text(
                    status.localizedLabel(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        TextButton(
            onClick = onAction,
        ) { Text(action) }
    }
}

@Composable
private fun SettingsSheet(
    state: CalendarUiState,
    onApiKeyChanged: (String) -> Unit,
    onDefaultReminderChanged: (String) -> Unit,
    onDefaultDurationChanged: (String) -> Unit,
    onExportJson: () -> Unit,
    onImportJson: () -> Unit,
    onExportIcs: () -> Unit,
    onPrivacy: () -> Unit,
    onReset: () -> Unit,
    onDismissReset: () -> Unit,
    onConfirmReset: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onRefreshPermissions: () -> Unit,
    onTestConnection: () -> Unit,
    onSave: () -> Unit,
    onClearApiKey: () -> Unit,
    onClose: () -> Unit,
) {
    CommercialSettingsContent(
        state = state,
        onApiKeyChanged = onApiKeyChanged,
        onDefaultReminderChanged = onDefaultReminderChanged,
        onDefaultDurationChanged = onDefaultDurationChanged,
        onExportJson = onExportJson,
        onImportJson = onImportJson,
        onExportIcs = onExportIcs,
        onPrivacy = onPrivacy,
        onReset = onReset,
        onDismissReset = onDismissReset,
        onConfirmReset = onConfirmReset,
        onOpenAppSettings = onOpenAppSettings,
        onOpenNotificationSettings = onOpenNotificationSettings,
        onOpenExactAlarmSettings = onOpenExactAlarmSettings,
        onRefreshPermissions = onRefreshPermissions,
        onTestConnection = onTestConnection,
        onSave = onSave,
        onClearApiKey = onClearApiKey,
        onClose = onClose,
    )
}

private enum class CalendarExportFormat(
    val mimeType: String,
    val subject: String,
    val chooserTitle: String,
) {
    JSON(
        mimeType = "application/json",
        subject = "声历日程备份 JSON",
        chooserTitle = "导出 JSON",
    ),
    ICS(
        mimeType = "text/calendar",
        subject = "声历日程备份 ICS",
        chooserTitle = "导出 ICS",
    ),
}

private fun shareEvents(
    context: android.content.Context,
    events: List<CalendarEvent>,
    format: CalendarExportFormat,
    onEmpty: (String) -> Unit,
) {
    if (events.isEmpty()) {
        onEmpty("暂无可导出的日程。")
        return
    }

    val payload = when (format) {
        CalendarExportFormat.JSON -> exportEventsJson(events)
        CalendarExportFormat.ICS -> exportEventsIcs(events)
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = format.mimeType
        putExtra(Intent.EXTRA_SUBJECT, format.subject)
        putExtra(Intent.EXTRA_TEXT, payload)
    }
    context.startActivity(Intent.createChooser(intent, format.chooserTitle))
}
