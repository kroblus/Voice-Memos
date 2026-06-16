package com.voicememos.aicalendar.ui

import android.Manifest
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.rounded.KeyboardVoice
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.AssistChip
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voicememos.aicalendar.domain.CalendarEvent
import com.voicememos.aicalendar.domain.EventStatus
import com.voicememos.aicalendar.speech.SpeechInputController
import com.voicememos.aicalendar.ui.theme.ShengliTheme
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

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

    val recordAudioLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) speechController.start() else viewModel.showError("需要麦克风权限才能语音输入。")
    }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* The receiver checks the final permission state before showing reminders. */ }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            ComposerBar(
                state = state,
                onTextChanged = viewModel::updateTextInput,
                onReuseTranscript = viewModel::reuseTranscript,
                onSubmit = viewModel::parseCurrentText,
                onVoiceInput = {
                    if (state.isListening) {
                        speechController.stop()
                    } else {
                        recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 18.dp)
                .padding(top = 14.dp, bottom = 8.dp),
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
                visible = state.draftEditors.isNotEmpty() && !state.showDraftSheet,
                onOpen = viewModel::showDraftSheet,
            )
        }
    }

    if (state.showSettings) {
        ModalBottomSheet(onDismissRequest = viewModel::closeSettings) {
            SettingsSheet(
                state = state,
                onApiKeyChanged = viewModel::updateApiKeyInput,
                onDefaultReminderChanged = viewModel::updateDefaultReminderMinutesInput,
                onDefaultDurationChanged = viewModel::updateDefaultDurationMinutesInput,
                onExportEvents = {
                    if (state.events.isEmpty()) {
                        viewModel.showError("暂无可导出的日程。")
                    } else {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/json"
                            putExtra(Intent.EXTRA_SUBJECT, "声历日程备份")
                            putExtra(Intent.EXTRA_TEXT, exportEventsJson(state.events))
                        }
                        context.startActivity(Intent.createChooser(intent, "导出日程"))
                    }
                },
                onSave = viewModel::saveApiKey,
                onClearApiKey = viewModel::clearApiKey,
                onClose = viewModel::closeSettings,
            )
        }
    }

    if (state.draftEditors.isNotEmpty() && state.showDraftSheet) {
        ModalBottomSheet(onDismissRequest = viewModel::hideDraftSheet) {
            DraftPanel(
                drafts = state.draftEditors,
                onUpdate = viewModel::updateDraft,
                onConfirmAll = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    viewModel.confirmAllDrafts()
                },
                onConfirm = { draftId ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    viewModel.confirmDraft(draftId)
                },
                onDiscard = viewModel::discardDraft,
                onClear = viewModel::clearDrafts,
            )
        }
    }

    state.eventEditor?.let { editor ->
        ModalBottomSheet(onDismissRequest = viewModel::closeEventEditor) {
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
private fun Header(
    selectedDate: LocalDate,
    themeMode: AppThemeMode,
    apiKeySaved: Boolean,
    onToday: () -> Unit,
    onThemeToggle: () -> Unit,
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
                onClick = onSettings,
                modifier = Modifier.testTag("settings-button"),
            ) {
                Box {
                    Icon(Icons.Rounded.Settings, contentDescription = "设置")
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
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CalendarViewMode.entries.forEach { mode ->
            FilterChip(
                selected = selected == mode,
                onClick = { onSelect(mode) },
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
        modifier = Modifier.height(250.dp),
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
                Spacer(modifier = Modifier.height(32.dp))
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
            .height(32.dp)
            .testTag("day-${date.dayOfMonth}")
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .border(
                1.dp,
                if (date == LocalDate.now()) MaterialTheme.colorScheme.tertiary else Color.Transparent,
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
        modifier = Modifier.fillMaxWidth(),
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
            .take(50)
        else -> events.filter { it.startAt.atZone(zone).toLocalDate() == selectedDate }
    }
    if (visible.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("今天还很清爽", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.testTag("event-list"),
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
                        MaterialTheme.colorScheme.primary
                    },
                ),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                event.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                event.startAt.atZone(zone).format(DateTimeFormatter.ofPattern("M月d日 HH:mm", Locale.SIMPLIFIED_CHINESE)),
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
private fun ComposerBar(
    state: CalendarUiState,
    onTextChanged: (String) -> Unit,
    onReuseTranscript: (String) -> Unit,
    onSubmit: () -> Unit,
    onVoiceInput: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.isParsing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                value = state.partialTranscript.ifBlank { state.textInput },
                onValueChange = onTextChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 92.dp, max = 132.dp)
                    .testTag("transcript-field"),
                shape = RoundedCornerShape(8.dp),
                label = { Text(if (state.isListening) "正在听" else "一句话") },
                placeholder = { Text("明天11点打开电脑，12点打开手机") },
                minLines = 2,
                maxLines = 4,
            )
            if (state.recentTranscripts.isNotEmpty() &&
                state.textInput.isBlank() &&
                state.partialTranscript.isBlank()
            ) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.recentTranscripts) { transcript ->
                        AssistChip(
                            onClick = { onReuseTranscript(transcript) },
                            label = {
                                Text(
                                    transcript,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledIconButton(
                    onClick = onVoiceInput,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("voice-input-button"),
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
                    Icon(Icons.Rounded.KeyboardVoice, contentDescription = "语音输入")
                }
                FilledIconButton(
                    onClick = onSubmit,
                    enabled = !state.isParsing && state.partialTranscript.ifBlank { state.textInput }.isNotBlank(),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("generate-button"),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "生成")
                }
            }
        }
    }
}

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
                    Text("待确认 ${drafts.size} 项", style = MaterialTheme.typography.titleMedium)
                    Text(
                        drafts.joinToString("  ") { it.title.ifBlank { "未命名" } },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TextButton(onClick = onOpen) { Text("继续确认") }
            }
        }
    }
}

@Composable
private fun DraftPanel(
    drafts: List<DraftEditor>,
    onUpdate: (String, (DraftEditor) -> DraftEditor) -> Unit,
    onConfirmAll: () -> Unit,
    onConfirm: (String) -> Unit,
    onDiscard: (String) -> Unit,
    onClear: () -> Unit,
) {
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
                        Text("待确认 ${drafts.size} 项", style = MaterialTheme.typography.titleMedium)
                        Text(
                            drafts.joinToString("  ") { it.title.ifBlank { "未命名" } },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onClear) { Text("清空") }
                        Spacer(Modifier.width(4.dp))
                        Button(
                            onClick = onConfirmAll,
                            enabled = drafts.any { it.title.isNotBlank() && it.startLocal.isNotBlank() },
                            modifier = Modifier.testTag("draft-save-all-button"),
                        ) {
                            Icon(Icons.Rounded.Save, contentDescription = null)
                            Text("全部保存")
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Notifications, contentDescription = null)
                    Text(
                        "保存时会请求通知权限，用来准时提醒。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(drafts, key = { it.draftId }) { draft ->
                        DraftEditorCard(
                            draft = draft,
                            onUpdate = { transform -> onUpdate(draft.draftId, transform) },
                            onConfirm = { onConfirm(draft.draftId) },
                            onDiscard = { onDiscard(draft.draftId) },
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
    onDiscard: () -> Unit,
) {
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
                    if (draft.missingFields.isEmpty()) Icons.Rounded.RadioButtonUnchecked else Icons.Rounded.Close,
                    contentDescription = null,
                    tint = if (draft.missingFields.isEmpty()) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        draft.title.ifBlank { "未命名任务" },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${draft.timeSummary()}  ·  ${draft.reminderSummary()}",
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { onUpdate { it.copy(expanded = !it.expanded) } },
                    modifier = Modifier.testTag("draft-edit-button"),
                ) {
                    Icon(
                        if (draft.expanded) Icons.Rounded.ExpandLess else Icons.Rounded.Edit,
                        contentDescription = if (draft.expanded) "收起编辑" else "编辑",
                    )
                }
                IconButton(onClick = onDiscard) {
                    Icon(Icons.Rounded.Delete, contentDescription = "删除草稿")
                }
                FilledIconButton(
                    onClick = onConfirm,
                    enabled = draft.title.isNotBlank() && draft.startLocal.isNotBlank(),
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("draft-save-button"),
                ) {
                    Icon(Icons.Rounded.Save, contentDescription = "保存")
                }
            }

            AnimatedVisibility(draft.expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = draft.title,
                        onValueChange = { value -> onUpdate { it.copy(title = value) } },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        label = { Text("标题") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = draft.startLocal,
                        onValueChange = { value -> onUpdate { it.copy(startLocal = value) } },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        label = { Text(if (draft.allDay) "日期 yyyy-MM-dd" else "开始 yyyy-MM-dd HH:mm") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = draft.endLocal,
                        onValueChange = { value -> onUpdate { it.copy(endLocal = value) } },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        label = { Text(if (draft.allDay) "结束日期" else "结束时间") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = draft.reminderMinutes,
                        onValueChange = { value -> onUpdate { it.copy(reminderMinutes = value) } },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        label = { Text("提醒分钟，逗号分隔") },
                        singleLine = true,
                    )
                }
            }
        }
    }
}

private fun DraftEditor.timeSummary(): String =
    when {
        startLocal.isBlank() -> "缺少时间"
        endLocal.isBlank() -> startLocal.replace("T", " ")
        else -> "${startLocal.replace("T", " ")} - ${endLocal.replace("T", " ")}"
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
private fun SettingsSheet(
    state: CalendarUiState,
    onApiKeyChanged: (String) -> Unit,
    onDefaultReminderChanged: (String) -> Unit,
    onDefaultDurationChanged: (String) -> Unit,
    onExportEvents: () -> Unit,
    onSave: () -> Unit,
    onClearApiKey: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("设置", style = MaterialTheme.typography.headlineSmall)
        Text(
            if (state.apiKeySaved) "DeepSeek API key 已保存在本机。" else "保存 DeepSeek API key 后即可生成日程。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = state.apiKeyInput,
            onValueChange = onApiKeyChanged,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("api-key-field"),
            label = { Text("DeepSeek API key") },
            singleLine = true,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = state.defaultDurationMinutesInput,
                onValueChange = onDefaultDurationChanged,
                modifier = Modifier
                    .weight(1f)
                    .testTag("default-duration-field"),
                shape = RoundedCornerShape(8.dp),
                label = { Text("默认时长") },
                suffix = { Text("分钟") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OutlinedTextField(
                value = state.defaultReminderMinutesInput,
                onValueChange = onDefaultReminderChanged,
                modifier = Modifier
                    .weight(1f)
                    .testTag("default-reminder-field"),
                shape = RoundedCornerShape(8.dp),
                label = { Text("提前提醒") },
                suffix = { Text("分钟") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.background,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Notifications, contentDescription = null)
                Column {
                    Text("到点提醒已开启", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "每个事件都会保留 0 分钟提醒。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        TextButton(
            onClick = onExportEvents,
            enabled = state.events.isNotEmpty(),
            modifier = Modifier.testTag("settings-export-button"),
        ) {
            Text("导出本地日程")
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.apiKeySaved) {
                TextButton(
                    onClick = onClearApiKey,
                    modifier = Modifier.testTag("settings-clear-key-button"),
                ) { Text("清除密钥") }
            } else {
                Spacer(Modifier.width(1.dp))
            }
            Row(horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onClose) { Text("关闭") }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onSave,
                    modifier = Modifier.testTag("settings-save-button"),
                ) { Text("保存") }
            }
        }
    }
}

private fun exportEventsJson(events: List<CalendarEvent>): String =
    events.joinToString(
        prefix = "[\n",
        postfix = "\n]",
        separator = ",\n",
    ) { event ->
        """
        |  {
        |    "id": "${event.id.escapeJson()}",
        |    "title": "${event.title.escapeJson()}",
        |    "notes": ${event.notes?.let { "\"${it.escapeJson()}\"" } ?: "null"},
        |    "startAt": "${event.startAt}",
        |    "endAt": ${event.endAt?.let { "\"$it\"" } ?: "null"},
        |    "allDay": ${event.allDay},
        |    "reminderMinutes": [${event.reminderMinutes.joinToString(",")}],
        |    "status": "${event.status.name}",
        |    "sourceTranscript": ${event.sourceTranscript?.let { "\"${it.escapeJson()}\"" } ?: "null"},
        |    "aiConfidence": ${event.aiConfidence}
        |  }
        """.trimMargin()
    }

private fun String.escapeJson(): String =
    buildString {
        this@escapeJson.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
    }
