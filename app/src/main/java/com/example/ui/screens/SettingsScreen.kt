package com.example.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.i18n.AppLanguage
import com.example.ui.OmitokUiState
import com.example.ui.components.LanguageTogglePill
import com.example.ui.components.OmitokPrimaryButton
import com.example.ui.components.OmitokSecondaryButton
import com.example.ui.components.ServerStatusChip
import com.example.ui.theme.CardSlate
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.SuccessEmerald
import com.example.ui.theme.WarningAmber

@Composable
fun SettingsScreen(
    uiState: OmitokUiState,
    onLanguageChange: (AppLanguage) -> Unit,
    onUpdatePlayback: (autoplay: Boolean, loop: Boolean, startMuted: Boolean) -> Unit,
    onUpdateBaseUrl: (String) -> Unit,
    onTestServer: () -> Unit,
    onProbeEndpoints: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = uiState.strings
    var editableUrl by remember(uiState.baseUrl) { mutableStateOf(uiState.baseUrl) }

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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
            ) {
                Text(
                    text = strings.settingsTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )

                // Language Section
                SettingsSectionCard(
                    title = strings.languageSection,
                    icon = { Icon(Icons.Rounded.Tune, contentDescription = null, tint = NeonCyan) }
                ) {
                    LanguageTogglePill(
                        currentLanguage = uiState.language,
                        onLanguageChange = onLanguageChange
                    )
                }

                // Playback Section
                SettingsSectionCard(
                    title = strings.playbackSection,
                    icon = { Icon(Icons.Rounded.Tune, contentDescription = null, tint = NeonCyan) }
                ) {
                    SettingsToggleRow(
                        label = strings.autoplayLabel,
                        checked = uiState.playback.autoplay,
                        onCheckedChange = {
                            onUpdatePlayback(it, uiState.playback.loop, uiState.playback.startMuted)
                        }
                    )
                    SettingsToggleRow(
                        label = strings.loopLabel,
                        checked = uiState.playback.loop,
                        onCheckedChange = {
                            onUpdatePlayback(uiState.playback.autoplay, it, uiState.playback.startMuted)
                        }
                    )
                    SettingsToggleRow(
                        label = strings.startMutedLabel,
                        checked = uiState.playback.startMuted,
                        onCheckedChange = {
                            onUpdatePlayback(uiState.playback.autoplay, uiState.playback.loop, it)
                        }
                    )
                }

                // Server & Endpoint Diagnostics
                SettingsSectionCard(
                    title = strings.serverSection,
                    icon = { Icon(Icons.Rounded.Dns, contentDescription = null, tint = NeonCyan) }
                ) {
                    ServerStatusChip(
                        health = uiState.serverHealth,
                        isChecking = uiState.isCheckingServer,
                        strings = strings,
                        onRefreshClick = onTestServer
                    )

                    OutlinedTextField(
                        value = editableUrl,
                        onValueChange = { editableUrl = it },
                        label = { Text(strings.serverUrlLabel) },
                        singleLine = true,
                        colors = omitokFieldColors(),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_base_url_input")
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OmitokSecondaryButton(
                            text = strings.saveUrl,
                            onClick = { onUpdateBaseUrl(editableUrl) },
                            modifier = Modifier.weight(1f)
                        )
                        OmitokPrimaryButton(
                            text = strings.probeEndpoints,
                            isLoading = uiState.isProbingEndpoints,
                            onClick = onProbeEndpoints,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_probe_endpoints_button")
                        )
                    }

                    if (uiState.isProbingEndpoints) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = NeonCyan)
                            Text(strings.loading, style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    if (uiState.endpointProbes.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            uiState.endpointProbes.forEach { probe ->
                                val statusColor = if (probe.isReady) SuccessEmerald else WarningAmber
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = ObsidianBlack.copy(alpha = 0.5f),
                                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.35f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(statusColor)
                                            )
                                            Text(
                                                text = "${probe.method} ${probe.path}",
                                                fontFamily = SpaceGroteskFontFamily,
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                        }
                                        Text(
                                            text = probe.note,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = statusColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Storage Architecture Card
                SettingsSectionCard(
                    title = strings.storageSection,
                    icon = { Icon(Icons.Rounded.CloudDone, contentDescription = null, tint = NeonCyan) }
                ) {
                    Text(
                        text = strings.storageArchitectureNote,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Security Card
                SettingsSectionCard(
                    title = strings.securitySection,
                    icon = { Icon(Icons.Rounded.Security, contentDescription = null, tint = NeonCyan) }
                ) {
                    Text(
                        text = strings.securityNote,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(92.dp))
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    icon: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = CardSlate,
        border = BorderStroke(1.dp, SubtleBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                icon()
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            content()
        }
    }
}

@Composable
private fun SettingsToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = ObsidianBlack,
                checkedTrackColor = NeonCyan
            )
        )
    }
}
