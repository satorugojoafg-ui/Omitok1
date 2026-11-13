package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.OmitokUiState
import com.example.ui.components.OmitokPrimaryButton
import com.example.ui.components.OmitokSecondaryButton
import com.example.ui.theme.CardSlate
import com.example.ui.theme.ErrorCoral
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.SuccessEmerald
import kotlin.math.roundToInt

@Composable
fun UploadScreen(
    uiState: OmitokUiState,
    onVideoPicked: (android.net.Uri) -> Unit,
    onSubmitUpload: (title: String, description: String, publishNow: Boolean) -> Unit,
    onNavigateLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val strings = uiState.strings

    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var publishImmediately by rememberSaveable { mutableStateOf(true) }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                onVideoPicked(uri)
            }
        }
    )

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
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = strings.uploadTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                if (!uiState.isLoggedIn) {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = CardSlate,
                        border = BorderStroke(1.dp, SubtleBorder),
                        modifier = Modifier.fillMaxWidth()
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
                                    .testTag("upload_login_prompt_button")
                            )
                        }
                    }
                } else {
                    // Video Selection Card (Zero-permission Photo Picker)
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = CardSlate,
                        border = BorderStroke(
                            1.dp,
                            if (uiState.pickedVideo != null) NeonCyan.copy(alpha = 0.55f) else SubtleBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Icon(
                                imageVector = if (uiState.pickedVideo != null) Icons.Rounded.Movie else Icons.Rounded.CloudUpload,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(42.dp)
                            )

                            val picked = uiState.pickedVideo
                            if (picked != null) {
                                Text(
                                    text = picked.fileName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center
                                )
                                val sizeMb = if (picked.sizeBytes > 0) {
                                    "%.2f MB".format(picked.sizeBytes / (1024f * 1024f))
                                } else "?"
                                Text(
                                    text = "${picked.mimeType} • $sizeMb",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Text(
                                    text = strings.maxFileLimitNote,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }

                            OmitokSecondaryButton(
                                text = if (picked != null) strings.changeVideoButton else strings.pickVideoButton,
                                onClick = {
                                    videoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                    )
                                },
                                enabled = !uiState.isUploading,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("pick_video_button")
                            )
                        }
                    }

                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text(strings.videoTitleLabel) },
                        singleLine = true,
                        enabled = !uiState.isUploading,
                        colors = omitokFieldColors(),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("upload_title_input")
                    )

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text(strings.videoDescLabel) },
                        minLines = 3,
                        maxLines = 5,
                        enabled = !uiState.isUploading,
                        colors = omitokFieldColors(),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("upload_description_input")
                    )

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = CardSlate,
                        border = BorderStroke(1.dp, SubtleBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = strings.publishNowLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = publishImmediately,
                                onCheckedChange = { publishImmediately = it },
                                enabled = !uiState.isUploading,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = ObsidianBlack,
                                    checkedTrackColor = NeonCyan
                                ),
                                modifier = Modifier.testTag("upload_publish_switch")
                            )
                        }
                    }

                    if (uiState.isUploading) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(strings.uploadingProgress, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = "${(uiState.uploadProgress * 100f).roundToInt()}%",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = NeonCyan
                                )
                            }
                            LinearProgressIndicator(
                                progress = { uiState.uploadProgress },
                                color = NeonCyan,
                                trackColor = CardSlate,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                            )
                        }
                    }

                    uiState.uploadError?.let { err ->
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

                    uiState.uploadSuccessMessage?.let { successTitle ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = SuccessEmerald.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, SuccessEmerald.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "${strings.statusPublished}: $successTitle",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SuccessEmerald,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }

                    OmitokPrimaryButton(
                        text = strings.uploadActionButton,
                        isLoading = uiState.isUploading,
                        enabled = uiState.pickedVideo != null && !uiState.isUploading,
                        onClick = {
                            onSubmitUpload(title, description, publishImmediately)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("upload_submit_button")
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = strings.storageArchitectureNote,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(88.dp))
            }
        }
    }
}
