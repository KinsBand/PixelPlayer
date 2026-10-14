@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.theveloper.pixelplay.presentation.screens.artist

import android.app.SearchManager
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.artist.ArtistPlatformIds
import com.theveloper.pixelplay.data.artist.ArtistSocialHub
import com.theveloper.pixelplay.data.artist.BandMember
import com.theveloper.pixelplay.data.artist.SocialCategory
import com.theveloper.pixelplay.data.artist.SocialPlatformInfo
import com.theveloper.pixelplay.presentation.components.CompactCountBadge
import com.theveloper.pixelplay.presentation.components.ExpressiveFilterChip
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.viewmodel.ArtistAiUiState
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import com.theveloper.pixelplay.ui.theme.MotionTokens
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

// ─────────────────────────────────────────────────────────────────────────────
// All songs: See more → Show all, with Show less
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Under the All songs preview. Collapsed: one full-width "See more" (6 → 10 songs). Expanded:
 * a "Show less" icon button on the left (back to 6) and "Show all" with the count (the full list).
 */
@Composable
internal fun AllSongsMoreRow(
    expanded: Boolean,
    total: Int,
    canShowAll: Boolean,
    onSeeMore: () -> Unit,
    onShowLess: () -> Unit,
    onShowAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().animateContentSize(tween(MotionTokens.DurationShort4, easing = MotionTokens.Emphasized)),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!expanded) {
            FilledTonalButton(onClick = onSeeMore, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.artist_songs_see_more))
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Rounded.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        } else {
            FilledTonalIconButton(onClick = onShowLess) {
                Icon(Icons.Rounded.ExpandLess, contentDescription = stringResource(R.string.artist_songs_show_less_cd))
            }
            if (canShowAll) {
                FilledTonalButton(onClick = onShowAll, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.artist_songs_show_all))
                    Spacer(Modifier.width(8.dp))
                    CompactCountBadge(
                        count = total,
                        color = LocalContentColor.current,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Merch: the official store
// ─────────────────────────────────────────────────────────────────────────────

/** The artist's official site as a store card (the Merch tab stays even when no items are listed). */
@Composable
internal fun MerchStoreCard(hasItems: Boolean, onOpen: () -> Unit) {
    Surface(
        onClick = onOpen,
        shape = AbsoluteSmoothCornerShape(20.dp, 60),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Storefront, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.artist_merch_store_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = GoogleSansRounded),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stringResource(if (hasItems) R.string.artist_merch_store_more else R.string.artist_merch_store_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Socials hub
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The Socials tab: a full-width "Official website" button with a web-search button on its left
 * (the phone's default search app), then All · Streaming · Social media · Community chips and
 * one row per linked service. Services without a link aren't shown; neither are empty categories.
 */
@Composable
internal fun ArtistSocialsHub(artistName: String, hub: ArtistSocialHub) {
    val context = LocalContext.current
    var category by rememberSaveable { mutableStateOf<SocialCategory?>(null) }
    val categories = remember(hub.platforms) { SocialCategory.entries.filter { c -> hub.platforms.any { it.category == c } } }
    val shown = remember(hub.platforms, category) { hub.platforms.filter { category == null || it.category == category } }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(
                onClick = { webSearch(context, artistName) },
                modifier = Modifier.size(56.dp)
            ) {
                Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.artist_socials_search_cd, artistName))
            }
            Button(
                onClick = {
                    val site = hub.officialWebsite
                    if (site != null) openLink(context, site, null) else webSearch(context, "$artistName official website")
                },
                modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                shape = AbsoluteSmoothCornerShape(18.dp, 60),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                Icon(Icons.Rounded.Language, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(if (hub.officialWebsite != null) R.string.artist_socials_website else R.string.artist_socials_find_website),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (categories.size > 1) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 2.dp)) {
                item(key = "all") {
                    ExpressiveFilterChip(
                        label = stringResource(R.string.artist_socials_all),
                        selected = category == null,
                        onClick = { category = null }
                    )
                }
                items(categories, key = { it.name }) { c ->
                    ExpressiveFilterChip(
                        label = stringResource(categoryLabel(c)),
                        selected = category == c,
                        onClick = { category = if (category == c) null else c }
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            shown.forEach { platform -> SocialRow(platform, onClick = { openLink(context, platform.webUrl, platform.appUri) }) }
        }
    }
}

private fun categoryLabel(c: SocialCategory): Int = when (c) {
    SocialCategory.STREAMING -> R.string.artist_socials_streaming
    SocialCategory.SOCIAL -> R.string.artist_socials_social
    SocialCategory.COMMUNITY -> R.string.artist_socials_community
}

/**
 * One linked service, in the same row-card style as the Events and Merch tabs. The leading
 * icon is the service's own app icon when that app is on the phone (launcher apps are visible
 * through the manifest's MAIN/LAUNCHER query), else its brand glyph or a brand-coloured
 * initial. Tap opens it; hold copies the link.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SocialRow(platform: SocialPlatformInfo, onClick: () -> Unit) {
    val context = LocalContext.current
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val openLabel = stringResource(R.string.artist_socials_open_cd, platform.displayName)
    val copyLabel = stringResource(R.string.artist_socials_copy_cd)
    val copiedText = stringResource(R.string.artist_socials_link_copied, platform.displayName)
    Surface(
        shape = AbsoluteSmoothCornerShape(20.dp, 60),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clip(AbsoluteSmoothCornerShape(20.dp, 60))
            .combinedClickable(
                onClickLabel = openLabel,
                onLongClickLabel = copyLabel,
                role = Role.Button,
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                    clipboard?.setPrimaryClip(android.content.ClipData.newPlainText(platform.displayName, platform.webUrl))
                    // Android 13+ shows its own "copied" chip.
                    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
                        android.widget.Toast.makeText(context, copiedText, android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            )
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            SocialAppIcon(platform)
            Spacer(Modifier.width(14.dp))
            Text(
                platform.displayName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

/** 40 dp leading icon: installed app icon → brand glyph → brand-coloured initial. */
@Composable
private fun SocialAppIcon(platform: SocialPlatformInfo) {
    val context = LocalContext.current
    val appIcon by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, platform.id) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val pm = context.packageManager
            appPackages(platform.id).firstNotNullOfOrNull { pkg ->
                runCatching {
                    pm.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap()
                }.getOrNull()
            }
        }
    }
    val icon = appIcon
    if (icon != null) {
        androidx.compose.foundation.Image(
            bitmap = icon,
            contentDescription = null,
            modifier = Modifier.size(40.dp).clip(AbsoluteSmoothCornerShape(12.dp, 60))
        )
        return
    }
    val brand = brandColor(platform.id)
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(brand ?: MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center
    ) {
        val onBrand = if (brand != null) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSecondaryContainer
        val brandIcon = brandIconRes(platform.id)
        when {
            brandIcon != null -> Icon(painterResource(brandIcon), contentDescription = null, tint = onBrand, modifier = Modifier.size(20.dp))
            brand != null -> Text(
                platform.displayName.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = GoogleSansRounded),
                fontWeight = FontWeight.Bold,
                color = onBrand
            )
            else -> Icon(
                when (platform.category) {
                    SocialCategory.STREAMING -> Icons.Rounded.MusicNote
                    SocialCategory.SOCIAL -> Icons.Rounded.Public
                    SocialCategory.COMMUNITY -> Icons.Rounded.Forum
                },
                contentDescription = null,
                tint = onBrand,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** Android packages for each service's app (first installed one wins). */
private fun appPackages(id: String): List<String> = when (id) {
    ArtistPlatformIds.SPOTIFY -> listOf("com.spotify.music")
    ArtistPlatformIds.YOUTUBE_MUSIC -> listOf("com.google.android.apps.youtube.music")
    ArtistPlatformIds.APPLE_MUSIC -> listOf("com.apple.android.music")
    ArtistPlatformIds.SOUNDCLOUD -> listOf("com.soundcloud.android")
    ArtistPlatformIds.DEEZER -> listOf("deezer.android.app")
    ArtistPlatformIds.TIDAL -> listOf("com.aspiro.tidal")
    ArtistPlatformIds.BANDCAMP -> listOf("com.bandcamp.android")
    ArtistPlatformIds.AMAZON_MUSIC -> listOf("com.amazon.mp3")
    ArtistPlatformIds.INSTAGRAM -> listOf("com.instagram.android")
    ArtistPlatformIds.TIKTOK -> listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill")
    ArtistPlatformIds.YOUTUBE -> listOf("com.google.android.youtube")
    ArtistPlatformIds.X_TWITTER -> listOf("com.twitter.android")
    ArtistPlatformIds.FACEBOOK -> listOf("com.facebook.katana")
    ArtistPlatformIds.THREADS -> listOf("com.instagram.barcelona")
    ArtistPlatformIds.SNAPCHAT -> listOf("com.snapchat.android")
    ArtistPlatformIds.LINKEDIN -> listOf("com.linkedin.android")
    ArtistPlatformIds.BLUESKY -> listOf("xyz.blueskyweb.app")
    ArtistPlatformIds.DISCORD -> listOf("com.discord")
    ArtistPlatformIds.REDDIT -> listOf("com.reddit.frontpage")
    ArtistPlatformIds.SUBSTACK -> listOf("com.substack.app")
    ArtistPlatformIds.PATREON -> listOf("com.patreon.android")
    ArtistPlatformIds.TWITCH -> listOf("tv.twitch.android.app")
    ArtistPlatformIds.PINTEREST -> listOf("com.pinterest")
    else -> emptyList()
}

/** Brand colour for the fallback badge when the app isn't installed. */
private fun brandColor(id: String): androidx.compose.ui.graphics.Color? = when (id) {
    ArtistPlatformIds.SPOTIFY -> androidx.compose.ui.graphics.Color(0xFF1DB954)
    ArtistPlatformIds.YOUTUBE_MUSIC, ArtistPlatformIds.YOUTUBE -> androidx.compose.ui.graphics.Color(0xFFFF0033)
    ArtistPlatformIds.APPLE_MUSIC -> androidx.compose.ui.graphics.Color(0xFFFA2D48)
    ArtistPlatformIds.SOUNDCLOUD -> androidx.compose.ui.graphics.Color(0xFFFF5500)
    ArtistPlatformIds.DEEZER -> androidx.compose.ui.graphics.Color(0xFFA238FF)
    ArtistPlatformIds.TIDAL, ArtistPlatformIds.THREADS, ArtistPlatformIds.X_TWITTER, ArtistPlatformIds.TIKTOK, ArtistPlatformIds.SUBSTACK ->
        androidx.compose.ui.graphics.Color(0xFF1F1F1F)
    ArtistPlatformIds.BANDCAMP -> androidx.compose.ui.graphics.Color(0xFF1DA0C3)
    ArtistPlatformIds.AMAZON_MUSIC -> androidx.compose.ui.graphics.Color(0xFF25D1DA)
    ArtistPlatformIds.INSTAGRAM -> androidx.compose.ui.graphics.Color(0xFFE1306C)
    ArtistPlatformIds.FACEBOOK -> androidx.compose.ui.graphics.Color(0xFF1877F2)
    ArtistPlatformIds.SNAPCHAT -> androidx.compose.ui.graphics.Color(0xFFE6C800)
    ArtistPlatformIds.LINKEDIN -> androidx.compose.ui.graphics.Color(0xFF0A66C2)
    ArtistPlatformIds.BLUESKY -> androidx.compose.ui.graphics.Color(0xFF1185FE)
    ArtistPlatformIds.DISCORD -> androidx.compose.ui.graphics.Color(0xFF5865F2)
    ArtistPlatformIds.REDDIT -> androidx.compose.ui.graphics.Color(0xFFFF4500)
    ArtistPlatformIds.PATREON -> androidx.compose.ui.graphics.Color(0xFFF96854)
    ArtistPlatformIds.TWITCH -> androidx.compose.ui.graphics.Color(0xFF9146FF)
    ArtistPlatformIds.PINTEREST -> androidx.compose.ui.graphics.Color(0xFFE60023)
    else -> null
}

private fun brandIconRes(id: String): Int? = when (id) {
    ArtistPlatformIds.SPOTIFY -> R.drawable.ic_source_spotify
    ArtistPlatformIds.YOUTUBE_MUSIC -> R.drawable.ic_source_youtube_music
    ArtistPlatformIds.APPLE_MUSIC -> R.drawable.ic_source_apple_music
    else -> null
}

/** Opens the service's app when one is set up for [appUri], otherwise the web page. */
private fun openLink(context: android.content.Context, webUrl: String, appUri: String?) {
    if (appUri != null) {
        val opened = runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, appUri.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
        if (opened) return
    }
    openWebLink(context, webUrl)
}

/** A web search in the phone's default search app (Google or whichever is set). */
private fun webSearch(context: android.content.Context, query: String) {
    val search = Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val ok = runCatching { context.startActivity(search) }.isSuccess
    if (!ok) openWebLink(context, "https://www.google.com/search?q=" + android.net.Uri.encode(query))
}

// ─────────────────────────────────────────────────────────────────────────────
// About finale: title, members, on-device Ask box
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun AboutFinaleTitle(artistName: String) {
    Text(
        text = stringResource(R.string.artist_about_title, artistName),
        style = MaterialTheme.typography.headlineSmall.copy(fontFamily = GoogleSansRounded),
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 12.dp).semantics { heading() }
    )
}

/** Band members: picture, name and instrument / role; past members are marked. */
@Composable
internal fun BandMembersRow(members: List<BandMember>) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.artist_members_title),
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = GoogleSansRounded),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() }
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
            items(members, key = { it.name }) { member -> BandMemberItem(member) }
        }
    }
}

@Composable
private fun BandMemberItem(member: BandMember) {
    val pastLabel = stringResource(R.string.artist_member_past)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(96.dp).semantics(mergeDescendants = true) {}
    ) {
        Box(
            Modifier.size(80.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            if (member.imageUrl != null) {
                SmartImage(model = member.imageUrl, contentDescription = null, shape = CircleShape, modifier = Modifier.size(80.dp))
            } else {
                Text(
                    member.name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() },
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = GoogleSansRounded),
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
        Spacer(Modifier.size(6.dp))
        Text(member.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val role = listOfNotNull(member.role.takeIf { it.isNotBlank() }, pastLabel.takeIf { !member.isCurrent }).joinToString(" · ")
        if (role.isNotBlank()) {
            Text(role, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * "Ask about ‹artist›": a question box answered by Gemini Nano on the phone (nothing goes
 * online), with three starter questions. Only shown where Nano is ready.
 */
@Composable
internal fun ArtistAskBox(artistName: String, state: ArtistAiUiState, onAsk: (String) -> Unit) {
    var text by rememberSaveable(artistName) { mutableStateOf("") }
    val submit = { q: String -> if (q.isNotBlank() && !state.thinking) { onAsk(q); text = "" } }
    Surface(
        shape = AbsoluteSmoothCornerShape(24.dp, 60),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.padding(16.dp).animateContentSize(tween(MotionTokens.DurationMedium2, easing = MotionTokens.Emphasized)),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.artist_ai_title, artistName),
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = GoogleSansRounded),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                stringResource(R.string.artist_ai_private),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(stringResource(R.string.artist_ai_hint)) },
                singleLine = true,
                shape = CircleShape,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit(text) }),
                trailingIcon = {
                    IconButton(onClick = { submit(text) }, enabled = text.isNotBlank() && !state.thinking) {
                        Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = stringResource(R.string.artist_ai_send_cd))
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            if (state.answer == null && !state.thinking) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val starters = listOf(R.string.artist_ai_suggest_members, R.string.artist_ai_suggest_origin, R.string.artist_ai_suggest_sound)
                    items(starters, key = { it }) { res ->
                        val q = stringResource(res)
                        ExpressiveFilterChip(label = q, selected = false, onClick = { submit(q) })
                    }
                }
            }
            AnimatedVisibility(visible = state.thinking, enter = fadeIn(), exit = fadeOut()) {
                LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            val answer = state.answer
            if (answer != null) {
                Surface(
                    shape = AbsoluteSmoothCornerShape(18.dp, 60),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.question?.let {
                            Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        }
                        Text(answer, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
