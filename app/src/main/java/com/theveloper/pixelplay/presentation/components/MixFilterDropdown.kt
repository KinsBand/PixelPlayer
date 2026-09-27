package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MixFilterDropdown(
    modifier: Modifier = Modifier
) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    
    // States for selected chips in each category (mock)
    var selectedCountry by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedEra by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedGenre by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedMood by rememberSaveable { mutableStateOf<String?>(null) }

    val countries = listOf("🇦🇺 AU", "🇺🇸 US", "🇯🇵 JP", "🇬🇧 UK", "🇧🇷 BR", "🇫🇷 FR")
    val eras = listOf("70s", "80s", "90s", "00s", "10s", "Modern")
    val genres = listOf("Rock", "Pop", "Jazz", "Electronic", "Hip Hop", "Classical")
    val moods = listOf("Chill", "Energetic", "Focus", "Melancholy", "Party", "Workout")

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Full-width trigger pill button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(OliveAccentYellow.copy(alpha = 0.15f))
                .border(1.dp, OliveAccentYellow.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                .clickable { isExpanded = !isExpanded }
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Mix Filters",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = OliveCream
            )
            Icon(
                imageVector = if (isExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                contentDescription = if (isExpanded) "Collapse filters" else "Expand filters",
                tint = OliveCream
            )
        }

        // Expanded Panel
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(OliveDarker)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Category 1: Country
                FilterCategoryRow(
                    title = "Country",
                    items = countries,
                    selectedItem = selectedCountry,
                    onSelect = { selectedCountry = if (selectedCountry == it) null else it }
                )

                // Category 2: Era
                FilterCategoryRow(
                    title = "Era",
                    items = eras,
                    selectedItem = selectedEra,
                    onSelect = { selectedEra = if (selectedEra == it) null else it }
                )

                // Category 3: Genre
                FilterCategoryRow(
                    title = "Genre",
                    items = genres,
                    selectedItem = selectedGenre,
                    onSelect = { selectedGenre = if (selectedGenre == it) null else it }
                )

                // Category 4: Mood
                FilterCategoryRow(
                    title = "Mood",
                    items = moods,
                    selectedItem = selectedMood,
                    onSelect = { selectedMood = if (selectedMood == it) null else it }
                )

                // Action Button: Generate Mix
                Button(
                    onClick = { isExpanded = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = OliveAccentYellow,
                        contentColor = OliveDarker
                    ),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.fillMaxWidth().height(40.dp)
                ) {
                    Text(
                        text = "Generate Mix",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterCategoryRow(
    title: String,
    items: List<String>,
    selectedItem: String?,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = OliveCream.copy(alpha = 0.7f)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(items) { item ->
                val isSelected = item == selectedItem
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelect(item) },
                    label = { Text(item) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color.Transparent,
                        selectedContainerColor = OliveAccentYellow,
                        labelColor = OliveCream,
                        selectedLabelColor = OliveDarker
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = OliveCream.copy(alpha = 0.2f),
                        selectedBorderColor = Color.Transparent,
                        borderWidth = 1.dp,
                        selectedBorderWidth = 0.dp
                    )
                )
            }
        }
    }
}
