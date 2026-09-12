package com.example.wavemuzix.uiresource.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * Skeleton UI for Home Screen.
 * Matches exact dimensions, paddings and structure of HomeScreen content to prevent layout jumps.
 */
@Composable
fun HomeScreenSkeleton() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 190.dp), // Exact match with HomeScreen LazyColumn
        userScrollEnabled = false
    ) {
        // 1. Welcome Section Placeholder (1:1 with UserWelcomeSection)
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp)) {
                Box(modifier = Modifier.size(width = 80.dp, height = 16.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(52.dp).clip(CircleShape).shimmerEffect())
                    Spacer(modifier = Modifier.width(16.dp))
                    Box(modifier = Modifier.size(width = 160.dp, height = 28.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
                }
            }
        }

        // 2. Quick Picks Section Placeholder (matches SectionHeader + SongHorizontalGrid)
        item {
            Column(modifier = Modifier.padding(vertical = 12.dp)) {
                // Section Header
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Box(modifier = Modifier.size(width = 120.dp, height = 12.dp).clip(RoundedCornerShape(2.dp)).shimmerEffect())
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(modifier = Modifier.size(width = 140.dp, height = 24.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
                    }
                }

                // Song Grid (Column of 4 items x 2 groups)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    userScrollEnabled = false
                ) {
                    items(2) {
                        Column(
                            modifier = Modifier.width(320.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            repeat(4) {
                                Row(
                                    modifier = Modifier.padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).shimmerEffect())
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Box(modifier = Modifier.size(width = 140.dp, height = 14.dp).clip(RoundedCornerShape(2.dp)).shimmerEffect())
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Box(modifier = Modifier.size(width = 100.dp, height = 12.dp).clip(RoundedCornerShape(2.dp)).shimmerEffect())
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Shelf Sections (matches PlaylistShelfRow / PlaylistCard)
        items(2) {
            Column(modifier = Modifier.padding(vertical = 12.dp)) {
                // Section Header
                Box(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .size(width = 180.dp, height = 24.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .shimmerEffect()
                )

                // Playlist Row
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    userScrollEnabled = false
                ) {
                    items(4) {
                        // Matching PlaylistCard size (160dp width) and internal padding (8dp)
                        Column(modifier = Modifier.width(160.dp).padding(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .shimmerEffect()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(modifier = Modifier.size(width = 120.dp, height = 13.dp).clip(RoundedCornerShape(2.dp)).shimmerEffect())
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(modifier = Modifier.size(width = 60.dp, height = 11.dp).clip(RoundedCornerShape(2.dp)).shimmerEffect())
                        }
                    }
                }
            }
        }
        
        // 4. Feature Cards (Matching request for 16dp corners and side-by-side layout)
        item {
             Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(modifier = Modifier.weight(1f).height(200.dp).clip(RoundedCornerShape(16.dp)).shimmerEffect())
                Box(modifier = Modifier.weight(1f).height(200.dp).clip(RoundedCornerShape(16.dp)).shimmerEffect())
            }
        }
    }
}
