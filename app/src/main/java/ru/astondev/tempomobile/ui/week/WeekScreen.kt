package ru.astondev.tempomobile.ui.week

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.astondev.tempomobile.domain.DaySummary
import ru.astondev.tempomobile.domain.Worklog
import ru.astondev.tempomobile.ui.theme.TempoDimens
import ru.astondev.tempomobile.util.DateTimeFormats
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun WeekScreen(
    onAdd: (LocalDate) -> Unit,
    onEdit: (String) -> Unit,
    onDuplicate: (String) -> Unit,
    onSettings: () -> Unit,
    onQuickLog: (LocalDate) -> Unit = {},
    onTimer: () -> Unit = {},
    refreshSignal: Boolean = false,
    onRefreshConsumed: () -> Unit = {},
    viewModel: WeekViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    LaunchedEffect(refreshSignal) {
        if (refreshSignal) {
            viewModel.refresh(showWeekLoading = true)
            onRefreshConsumed()
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(state.message, state.undoDelete) {
        val msg = state.message ?: return@LaunchedEffect
        if (state.undoDelete != null) {
            val result = snackbar.showSnackbar(
                message = msg,
                actionLabel = "Отменить",
                duration = SnackbarDuration.Short,
            )
            viewModel.clearMessage()
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoDelete()
            }
        } else {
            snackbar.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Неделя") },
                actions = {
                    IconButton(onClick = onTimer) {
                        Icon(Icons.Default.Timer, contentDescription = "Таймер")
                    }
                    IconButton(onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.goToToday()
                    }) {
                        Icon(Icons.Default.Today, contentDescription = "Сегодня")
                    }
                    IconButton(onClick = { viewModel.showCopyDialog(true) }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Копировать день")
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Настройки")
                    }
                },
            )
        },
        floatingActionButton = {
            Box(
                modifier = Modifier.combinedClickable(
                    onClick = { onAdd(state.selectedDate) },
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onQuickLog(state.selectedDate)
                    },
                ),
            ) {
                FloatingActionButton(onClick = { onAdd(state.selectedDate) }) {
                    Icon(Icons.Default.Add, contentDescription = "Добавить (удержание — быстрый лог)")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = { viewModel.refresh(fromPull = true) },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .pointerInput(Unit) {
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            when {
                                total > 80f -> {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.previousWeek()
                                }
                                total < -80f -> {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.nextWeek()
                                }
                            }
                            total = 0f
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            total += dragAmount
                        },
                    )
                },
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                WeekHeader(
                    label = state.weekLabel,
                    weekSeconds = state.weekLoggedSeconds,
                    weekTargetSeconds = state.weekTargetSeconds,
                    onPrev = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.previousWeek()
                    },
                    onNext = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.nextWeek()
                    },
                )
                if (state.weekLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                DayStrip(
                    days = state.weekDays,
                    selected = state.selectedDate,
                    summaryFor = { state.daySummary(it) },
                    onSelect = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.selectDate(it)
                    },
                )
                val day = state.daySummary(state.selectedDate)
                DayProgress(summary = day)

                if (state.hasHiddenWeekendLogs) {
                    WeekendBanner(onReveal = viewModel::revealWeekends)
                }

                when {
                    state.loading -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }

                    else -> LazyColumn(
                        contentPadding = PaddingValues(TempoDimens.screenPadding),
                        verticalArrangement = Arrangement.spacedBy(TempoDimens.itemGap),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "${state.selectedWorklogs.size} записей · ${DateTimeFormats.formatDuration(day.loggedSeconds)}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                TextButton(onClick = { onQuickLog(state.selectedDate) }) {
                                    Text("Быстрый лог")
                                }
                            }
                        }

                        if (state.selectedWorklogs.isEmpty()) {
                            item {
                                EmptyDayState(
                                    onLog = { onAdd(state.selectedDate) },
                                    onQuickLog = { onQuickLog(state.selectedDate) },
                                )
                            }
                        } else {
                            items(state.selectedWorklogs, key = { it.id }) { wl ->
                                WorklogRow(
                                    worklog = wl,
                                    onEdit = { onEdit(wl.id) },
                                    onDuplicate = { onDuplicate(wl.id) },
                                    onDelete = { viewModel.requestDelete(wl) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    state.pendingDelete?.let { pending ->
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text("Удалить запись?") },
            text = {
                Text(
                    buildString {
                        append(pending.issueKey ?: "Без задачи")
                        append(" · ")
                        append(DateTimeFormats.formatDuration(pending.timeSpentSeconds))
                        if (pending.comment.isNotBlank()) {
                            append("\n")
                            append(pending.comment.take(80))
                        }
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelDelete) { Text("Отмена") }
            },
        )
    }

    if (state.copyDialogVisible) {
        AlertDialog(
            onDismissRequest = { viewModel.showCopyDialog(false) },
            title = { Text("Копировать день") },
            text = {
                Column {
                    Text("Скопировать ${state.selectedWorklogs.size} записей с ${state.selectedDate.format(DateTimeFormats.weekRange)} на:")
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(-1L, 1L, 7L).forEach { offset ->
                            val d = state.selectedDate.plusDays(offset)
                            val selected = d == state.copyTargetDate
                            TextButton(onClick = { viewModel.onCopyTargetChange(d) }) {
                                Text(
                                    text = "${d.format(DateTimeFormats.dayShort)} ${d.format(DateTimeFormats.dayNum)}",
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                    Text(
                        text = "Цель: ${state.copyTargetDate.format(DateTimeFormats.weekRange)}",
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::copySelectedDay) { Text("Копировать") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showCopyDialog(false) }) { Text("Отмена") }
            },
        )
    }
}

@Composable
private fun WeekendBanner(onReveal: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(TempoDimens.radiusMd),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TempoDimens.screenPadding, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Есть записи в выходные",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onReveal) { Text("Показать") }
        }
    }
}

@Composable
private fun EmptyDayState(onLog: () -> Unit, onQuickLog: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Нет записей за этот день",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Залогируйте время или используйте быстрый лог.\nМеню у записи — изменить, дублировать, удалить.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onLog) { Text("Залогировать") }
        TextButton(onClick = onQuickLog) { Text("Быстрый лог") }
    }
}

@Composable
private fun WeekHeader(
    label: String,
    weekSeconds: Int,
    weekTargetSeconds: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrev) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Пред. неделя")
        }
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(
                "Всего: ${DateTimeFormats.formatDuration(weekSeconds)} / ${DateTimeFormats.formatDuration(weekTargetSeconds)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "След. неделя")
        }
    }
}

@Composable
private fun DayStrip(
    days: List<LocalDate>,
    selected: LocalDate,
    summaryFor: (LocalDate) -> DaySummary,
    onSelect: (LocalDate) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        days.forEach { day ->
            val isSelected = day == selected
            val isToday = day == LocalDate.now()
            val summary = summaryFor(day)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(TempoDimens.radiusMd))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surface
                    )
                    .clickable { onSelect(day) }
                    .padding(horizontal = 8.dp, vertical = 10.dp)
                    .width(TempoDimens.dayCellWidth),
            ) {
                Text(
                    text = day.format(DateTimeFormats.dayShort),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = day.format(DateTimeFormats.dayNum),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(6.dp))
                DayProgressRing(
                    progress = summary.progress,
                    selected = isSelected,
                    hasTime = summary.loggedSeconds > 0,
                )
            }
        }
    }
}

@Composable
private fun DayProgressRing(
    progress: Float,
    selected: Boolean,
    hasTime: Boolean,
) {
    val track = if (selected) {
        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val fill = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.primary
    }
    val overtime = progress > 1f
    Canvas(modifier = Modifier.size(TempoDimens.ringSize)) {
        val stroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        drawArc(
            color = track,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            style = stroke,
            size = Size(size.minDimension, size.minDimension),
            topLeft = Offset.Zero,
        )
        if (hasTime) {
            drawArc(
                color = if (overtime) Color(0xFFFFB74D) else fill,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                style = stroke,
                size = Size(size.minDimension, size.minDimension),
                topLeft = Offset.Zero,
            )
        }
    }
}

@Composable
private fun DayProgress(summary: DaySummary) {
    val overtime = (summary.loggedSeconds - summary.targetSeconds).coerceAtLeast(0)
    Column(modifier = Modifier.padding(horizontal = TempoDimens.screenPadding, vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = buildString {
                    append(DateTimeFormats.formatDuration(summary.loggedSeconds))
                    if (overtime > 0) {
                        append(" +")
                        append(DateTimeFormats.formatDuration(overtime))
                    }
                },
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                "из ${DateTimeFormats.formatDuration(summary.targetSeconds)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { summary.progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(TempoDimens.progressHeight)
                .clip(RoundedCornerShape(4.dp)),
        )
    }
}

@Composable
private fun WorklogRow(
    worklog: Worklog,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val title = buildString {
        val key = worklog.issueKey
        val summary = worklog.issueSummary?.takeIf { it.isNotBlank() }
        when {
            key != null && summary != null -> append("$key · $summary")
            key != null -> append(key)
            summary != null -> append(summary)
            else -> append("Без задачи")
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(TempoDimens.radiusMd))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onEdit)
            .padding(TempoDimens.rowPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${worklog.started.format(DateTimeFormats.timeUi)} – ${worklog.end.format(DateTimeFormats.timeUi)} · ${DateTimeFormats.formatDuration(worklog.timeSpentSeconds)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            if (worklog.comment.isNotBlank()) {
                Text(
                    text = worklog.comment,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Действия")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Изменить") },
                    onClick = { menuOpen = false; onEdit() },
                )
                DropdownMenuItem(
                    text = { Text("Дублировать") },
                    onClick = { menuOpen = false; onDuplicate() },
                )
                DropdownMenuItem(
                    text = { Text("Удалить", color = MaterialTheme.colorScheme.error) },
                    onClick = { menuOpen = false; onDelete() },
                )
            }
        }
    }
}
