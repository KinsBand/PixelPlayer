package com.theveloper.pixelplay.presentation.components

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.presentation.components.tabs.TabPracticeController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Drum tab from Songsterr on a 5-line percussion staff. Practice controls live in the
 * swipe-up panel under the toolbar (shared through [practice]); an uploaded PDF replaces
 * the notation until "Use tab" is chosen.
 */
@Composable
fun DrumsPerformanceView(
    title: String,
    artist: String,
    onBackgroundColor: Color,
    modifier: Modifier = Modifier,
    accentColor: Color = onBackgroundColor,
    songId: String? = null,
    practice: TabPracticeController? = null,
) {
    val controller = practice ?: rememberTabPractice(title, artist, songId)
    TabScoreView(controller, onBackgroundColor, accentColor, modifier)
}

// ─── PDF Viewer ──────────────────────────────────────────────────────────────

@Composable
fun PdfViewerComponent(pdfUri: Uri, onBackgroundColor: Color, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var pageBitmaps by remember(pdfUri) { mutableStateOf<List<Bitmap>>(emptyList()) }
    var isLoading by remember(pdfUri) { mutableStateOf(true) }
    var error by remember(pdfUri) { mutableStateOf<String?>(null) }

    LaunchedEffect(pdfUri) {
        isLoading = true
        error = null
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openFileDescriptor(pdfUri, "r")?.use { pfd ->
                    val renderer = PdfRenderer(pfd)
                    val bitmaps = mutableListOf<Bitmap>()
                    for (i in 0 until renderer.pageCount) {
                        renderer.openPage(i).use { page ->
                            val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                            val canvas = android.graphics.Canvas(bitmap)
                            canvas.drawColor(android.graphics.Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bitmaps.add(bitmap)
                        }
                    }
                    withContext(Dispatchers.Main) { pageBitmaps = bitmaps }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { error = "Failed to load PDF: ${e.localizedMessage}" }
            } finally {
                withContext(Dispatchers.Main) { isLoading = false }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (isLoading) {
            CircularProgressIndicator(color = onBackgroundColor)
        } else if (error != null) {
            Text(text = error!!, color = onBackgroundColor.copy(alpha = 0.7f))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(pageBitmaps.size) { index ->
                    Card(
                        modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    ) {
                        Image(
                            bitmap = pageBitmaps[index].asImageBitmap(),
                            contentDescription = "Page ${index + 1}",
                            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                        )
                    }
                }
            }
        }
    }
}
