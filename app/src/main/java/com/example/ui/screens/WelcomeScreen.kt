package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.i18n.AppLanguage
import com.example.ui.OmitokUiState
import com.example.ui.components.LanguageTogglePill
import com.example.ui.components.OmitokBrandHeader
import com.example.ui.components.OmitokPrimaryButton
import com.example.ui.components.OmitokSecondaryButton
import com.example.ui.components.ServerStatusChip
import com.example.ui.theme.CardSlate
import com.example.ui.theme.ElectricMagenta
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.ObsidianBlack

@Composable
fun WelcomeScreen(
    uiState: OmitokUiState,
    onLanguageChange: (AppLanguage) -> Unit,
    onRefreshServer: () -> Unit,
    onNavigateLogin: () -> Unit,
    onNavigateRegister: () -> Unit,
    onWatchFeed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = uiState.strings

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBlack)
    ) {
        // Ambient futuristic radial glow
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            ElectricMagenta.copy(alpha = 0.16f),
                            NeonCyan.copy(alpha = 0.10f),
                            ObsidianBlack
                        )
                    )
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 18.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OmitokBrandHeader()
                    LanguageTogglePill(
                        currentLanguage = uiState.language,
                        onLanguageChange = onLanguageChange
                    )
                }

                Spacer(Modifier.height(20.dp))

                // Hero Cinema Card
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = CardSlate,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Image(
                            painter = painterResource(id = R.drawable.img_welcome_hero),
                            contentDescription = strings.tagline,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(28.dp))
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        0.0f to Color.Transparent,
                                        1.0f to ObsidianBlack.copy(alpha = 0.85f)
                                    )
                                )
                        )
                        ServerStatusChip(
                            health = uiState.serverHealth,
                            isChecking = uiState.isCheckingServer,
                            strings = strings,
                            onRefreshClick = onRefreshServer,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(14.dp)
                        )
                    }
                }

                Spacer(Modifier.height(28.dp))

                Text(
                    text = strings.tagline,
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(10.dp))

                Text(
                    text = strings.welcomeSubtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(Modifier.height(32.dp))

                OmitokPrimaryButton(
                    text = strings.signIn,
                    onClick = onNavigateLogin,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("welcome_login_button")
                )

                Spacer(Modifier.height(12.dp))

                OmitokSecondaryButton(
                    text = strings.createAccount,
                    onClick = onNavigateRegister,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("welcome_register_button")
                )

                Spacer(Modifier.height(14.dp))

                TextButton(
                    onClick = onWatchFeed,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("welcome_guest_feed_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayCircle,
                        contentDescription = null,
                        tint = NeonCyan
                    )
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    Text(
                        text = strings.watchAsGuest,
                        style = MaterialTheme.typography.labelLarge,
                        color = NeonCyan
                    )
                }
            }
        }
    }
}
