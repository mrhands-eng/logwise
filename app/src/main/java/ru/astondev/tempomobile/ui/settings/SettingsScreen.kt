package ru.astondev.tempomobile.ui.settings

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.astondev.tempomobile.domain.AppIcon
import ru.astondev.tempomobile.domain.AppPalette
import ru.astondev.tempomobile.domain.ThemeMode
import ru.astondev.tempomobile.util.AppIconManager.Companion.drawableRes
import ru.astondev.tempomobile.util.AppIconManager.Companion.label

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLoggedOut: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(state.loggedOut) {
        if (state.loggedOut) onLoggedOut()
    }

    LaunchedEffect(state.shareLogRequest) {
        val file = state.shareLogRequest ?: return@LaunchedEffect
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Logwise logs")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Отправить логи Logwise"))
        } catch (_: Exception) {
        } finally {
            viewModel.onShareHandled()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            SectionTitle("Аккаунт")
            Text("URL: ${state.baseUrl}", style = MaterialTheme.typography.bodyMedium)
            Text("Пользователь: ${state.username}", style = MaterialTheme.typography.bodyMedium)

            Spacer(Modifier.height(24.dp))
            SectionTitle("Оформление")
            Text("Режим темы", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = state.themeMode == mode,
                        onClick = { viewModel.setThemeMode(mode) },
                        label = { Text(mode.label()) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Палитра", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppPalette.entries.forEach { palette ->
                    FilterChip(
                        selected = state.palette == palette,
                        onClick = { viewModel.setPalette(palette) },
                        label = { Text(palette.label()) },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Иконка приложения", style = MaterialTheme.typography.labelLarge)
            Text(
                "Смена может появиться на рабочем столе не сразу",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppIcon.entries.forEach { icon ->
                    IconChoice(
                        icon = icon,
                        selected = state.appIcon == icon,
                        onClick = { viewModel.setAppIcon(icon) },
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionTitle("Календарь и норма")
            RowSwitch(
                title = "Скрывать субботу и воскресенье",
                checked = state.hideWeekends,
                onCheckedChange = viewModel::setHideWeekends,
            )
            Spacer(Modifier.height(8.dp))
            Text("Норма часов в день", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(6, 7, 8, 9, 10).forEach { h ->
                    FilterChip(
                        selected = state.dailyTargetHours == h,
                        onClick = { viewModel.setDailyTargetHours(h) },
                        label = { Text("${h}ч") },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Начало рабочего дня (для авто-интервала)",
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(8, 9, 10, 11).forEach { h ->
                    FilterChip(
                        selected = state.dayStartHour == h && state.dayStartMinute == 0,
                        onClick = { viewModel.setDayStart(h, 0) },
                        label = { Text("%02d:00".format(h)) },
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionTitle("Задача по умолчанию")
            Text(
                "Большинство записей будут создаваться на эту задачу",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            if (state.defaultIssueKey.isNotBlank()) {
                Text(
                    "${state.defaultIssueKey} — ${state.defaultIssueSummary}",
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
            }
            if (state.recentIssues.isNotEmpty()) {
                Text("Недавние", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.recentIssues.forEach { issue ->
                        FilterChip(
                            selected = state.issueInput.equals(issue.key, ignoreCase = true),
                            onClick = { viewModel.pickRecentIssue(issue) },
                            label = { Text(issue.key) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            OutlinedTextField(
                value = state.issueInput,
                onValueChange = viewModel::onIssueInputChange,
                label = { Text("Ключ задачи") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = viewModel::saveDefaultIssue,
                enabled = !state.savingIssue,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.savingIssue) "Сохранение..." else "Сохранить задачу")
            }

            Spacer(Modifier.height(24.dp))
            SectionTitle("Диагностика")
            Text(
                "Версия ${state.appVersion}. Перед отправкой: Очистить логи → воспроизвести ошибку → Поделиться логами.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                state.logPath,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = viewModel::shareLogs,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Поделиться логами")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = viewModel::clearLogs,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Очистить логи")
            }

            if (state.message != null) {
                Spacer(Modifier.height(8.dp))
                Text(state.message!!, color = MaterialTheme.colorScheme.primary)
            }
            if (state.error != null) {
                Spacer(Modifier.height(8.dp))
                Text(state.error!!, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(32.dp))
            OutlinedButton(
                onClick = viewModel::logout,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Выйти")
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "После выхода тема и настройки календаря сохраняются. Для смены учётки войдите заново.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun IconChoice(
    icon: AppIcon,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(88.dp)
            .clickable(onClick = onClick),
    ) {
        Image(
            painter = painterResource(icon.drawableRes()),
            contentDescription = icon.label(),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(72.dp)
                .clip(shape)
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    },
                    shape = shape,
                ),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = icon.label(),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun RowSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
