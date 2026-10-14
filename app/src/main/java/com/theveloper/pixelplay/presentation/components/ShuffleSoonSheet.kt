package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch

/**
 * Hold a Home shuffle button (Quick picks, Fresh Finds): the Soon slider in a sheet, so the
 * shuffle can be scheduled after any song of Up next instead of replacing what's playing.
 * The sheet slides away first, then [onConfirm] runs with "after k upcoming songs".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShuffleSoonSheet(
    playerViewModel: PlayerViewModel,
    /** What is being shuffled, for the title ("Shuffle Quick picks soon"). */
    name: String,
    onDismiss: () -> Unit,
    onConfirm: (afterUpcoming: Int) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun closeThen(action: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            onDismiss()
            action()
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        PlaylistSoonPanel(
            playerViewModel = playerViewModel,
            onBack = { closeThen {} },
            onConfirm = { k -> closeThen { onConfirm(k) } },
            titleOverride = stringResource(R.string.home_shuffle_soon_title, name),
            thenNowOverride = stringResource(R.string.home_shuffle_soon_then_now),
            thenAfterOverride = stringResource(R.string.home_shuffle_soon_then_after),
        )
        androidx.compose.foundation.layout.Spacer(Modifier.navigationBarsPadding())
    }
}
