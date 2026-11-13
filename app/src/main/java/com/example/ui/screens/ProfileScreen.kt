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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.ui.components.OmitokPrimaryButton
import com.example.ui.components.OmitokSecondaryButton
import com.example.ui.theme.CardSlate
import com.example.ui.theme.ErrorCoral
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.WarningAmber

@Composable
fun ProfileScreen(
    uiState: OmitokUiState,
    requestedUsername: String?,
    onLoadProfile: (String?) -> Unit,
    onUpdateProfile: (displayName: String, bio: String, onDone: () -> Unit) -> Unit,
    onSignOut: () -> Unit,
    onNavigateLogin: () -> Unit,
    onPlayVideo: (String) -> Unit,
    onDeleteVideo: (String) -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val strings = uiState.strings
    var showEditDialog by remember { mutableStateOf(false) }
    var videoToDelete by remember { mutableStateOf<VideoItem?>(null) }

    LaunchedEffect(requestedUsername, uiState.currentUser?.username) {
        if (requestedUsername != null || uiState.currentUser != null) {
            onLoadProfile(requestedUsername)
        }
    }

    val isOwnProfile = requestedUsername == null ||
        (uiState.currentUser != null && requestedUsername.equals(uiState.currentUser.username, ignoreCase = true))

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBlack)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
                    .fillMaxSize()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (onBack != null && requestedUsername != null) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = strings.back
                                )
                            }
                        }
                        Text(
                            text = strings.profileTitle,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (isOwnProfile && uiState.isLoggedIn) {
                        IconButton(
                            onClick = onSignOut,
                            modifier = Modifier.testTag("profile_logout_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Logout,
                                contentDescription = strings.signOut,
                                tint = ErrorCoral
                            )
                        }
                    }
                }

                if (requestedUsername == null && !uiState.isLoggedIn) {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = CardSlate,
                        border = BorderStroke(1.dp, SubtleBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 20.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(44.dp)
                            )
                            Text(
                                text = strings.loginRequiredTitle,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = strings.loginRequiredBody,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            OmitokPrimaryButton(
                                text = strings.signIn,
                                onClick = onNavigateLogin,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("profile_login_button")
                            )
                        }
                    }
                } else if (uiState.isProfileLoading && uiState.viewedProfile == null) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        CircularProgressIndicator(color = NeonCyan)
                    }
                } else {
                    val profile = uiState.viewedProfile
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (profile != null) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(24.dp),
                                    color = CardSlate,
                                    border = BorderStroke(1.dp, SubtleBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.padding(20.dp)
                                    ) {
                                        Image(
                                            painter = painterResource(id = R.drawable.img_default_avatar),
                                            contentDescription = profile.username,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(84.dp)
                                                .clip(CircleShape)
                                        )
                                        Text(
                                            text = profile.displayName.ifBlank { profile.username },
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "@${profile.username}",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = NeonCyan
                                        )
                                        if (profile.bio.isNotBlank()) {
                                            Text(
                                                text = profile.bio,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center
                                            )
                                        }

                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                                            modifier = Modifier.padding(top = 6.dp)
                                        ) {
                                            val videosCount = maxOf(
                                                profile.videoCount,
                                                uiState.viewedProfileVideos.size
                                            )
                                            val viewsSum = maxOf(
                                                profile.totalViews,
                                                uiState.viewedProfileVideos.sumOf { it.views }
                                            )
                                            ProfileStatItem(videosCount.toString(), strings.videosCountLabel)
                                            ProfileStatItem(viewsSum.toString(), strings.totalViewsLabel)
                                        }

                                        if (isOwnProfile && uiState.isLoggedIn) {
                                            Spacer(Modifier.height(4.dp))
                                            OmitokSecondaryButton(
                                                text = strings.editProfile,
                                                onClick = { showEditDialog = true },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("profile_edit_button")
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        uiState.profileInfoMessage?.let { note ->
                            item {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = WarningAmber.copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = note,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = WarningAmber,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }
                        }

                        uiState.profileError?.let { err ->
                            item {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = ErrorCoral.copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, ErrorCoral.copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = err,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = ErrorCoral,
                                        modifier = Modifier.padding(14.dp)
                                    )
                                }
                            }
                        }

                        if (uiState.viewedProfileVideos.isEmpty()) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = CardSlate.copy(alpha = 0.7f),
                                    border = BorderStroke(1.dp, SubtleBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = strings.userVideosEmpty,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(24.dp)
                                    )
                                }
                            }
                        } else {
                            items(
                                items = uiState.viewedProfileVideos,
                                key = { it.id.ifBlank { it.title } }
                            ) { video ->
                                ProfileVideoCard(
                                    video = video,
                                    isOwner = isOwnProfile && uiState.isLoggedIn,
                                    viewsLabel = strings.viewsLabel,
                                    deleteLabel = strings.deleteVideo,
                                    onPlay = { onPlayVideo(video.id) },
                                    onDelete = { videoToDelete = video }
                                )
                            }
                        }

                        item { Spacer(Modifier.height(92.dp)) }
                    }
                }
            }
        }
    }

    if (showEditDialog && uiState.viewedProfile != null) {
        var displayName by remember { mutableStateOf(uiState.viewedProfile.displayName) }
        var bio by remember { mutableStateOf(uiState.viewedProfile.bio) }
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            containerColor = CardSlate,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.Edit, contentDescription = null, tint = NeonCyan)
                    Text(strings.editProfile)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text(strings.displayNameLabel) },
                        singleLine = true,
                        colors = omitokFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = bio,
                        onValueChange = { bio = it },
                        label = { Text(strings.bioLabel) },
                        minLines = 2,
                        maxLines = 4,
                        colors = omitokFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onUpdateProfile(displayName, bio) { showEditDialog = false }
                    }
                ) {
                    Text(strings.saveChanges, color = NeonCyan)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text(strings.cancel)
                }
            }
        )
    }

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
}

@Composable
private fun ProfileStatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = NeonCyan
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ProfileVideoCard(
    video: VideoItem,
    isOwner: Boolean,
    viewsLabel: String,
    deleteLabel: String,
    onPlay: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = CardSlate,
        border = BorderStroke(1.dp, SubtleBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onPlay)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(width = 76.dp, height = 96.dp)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_video_placeholder),
                    contentDescription = video.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = NeonCyan,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (video.description.isNotBlank()) {
                    Text(
                        text = video.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Visibility,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "${video.views} $viewsLabel",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isOwner) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = deleteLabel,
                        tint = ErrorCoral
                    )
                }
            }
        }
    }
}
