package com.theveloper.pixelplay.presentation.screens.settings

import com.theveloper.pixelplay.data.preferences.AiPreferencesRepository
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.rotate
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.viewmodel.SettingsViewModel
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import com.theveloper.pixelplay.presentation.screens.ActionSettingsItem
import com.theveloper.pixelplay.presentation.screens.AiSystemPromptItem
import com.theveloper.pixelplay.presentation.screens.AiUsageDateHeader
import com.theveloper.pixelplay.presentation.screens.AiUsageLogItem
import com.theveloper.pixelplay.presentation.screens.SliderSettingsItem
import com.theveloper.pixelplay.presentation.screens.SwitchSettingItem
import com.theveloper.pixelplay.presentation.screens.ThemeSelectorItem
import com.theveloper.pixelplay.presentation.viewmodel.SettingsUiState


@Composable
internal fun AiSettingsContent(
    settingsViewModel: SettingsViewModel,
    uiState: SettingsUiState
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val currentAiSystemPrompt by settingsViewModel.currentAiSystemPrompt.collectAsStateWithLifecycle()
    val aiProvider by settingsViewModel.aiProvider.collectAsStateWithLifecycle()

    val provider = com.theveloper.pixelplay.data.ai.provider.AiProvider.fromString(aiProvider)
    var showPromptDialog by remember { mutableStateOf(false) }
    var advancedExpanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(targetValue = if (advancedExpanded) 180f else 0f)

    SettingsSubsection(title = stringResource(R.string.settings_ai_network_section)) {
        SwitchSettingItem(
            settingKey = "offline_mode",
            title = stringResource(R.string.settings_offline_mode_title),
            subtitle = stringResource(R.string.settings_offline_mode_subtitle),
            checked = uiState.offlineMode,
            onCheckedChange = { settingsViewModel.setOfflineMode(it) },
            leadingIcon = { Icon(painterResource(R.drawable.rounded_wifi_24), null, tint = MaterialTheme.colorScheme.secondary) }
        )
    }

    // AI Engine - Gemini Nano
    SettingsSubsection(title = stringResource(R.string.settings_ai_local_engine_section)) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.settings_ai_engine_name),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.settings_ai_engine_privacy_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.AssistChip(
                        onClick = { showPromptDialog = true },
                        label = { Text(stringResource(R.string.settings_ai_system_prompt_chip)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Science,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
            }
        }

        SwitchSettingItem(
            settingKey = "safe_token",
            title = stringResource(R.string.settings_safe_token_title),
            subtitle = if (uiState.isSafeTokenLimitEnabled) {
                stringResource(R.string.settings_safe_token_on)
            } else {
                stringResource(R.string.settings_safe_token_off)
            },
            checked = uiState.isSafeTokenLimitEnabled,
            onCheckedChange = { settingsViewModel.setSafeTokenLimitEnabled(it) },
            leadingIcon = {
                Icon(
                    painterResource(R.drawable.rounded_monitoring_24),
                    null,
                    tint = if (uiState.isSafeTokenLimitEnabled) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(24.dp)
                )
            }
        )
    }

    if (showPromptDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showPromptDialog = false },
            title = { Text(stringResource(R.string.settings_ai_edit_system_prompt_title)) },
            text = {
                AiSystemPromptItem(
                    systemPrompt = currentAiSystemPrompt,
                    defaultPrompt = com.theveloper.pixelplay.data.preferences.AiPreferencesRepository.DEFAULT_SYSTEM_PROMPT,
                    onSystemPromptSave = { 
                        settingsViewModel.onAiSystemPromptChange(it)
                        showPromptDialog = false 
                    },
                    onReset = { settingsViewModel.resetAiSystemPrompt() },
                    title = stringResource(R.string.settings_system_prompt_title),
                    subtitle = stringResource(R.string.settings_system_prompt_subtitle)
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showPromptDialog = false }) {
                    Text(stringResource(R.string.common_close))
                }
            }
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    SettingsSubsection(title = stringResource(R.string.settings_ai_usage_report_section)) {
        val recentAiUsage by settingsViewModel.recentAiUsage.collectAsStateWithLifecycle()
        val totalPromptTokens by settingsViewModel.totalPromptTokens.collectAsStateWithLifecycle()
        val totalOutputTokens by settingsViewModel.totalOutputTokens.collectAsStateWithLifecycle()
        val totalThoughtTokens by settingsViewModel.totalThoughtTokens.collectAsStateWithLifecycle()

        val totalTokens = totalPromptTokens + totalOutputTokens + totalThoughtTokens
        val totalTokStr = String.format(Locale.US, "%,d", totalTokens)
        val promptTokStr = String.format(Locale.US, "%,d", totalPromptTokens)
        val outputTokStr = String.format(Locale.US, "%,d", totalOutputTokens)
        val thoughtTokStr = String.format(Locale.US, "%,d", totalThoughtTokens)

        ActionSettingsItem(
            title = stringResource(R.string.settings_total_consumption_title),
            subtitle = stringResource(
                R.string.settings_ai_usage_tokens_subtitle,
                totalTokStr,
                promptTokStr,
                outputTokStr,
                thoughtTokStr
            ),
            icon = {
                Icon(
                    painter = painterResource(R.drawable.rounded_monitoring_24),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary
                )
            },
            primaryActionLabel = stringResource(R.string.settings_ai_clear_logs),
            onPrimaryAction = { settingsViewModel.clearAiUsageData() }
        )

        if (recentAiUsage.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            var expanded by remember { mutableStateOf(false) }
            val rotation by animateFloatAsState(targetValue = if (expanded) 180f else 0f)
            
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                color = Color.Transparent
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(R.drawable.rounded_monitoring_24),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.settings_ai_activity_log_title, recentAiUsage.size),
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = GoogleSansRounded),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.ExpandMore,
                        contentDescription = if (expanded) stringResource(R.string.settings_ai_hide_logs) else stringResource(R.string.settings_ai_show_logs),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.rotate(rotation)
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 8.dp)
                ) {
                    val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
                    val groupedUsage = recentAiUsage.groupBy { 
                        dateFormat.format(Date(it.timestamp)) 
                    }

                    groupedUsage.forEach { (date, items) ->
                        AiUsageDateHeader(date = date)
                        items.forEach { usage ->
                            AiUsageLogItem(usage = usage)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }

    // Generation and song-data tuning sits last: it is the part almost nobody changes.
    Spacer(modifier = Modifier.height(16.dp))

    // Advanced AI Settings Collapsible
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { advancedExpanded = !advancedExpanded }
            // expand/collapse actions carry framework-localised labels, so TalkBack
            // announces the disclosure state in every shipped locale without new strings.
            .semantics {
                if (advancedExpanded) {
                    collapse { advancedExpanded = false; true }
                } else {
                    expand { advancedExpanded = true; true }
                }
            }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_ai_advanced_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.settings_ai_advanced_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.Rounded.ExpandMore,
            contentDescription = null,
            modifier = Modifier
                .size(24.dp)
                .rotate(rotation)
        )
    }

    androidx.compose.animation.AnimatedVisibility(
        visible = advancedExpanded,
        enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
    ) {
        Column {
            // Generation Parameters Section
            SettingsSubsection(title = stringResource(R.string.settings_ai_generation_params_section)) {
                SliderSettingsItem(
                    label = stringResource(R.string.settings_ai_temperature_label),
                    value = settingsViewModel.aiTemperature.collectAsStateWithLifecycle().value,
                    valueRange = 0.0f..2.0f,
                    steps = 20,
                    onValueChange = { settingsViewModel.onAiTemperatureChange(it) },
                    valueText = { String.format(Locale.US, "%.2f", it) }
                )
                Text(
                    text = stringResource(R.string.settings_ai_temperature_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                )
                SliderSettingsItem(
                    label = stringResource(R.string.settings_ai_top_p_label),
                    value = settingsViewModel.aiTopP.collectAsStateWithLifecycle().value,
                    valueRange = 0.0f..1.0f,
                    steps = 20,
                    onValueChange = { settingsViewModel.onAiTopPChange(it) },
                    valueText = { String.format(Locale.US, "%.2f", it) }
                )
                Text(
                    text = stringResource(R.string.settings_ai_top_p_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                )
                SliderSettingsItem(
                    label = stringResource(R.string.settings_ai_top_k_label),
                    value = settingsViewModel.aiTopK.collectAsStateWithLifecycle().value.toFloat(),
                    valueRange = 1f..100f,
                    steps = 99,
                    onValueChange = { settingsViewModel.onAiTopKChange(it.toInt()) },
                    valueText = { it.toInt().toString() }
                )
                Text(
                    text = stringResource(R.string.settings_ai_top_k_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                )
                SliderSettingsItem(
                    label = stringResource(R.string.settings_ai_max_tokens_label),
                    value = settingsViewModel.aiMaxTokens.collectAsStateWithLifecycle().value.toFloat(),
                    valueRange = 128f..8192f,
                    steps = 63,
                    onValueChange = { settingsViewModel.onAiMaxTokensChange(it.toInt()) },
                    valueText = { it.toInt().toString() }
                )
                Text(
                    text = stringResource(R.string.settings_ai_max_tokens_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                )
                SliderSettingsItem(
                    label = stringResource(R.string.settings_ai_presence_penalty_label),
                    value = settingsViewModel.aiPresencePenalty.collectAsStateWithLifecycle().value,
                    valueRange = -2.0f..2.0f,
                    steps = 40,
                    onValueChange = { settingsViewModel.onAiPresencePenaltyChange(it) },
                    valueText = { String.format(Locale.US, "%.1f", it) }
                )
                Text(
                    text = stringResource(R.string.settings_ai_presence_penalty_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                )
                SliderSettingsItem(
                    label = stringResource(R.string.settings_ai_frequency_penalty_label),
                    value = settingsViewModel.aiFrequencyPenalty.collectAsStateWithLifecycle().value,
                    valueRange = -2.0f..2.0f,
                    steps = 40,
                    onValueChange = { settingsViewModel.onAiFrequencyPenaltyChange(it) },
                    valueText = { String.format(Locale.US, "%.1f", it) }
                )
                Text(
                    text = stringResource(R.string.settings_ai_frequency_penalty_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Song Data Configuration Section
            SettingsSubsection(title = stringResource(R.string.settings_ai_song_data_section)) {
                val aiSampleSize by settingsViewModel.aiSampleSize.collectAsStateWithLifecycle()
                SliderSettingsItem(
                    label = stringResource(R.string.settings_ai_sample_size_label),
                    value = aiSampleSize.toFloat(),
                    valueRange = 10f..120f,
                    steps = 11,
                    onValueChange = { settingsViewModel.onAiSampleSizeChange(it.toInt()) },
                    valueText = { context.getString(R.string.settings_ai_sample_size_value, it.toInt()) }
                )
                Text(
                    text = stringResource(R.string.settings_ai_sample_size_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                )
                ThemeSelectorItem(
                    label = stringResource(R.string.settings_ai_digest_detail_label),
                    description = stringResource(R.string.settings_ai_digest_detail_desc),
                    options = mapOf(
                        "safe" to stringResource(R.string.settings_ai_digest_concise),
                        "full" to stringResource(R.string.settings_ai_digest_full)
                    ),
                    selectedKey = settingsViewModel.aiDigestMode.collectAsStateWithLifecycle().value,
                    onSelectionChanged = { settingsViewModel.onAiDigestModeChange(it) },
                    leadingIcon = {
                        Icon(
                            painterResource(R.drawable.rounded_monitoring_24),
                            null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                )
                SwitchSettingItem(
                    title = stringResource(R.string.settings_ai_extended_fields_title),
                    subtitle = stringResource(R.string.settings_ai_extended_fields_subtitle),
                    checked = settingsViewModel.aiIncludeExtendedFields.collectAsStateWithLifecycle().value,
                    onCheckedChange = { settingsViewModel.onAiIncludeExtendedFieldsChange(it) },
                    leadingIcon = {
                        Icon(
                            painterResource(R.drawable.rounded_music_note_24),
                            null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                )
            }
        }
    }
}
