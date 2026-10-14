package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.ui.theme.LocalPixelPlayDarkTheme

/**
 * The one header every bottom-nav tab uses (Home, Radio, Search, Friends, Library):
 * tab title on the left, optional [actions], and the round settings button on the right.
 * No tinted band — it sits on the screen's own background, like Pixel's Now Playing.
 *
 * [titleContent] replaces the plain title (Search types its query there; Library shows its
 * tab pill in compact mode). [subtitle] is an optional line under the title.
 */
@Composable
fun TabHeader(
    title: String,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: (@Composable () -> Unit)? = null,
    titleContent: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .heightIn(min = 72.dp)
            .padding(start = 20.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (titleContent != null) {
                titleContent()
            } else {
                Column {
                    TabHeaderTitle(title)
                    subtitle?.invoke()
                }
            }
        }
        actions?.invoke(this)
        TabSettingsButton(onClick = onSettingsClick)
    }
}

/** The shared tab title style: headlineLarge, extra bold, primary. */
@Composable
fun TabHeaderTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.semantics { heading() }
    )
}

/**
 * Big round settings button (56 dp), modelled on Pixel's Now Playing: a dark circle with a
 * soft gear in dark theme, the deepest surface tone in light theme. Colours come from the
 * Material You / album-art scheme, so it always matches the screen.
 */
@Composable
fun TabSettingsButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val dark = LocalPixelPlayDarkTheme.current
    // Same red/green trace as the nav pill, started by the same connectivity flip.
    val trace = rememberStatusTrace()
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (dark) colors.surfaceContainerLowest else colors.surfaceContainerHighest,
        contentColor = colors.onSurfaceVariant,
        modifier = modifier
            .size(56.dp)
            .statusOutlineTrace(trace) { CircleShape }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(R.drawable.rounded_settings_24),
                contentDescription = stringResource(R.string.library_cd_open_settings),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
