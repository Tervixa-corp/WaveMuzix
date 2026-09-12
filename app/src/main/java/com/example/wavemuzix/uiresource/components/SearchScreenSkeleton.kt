package com.example.wavemuzix.uiresource.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * Skeleton UI for Search Screen results.
 */
@Composable
fun SearchScreenSkeleton() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = 12.dp,
            bottom = 190.dp
        ),
        userScrollEnabled = false
    ) {
        // Artists Section
        item {
            Column {
                Box(modifier = Modifier.size(width = 80.dp, height = 20.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    userScrollEnabled = false
                ) {
                    items(3) {
                        Column(
                            modifier = Modifier.width(120.dp).padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(modifier = Modifier.size(104.dp).clip(CircleShape).shimmerEffect())
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(modifier = Modifier.size(width = 80.dp, height = 14.dp).clip(RoundedCornerShape(2.dp)).shimmerEffect())
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(modifier = Modifier.size(width = 40.dp, height = 11.dp).clip(RoundedCornerShape(2.dp)).shimmerEffect())
                        }
                    }
                }
            }
        }

        // Playlists Section
        item {
            Column {
                Box(modifier = Modifier.size(width = 100.dp, height = 20.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    userScrollEnabled = false
                ) {
                    items(3) {
                        Column(modifier = Modifier.width(120.dp).padding(8.dp)) {
                            Box(modifier = Modifier.size(104.dp).clip(RoundedCornerShape(12.dp)).shimmerEffect())
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(modifier = Modifier.size(width = 90.dp, height = 14.dp).clip(RoundedCornerShape(2.dp)).shimmerEffect())
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(modifier = Modifier.size(width = 50.dp, height = 11.dp).clip(RoundedCornerShape(2.dp)).shimmerEffect())
                        }
                    }
                }
            }
        }

        // Songs Section
        item {
            Box(modifier = Modifier.size(width = 70.dp, height = 20.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
        }

        items(6) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(76.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .shimmerEffect()
            ) {}
        }
    }
}
