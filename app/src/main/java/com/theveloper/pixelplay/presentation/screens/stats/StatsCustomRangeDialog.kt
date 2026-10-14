package com.theveloper.pixelplay.presentation.screens.stats

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Picks an inclusive date range for the "Custom" stats range. Future days can't be picked. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StatsCustomRangeDialog(
    initial: Pair<LocalDate, LocalDate>?,
    onConfirm: (LocalDate, LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    // The picker works in UTC midnights.
    fun LocalDate.toPickerMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

    val todayMillis = remember { LocalDate.now().toPickerMillis() }
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initial?.first?.toPickerMillis(),
        initialSelectedEndDateMillis = initial?.second?.toPickerMillis(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= todayMillis
            override fun isSelectableYear(year: Int): Boolean = year <= LocalDate.now().year
        }
    )
    val start = state.selectedStartDateMillis
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = start != null,
                onClick = {
                    val from = start ?: return@TextButton
                    val to = state.selectedEndDateMillis ?: from
                    onConfirm(from.toLocalDate(), to.toLocalDate())
                }
            ) { Text(stringResource(R.string.stats_x_custom_apply)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.stats_x_custom_cancel)) }
        }
    ) {
        DateRangePicker(
            state = state,
            modifier = Modifier.fillMaxWidth().height(520.dp)
        )
    }
}
