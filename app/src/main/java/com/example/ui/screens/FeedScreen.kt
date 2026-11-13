package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.VideoItem
import com.example.ui.OmitokUiState
import com.example.ui.components.OmitokBrandHeader
import com.example.ui.components.OmitokPrimaryButton
import com.example.ui.components.OmitokVideoPlayer
import com.example.ui.theme.CardSlate
import com.example.ui.theme.ElectricMagenta
import com.example.ui.theme.ErrorCoral
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.SubtleBorder
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

@Composable
fun FeedScreen(
    uiState: OmitokUiState,
    initialVideoId: String?,
    httpClient: OkHttpClient,
    onRefreshFeed: () -> Unit,
    onOpenCreatorProfile: (String) -> Unit,
    onDeleteVideo: (String) -> Unit,
    onReportVideo: (String, String) -> Unit,
    onNavigateUpload: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = uiState.strings
    val videos = uiState.videos
    val scope = rememberCoroutineScope()

    var isMuted by remember(uiState.playback.startMuted) {
        mutableStateOf(uiState.playback.startMuted)
    }
    var videoToDelete by remember { mutableStateOf<VideoItem?>(null) }
    var videoToReport by remember { mutableStateOf<VideoItem?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBlack)
    ) {
        when {
            uiState.isFeedLoading && videos.isEmpty() -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    CircularProgressIndicator(color = NeonCyan)
                    Spacer(Modifier.height(14.dp))
                    Text(strings.loading, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            videos.isEmpty() -> {
                EmptyOrErrorFeedState(
                    title = if (uiState.feedError != null) strings.playbackErrorTitle else strings.noVideosTitle,
                    body = uiState.feedError ?: strings.noVideosBody,
                    refreshLabel = strings.refreshFeed,
                    uploadLabel = strings.uploadTitle,
                    onRefresh = onRefreshFeed,
                    onUpload = onNavigateUpload,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            else -> {
                val startIndex = remember(initialVideoId, videos) {
                    if (initialVideoId != null) {
                        videos.indexOfFirst { it.id == initialVideoId }.coerceAtLeast(0)
                    } else 0
                }
                val pagerState = rememberPagerState(
                    initialPage = startIndex,
                    pageCount = { videos.size }
                )

                LaunchedEffect(initialVideoId, videos.size) {
                    if (initialVideoId != null) {
                        val idx = videos.indexOfFirst { it.id == initialVideoId }
                        if (idx >= 0 && idx != pagerState.currentPage) {
                            pagerState.scrollToPage(idx)
                        }
                    }
                }

                VerticalPager(
                    state = pagerState,
                    beyondViewportPageCount = 1,
                    key = { index -> videos[index].id.ifBlank { index.toString() } },
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("video_vertical_pager")
                ) { page ->
                    val video = videos[page]
                    val isCurrent = pagerState.currentPage == page
                    val isOwner = uiState.currentUser != null && (
                        video.ownerId == uiState.currentUser.id ||
                            video.authorUsername.equals(uiState.currentUser.username, ignoreCase = true)
                        )

                    Box(modifier = Modifier.fillMaxSize()) {
                        OmitokVideoPlayer(
                            streamUrl = video.resolvedStreamUrl(uiState.baseUrl),
                            isActivePage = isCurrent,
                            autoplay = uiState.playback.autoplay,
                            loop = uiState.playback.loop,
                            isMuted = isMuted,
                            httpClient = httpClient,
                            strings = strings,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Right-edge (or logical end) creator & playback action rail
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 14.dp, bottom = 74.dp)
                        ) {
                            // Previous Video button
                            FeedActionCircle(
                                icon = {
                                    Icon(
                                        Icons.Rounded.KeyboardArrowUp,
                                        contentDescription = strings.prevVideo,
                                        tint = Color.White
                                    )
                                },
                                enabled = page > 0,
                                onClick = {
                                    if (page > 0) {
                                        scope.launch { pagerState.animateScrollToPage(page - 1) }
                                    }
                                },
                                testTag = "prev_video_button"
                            )

                            // Next Video button
                            FeedActionCircle(
                                icon = {
                                    Icon(
                                        Icons.Rounded.KeyboardArrowDown,
                                        contentDescription = strings.nextVideo,
                                        tint = Color.White
                                    )
                                },
                                enabled = page < videos.lastIndex,
                                onClick = {
                                    if (page < videos.lastIndex) {
                                        scope.launch { pagerState.animateScrollToPage(page + 1) }
                                    }
                                },
                                testTag = "next_video_button"
                            )

                            // Mute / Unmute
                            FeedActionCircle(
                                icon = {
                                    Icon(
                                        imageVector = if (isMuted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                                        contentDescription = if (isMuted) strings.unmute else strings.mute,
                                        tint = if (isMuted) ElectricMagenta else NeonCyan
                                    )
                                },
                                onClick = { isMuted = !isMuted },
                                testTag = "mute_toggle_button"
                            )

                            // Report Video
                            FeedActionCircle(
                                icon = {
                                    Icon(
                                        Icons.Rounded.Flag,
                                        contentDescription = strings.reportVideo,
                                        tint = Color.White
                                    )
                                },
                                onClick = { videoToReport = video },
                                testTag = "report_video_button"
                            )

                            // Delete own video
                            if (isOwner) {
                                FeedActionCircle(
                                    icon = {
                                        Icon(
                                            Icons.Rounded.DeleteOutline,
                                            contentDescription = strings.deleteVideo,
                                            tint = ErrorCoral
                                        )
                                    },
                                    onClick = { videoToDelete = video },
                                    testTag = "delete_video_button"
                                )
                            }
                        }

                        // Bottom-start Video Metadata Overlay
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth(0.78f)
                                .padding(start = 16.dp, bottom = 58.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = CardSlate.copy(alpha = 0.72f),
                                border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.4f)),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .clickable { onOpenCreatorProfile(video.authorUsername) }
                                    .testTag("feed_creator_chip")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.img_default_avatar),
                                        contentDescription = video.authorUsername,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                    )
                                    Text(
                                        text = "@${video.authorUsername}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = NeonCyan,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Text(
                                text = video.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (video.description.isNotBlank()) {
                                Text(
                                    text = video.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.85f),
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Visibility,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.75f),
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "${video.views} ${strings.viewsLabel}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                                if (video.publishedAt.isNotBlank()) {
                                    Text(
                                        text = "• ${video.publishedAt.take(10)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.65f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Top Floating Header Bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            OmitokBrandHeader()
            Surface(
                shape = CircleShape,
                color = CardSlate.copy(alpha = 0.72f),
                border = BorderStroke(1.dp, SubtleBorder)
            ) {
                IconButton(
                    onClick = onRefreshFeed,
                    modifier = Modifier.testTag("feed_refresh_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = strings.refreshFeed,
                        tint = NeonCyan
                    )
                }
            }
        }
    }

    // Delete confirmation dialog
    videoToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { videoToDelete = null },
            containerColor = CardSlate,
            title = { Text(strings.deleteConfirmTitle) },
            text = { Text(strings.deleteConfirmBody) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteVideo(target.id)
                        videoToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = ErrorCoral)
                ) {
                    Text(strings.confirm)
                }
            },
            dismissButton = {
                TextButton(onClick = { videoToDelete = null }) {
                    Text(strings.cancel)
                }
            }
        )
    }

    // Report dialog
    videoToReport?.let { target ->
        val reasons = listOf(
            strings.reportReasonSpam,
            strings.reportReasonInappropriate,
            strings.reportReasonCopyright
        )
        AlertDialog(
            onDismissRequest = { videoToReport = null },
            containerColor = CardSlate,
            title = { Text(strings.reportVideo) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        strings.selectReportReason,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    reasons.forEach { reason ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = ObsidianBlack.copy(alpha = 0.55f),
                            border = BorderStroke(1.dp, SubtleBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    onReportVideo(target.id, reason)
                                    videoToReport = null
                                }
                        ) {
                            Text(
                                text = reason,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { videoToReport = null }) {
                    Text(strings.cancel)
                }
            }
        )
    }
}

@Composable
private fun FeedActionCircle(
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    testTag: String,
    enabled: Boolean = true
) {
    Surface(
        shape = CircleShape,
        color = ObsidianBlack.copy(alpha = if (enabled) 0.62f else 0.28f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = if (enabled) 0.22f else 0.08f)),
        modifier = Modifier.size(48.dp)
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .fillMaxSize()
                .testTag(testTag)
        ) {
            icon()
        }
    }
}

@Composable
private fun EmptyOrErrorFeedState(
    title: String,
    body: String,
    refreshLabel: String,
    uploadLabel: String,
    onRefresh: () -> Unit,
    onUpload: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = CardSlate.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, SubtleBorder),
        modifier = modifier
            .padding(24.dp)
            .fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.VideoLibrary,
                contentDescription = null,
                tint = NeonCyan,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OmitokPrimaryButton(
                    text = refreshLabel,
                    onClick = onRefresh,
                    modifier = Modifier.testTag("empty_feed_refresh_button")
                )
                TextButton(
                    onClick = onUpload,
                    modifier = Modifier.height(52.dp)
                ) {
                    Text(uploadLabel, color = NeonCyan)
                }
            }
        }
    }
}
