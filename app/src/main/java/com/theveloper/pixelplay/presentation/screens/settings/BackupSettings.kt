package com.theveloper.pixelplay.presentation.screens.settings

import com.theveloper.pixelplay.presentation.components.BackupModuleSelectionDialog
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kotlinx.coroutines.launch
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.backup.model.BackupOperationType
import com.theveloper.pixelplay.data.backup.model.BackupSection
import com.theveloper.pixelplay.presentation.viewmodel.SettingsViewModel
import com.theveloper.pixelplay.presentation.screens.ActionSettingsItem
import com.theveloper.pixelplay.presentation.viewmodel.SettingsUiState


@Composable
internal fun BackupSettingsContent(
    settingsViewModel: SettingsViewModel,
    uiState: SettingsUiState
) {
    val context = LocalContext.current

    // Local State
    var showExportDataDialog by remember { mutableStateOf(false) }
    var showImportFlow by remember { mutableStateOf(false) }
    var exportSections by remember { mutableStateOf(BackupSection.defaultSelection) }
    var importFileUri by remember { mutableStateOf<Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            settingsViewModel.exportAppData(uri, exportSections)
        }
    }

    val importFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            importFileUri = uri
            settingsViewModel.inspectBackupFile(uri)
        }
    }

    if (!uiState.backupInfoDismissed) {
        BackupInfoNoticeCard(
            onDismiss = { settingsViewModel.setBackupInfoDismissed(true) }
        )
        Spacer(modifier = Modifier.height(10.dp))
    }

    SettingsSubsection(title = stringResource(R.string.settings_create_backup_section)) {
        ActionSettingsItem(
            settingKey = "export_backup",
            title = stringResource(R.string.settings_export_backup_title),
            subtitle = stringResource(
                R.string.settings_export_backup_subtitle,
                buildBackupSelectionSummary(context, exportSections)
            ),
            icon = {
                Icon(
                    painter = painterResource(R.drawable.outline_save_24),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                )
            },
            primaryActionLabel = stringResource(R.string.settings_action_select_export),
            onPrimaryAction = { showExportDataDialog = true },
            enabled = !uiState.isDataTransferInProgress
        )
    }

    SettingsSubsection(
        title = stringResource(R.string.settings_restore_backup_section),
        addBottomSpace = false
    ) {
        ActionSettingsItem(
            settingKey = "import_backup",
            title = stringResource(R.string.settings_import_backup_title),
            subtitle = stringResource(R.string.settings_import_backup_subtitle),
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Restore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                )
            },
            primaryActionLabel = stringResource(R.string.settings_action_select_restore),
            onPrimaryAction = { showImportFlow = true },
            enabled = !uiState.isDataTransferInProgress
        )
    }

    if (showExportDataDialog) {
        val backupFileNameFormat = stringResource(R.string.settings_backup_file_name_format)
        BackupSectionSelectionDialog(
            operation = BackupOperationType.EXPORT,
            title = stringResource(R.string.settings_export_backup_title),
            supportingText = stringResource(R.string.settings_export_backup_hint),
            selectedSections = exportSections,
            confirmLabel = stringResource(R.string.settings_action_export_pxpl),
            inProgress = uiState.isDataTransferInProgress,
            onDismiss = { showExportDataDialog = false },
            onSelectionChanged = { exportSections = it },
            onConfirm = {
                showExportDataDialog = false
                val fileName = backupFileNameFormat.format(System.currentTimeMillis())
                exportLauncher.launch(fileName)
            }
        )
    }

    if (showImportFlow) {
        val restorePlan = uiState.restorePlan
        if (restorePlan != null && importFileUri != null) {
            // Step 2: Module selection from inspected backup
            BackupModuleSelectionDialog(
                plan = restorePlan,
                inProgress = uiState.isDataTransferInProgress,
                onDismiss = {
                    showImportFlow = false
                    importFileUri = null
                    settingsViewModel.clearRestorePlan()
                },
                onBack = {
                    importFileUri = null
                    settingsViewModel.clearRestorePlan()
                },
                onSelectionChanged = { settingsViewModel.updateRestorePlanSelection(it) },
                onConfirm = {
                    settingsViewModel.restoreFromPlan(importFileUri!!)
                    showImportFlow = false
                    importFileUri = null
                }
            )
        } else {
            // Step 1: File selection with backup history
            ImportFileSelectionDialog(
                backupHistory = uiState.backupHistory,
                isInspecting = uiState.isInspectingBackup,
                onDismiss = {
                    showImportFlow = false
                    importFileUri = null
                    settingsViewModel.clearRestorePlan()
                },
                onBrowseFile = { importFilePicker.launch("*/*") },
                onHistoryItemSelected = { entry ->
                    val uri = entry.uri.toUri()
                    importFileUri = uri
                    settingsViewModel.inspectBackupFile(uri)
                },
                onRemoveHistoryEntry = { settingsViewModel.removeBackupHistoryEntry(it) }
            )
        }
    }
}
