package ru.astondev.tempomobile.ui.logtime

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.astondev.tempomobile.domain.IssueRef
import ru.astondev.tempomobile.ui.theme.TempoDimens
import ru.astondev.tempomobile.util.DateTimeFormats
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogTimeScreen(
    onDone: () -> Unit,
    onCancel: () -> Unit,
    viewModel: LogTimeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    LaunchedEffect(state.done) {
        if (state.done) onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (state.editingId != null) "Редактировать" else "Лог времени")
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        if (state.loading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(scrollState)
                .padding(TempoDimens.screenPadding),
        ) {
            Text("Задача", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))

            if (state.recentIssues.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(TempoDimens.chipGap)) {
                    state.recentIssues.take(4).forEach { issue ->
                        FilterChip(
                            selected = state.issueKey.equals(issue.key, ignoreCase = true),
                            onClick = { viewModel.selectIssue(issue) },
                            label = { Text(issue.key) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            OutlinedTextField(
                value = state.issueKey,
                onValueChange = viewModel::onIssueKeyChange,
                label = { Text("Ключ или поиск") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    TextButton(onClick = viewModel::resolveAndRememberIssue) {
                        Text(if (state.resolvingIssue) "..." else "OK")
                    }
                },
            )
            if (state.searchingIssues) {
                Text("Поиск…", style = MaterialTheme.typography.bodySmall)
            }
            state.issueSuggestions.forEach { issue ->
                IssueSuggestionRow(issue = issue, onClick = { viewModel.selectIssue(issue) })
            }
            if (state.issueSummary.isNotBlank()) {
                Text(
                    text = state.issueSummary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = state.pinAsDefault,
                    onCheckedChange = viewModel::setPinAsDefault,
                )
                Text("Закрепить как задачу по умолчанию")
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.comment,
                onValueChange = viewModel::onCommentChange,
                label = { Text("Комментарий") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))
            Text("Дата", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            DatePickerField(value = state.date, onChange = viewModel::onDateChange)

            Spacer(Modifier.height(16.dp))
            Text("Время", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.timeInputMode == TimeInputMode.Duration,
                    onClick = { viewModel.setTimeInputMode(TimeInputMode.Duration) },
                    label = { Text("Длительность") },
                )
                FilterChip(
                    selected = state.timeInputMode == TimeInputMode.Interval,
                    onClick = { viewModel.setTimeInputMode(TimeInputMode.Interval) },
                    label = { Text("Интервал") },
                )
            }
            Spacer(Modifier.height(10.dp))

            when (state.timeInputMode) {
                TimeInputMode.Duration -> {
                    TimePickerField(
                        label = "Начало",
                        value = state.startTime,
                        onChange = viewModel::onStartChange,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DurationNumberField(
                            label = "Часы",
                            value = state.durationHours,
                            onChange = viewModel::onDurationHoursChange,
                            modifier = Modifier.weight(1f),
                        )
                        DurationNumberField(
                            label = "Минуты",
                            value = state.durationMinutes,
                            onChange = viewModel::onDurationMinutesChange,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Длительность: ${DateTimeFormats.formatDuration(state.durationSeconds)}",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (state.remainingTargetSeconds > 0) {
                        Text(
                            text = "Осталось до нормы: ${DateTimeFormats.formatDuration(state.remainingTargetSeconds)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            Triple("+30м", 0, 30),
                            Triple("+1ч", 1, 0),
                            Triple("+2ч", 2, 0),
                            Triple("+4ч", 4, 0),
                        ).forEach { (label, h, m) ->
                            FilterChip(
                                selected = false,
                                onClick = { viewModel.addDuration(hours = h, minutes = m) },
                                label = { Text(label) },
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.remainingTargetSeconds >= 60) {
                            FilterChip(
                                selected = false,
                                onClick = viewModel::fillRemainingTarget,
                                label = { Text("До нормы") },
                            )
                        }
                        FilterChip(
                            selected = false,
                            onClick = { viewModel.resetDurationTo(0, 0) },
                            label = { Text("Сброс") },
                        )
                    }
                    Text(
                        text = "Интервал: ${state.autoIntervalLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                TimeInputMode.Interval -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TimePickerField(
                            label = "Начало",
                            value = state.startTime,
                            onChange = viewModel::onStartChange,
                            modifier = Modifier.weight(1f),
                        )
                        TimePickerField(
                            label = "Конец",
                            value = state.endTime,
                            onChange = viewModel::onEndChange,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Text(
                        text = "Длительность: ${DateTimeFormats.formatDuration(state.durationSeconds)}",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            // attributesError is only set for unexpected failures; 404 = no attrs on this server.

            if (state.attributes.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("Атрибуты Tempo", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                state.attributes.forEach { attr ->
                    AttributeField(
                        name = attr.name + if (attr.required) " *" else "",
                        value = state.attributeValues[attr.key].orEmpty(),
                        options = attr.values,
                        onChange = { viewModel.onAttributeChange(attr.key, it) },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }

            if (state.error != null) {
                Spacer(Modifier.height(12.dp))
                Text(state.error!!, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = viewModel::save,
                enabled = !state.saving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                if (state.saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("Сохранить")
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Отмена")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun IssueSuggestionRow(issue: IssueRef, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Text(issue.key, fontWeight = FontWeight.SemiBold)
        if (issue.summary.isNotBlank()) {
            Text(
                issue.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DurationNumberField(
    label: String,
    value: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            text = raw.filter { it.isDigit() }.take(3)
            text.toIntOrNull()?.let(onChange)
            if (text.isEmpty()) onChange(0)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerField(
    value: LocalDate,
    onChange: (LocalDate) -> Unit,
) {
    var show by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = { show = true },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Дата: $value")
    }
    Row {
        TextButton(onClick = { onChange(LocalDate.now()) }) { Text("Сегодня") }
        TextButton(onClick = { onChange(value.minusDays(1)) }) { Text("-1 день") }
        TextButton(onClick = { onChange(value.plusDays(1)) }) { Text("+1 день") }
    }
    if (show) {
        val millis = value.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = millis)
        DatePickerDialog(
            onDismissRequest = { show = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        val date = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        onChange(date)
                    }
                    show = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { show = false }) { Text("Отмена") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerField(
    label: String,
    value: LocalTime,
    onChange: (LocalTime) -> Unit,
    modifier: Modifier = Modifier,
) {
    var show by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = { show = true },
        modifier = modifier,
    ) {
        Text("$label: ${value.format(DateTimeFormats.timeUi)}")
    }
    if (show) {
        val timeState = rememberTimePickerState(
            initialHour = value.hour,
            initialMinute = value.minute,
            is24Hour = true,
        )
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { show = false },
            confirmButton = {
                TextButton(onClick = {
                    onChange(LocalTime.of(timeState.hour, timeState.minute))
                    show = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { show = false }) { Text("Отмена") }
            },
            text = { TimePicker(state = timeState) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttributeField(
    name: String,
    value: String,
    options: List<String>,
    onChange: (String) -> Unit,
) {
    if (options.isEmpty()) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(name) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = {},
                readOnly = true,
                label = { Text(name) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onChange(option)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}
