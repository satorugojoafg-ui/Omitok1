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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.i18n.AppLanguage
import com.example.ui.OmitokUiState
import com.example.ui.components.LanguageTogglePill
import com.example.ui.components.OmitokPrismEmblem
import com.example.ui.components.OmitokPrimaryButton
import com.example.ui.theme.CardSlate
import com.example.ui.theme.CyberViolet
import com.example.ui.theme.ErrorCoral
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.WarningAmber

@Composable
fun LoginScreen(
    uiState: OmitokUiState,
    onLanguageChange: (AppLanguage) -> Unit,
    onBack: () -> Unit,
    onSubmitLogin: (username: String, password: String) -> Unit,
    onSwitchToRegister: () -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = uiState.strings
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { onClearError() }

    AuthScaffold(
        title = strings.loginTitle,
        language = uiState.language,
        backLabel = strings.back,
        onLanguageChange = onLanguageChange,
        onBack = onBack,
        modifier = modifier
    ) {
        OutlinedTextField(
            value = username,
            onValueChange = {
                username = it
                if (uiState.authError != null) onClearError()
            },
            label = { Text(strings.username) },
            supportingText = { Text(strings.usernameHint) },
            leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Next
            ),
            colors = omitokFieldColors(),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("login_username_input")
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                if (uiState.authError != null) onClearError()
            },
            label = { Text(strings.password) },
            supportingText = { Text(strings.passwordHint) },
            leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = null
                    )
                }
            },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { onSubmitLogin(username, password) }
            ),
            colors = omitokFieldColors(),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("login_password_input")
        )

        AuthErrorAndRateLimitBanner(
            error = uiState.authError,
            rateLimitSeconds = uiState.rateLimitSeconds
        )

        Spacer(Modifier.height(20.dp))

        OmitokPrimaryButton(
            text = strings.signIn,
            isLoading = uiState.authLoading,
            enabled = uiState.rateLimitSeconds == 0,
            onClick = { onSubmitLogin(username, password) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("login_submit_button")
        )

        Spacer(Modifier.height(12.dp))

        TextButton(
            onClick = onSwitchToRegister,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("switch_to_register_button")
        ) {
            Text(strings.noAccountYet, color = NeonCyan)
        }
    }
}

@Composable
fun RegisterScreen(
    uiState: OmitokUiState,
    onLanguageChange: (AppLanguage) -> Unit,
    onBack: () -> Unit,
    onSubmitRegister: (username: String, password: String, confirm: String) -> Unit,
    onSwitchToLogin: () -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = uiState.strings
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { onClearError() }

    AuthScaffold(
        title = strings.registerTitle,
        language = uiState.language,
        backLabel = strings.back,
        onLanguageChange = onLanguageChange,
        onBack = onBack,
        modifier = modifier
    ) {
        OutlinedTextField(
            value = username,
            onValueChange = {
                username = it
                if (uiState.authError != null) onClearError()
            },
            label = { Text(strings.username) },
            supportingText = { Text(strings.usernameHint) },
            leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Next
            ),
            colors = omitokFieldColors(),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("register_username_input")
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                if (uiState.authError != null) onClearError()
            },
            label = { Text(strings.password) },
            supportingText = { Text(strings.passwordHint) },
            leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = null
                    )
                }
            },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next
            ),
            colors = omitokFieldColors(),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("register_password_input")
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = confirmPassword,
            onValueChange = {
                confirmPassword = it
                if (uiState.authError != null) onClearError()
            },
            label = { Text(strings.confirmPassword) },
            leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { onSubmitRegister(username, password, confirmPassword) }
            ),
            colors = omitokFieldColors(),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("register_confirm_password_input")
        )

        AuthErrorAndRateLimitBanner(
            error = uiState.authError,
            rateLimitSeconds = uiState.rateLimitSeconds
        )

        Spacer(Modifier.height(20.dp))

        OmitokPrimaryButton(
            text = strings.createAccount,
            isLoading = uiState.authLoading,
            enabled = uiState.rateLimitSeconds == 0,
            onClick = { onSubmitRegister(username, password, confirmPassword) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("register_submit_button")
        )

        Spacer(Modifier.height(12.dp))

        TextButton(
            onClick = onSwitchToLogin,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("switch_to_login_button")
        ) {
            Text(strings.alreadyHaveAccount, color = NeonCyan)
        }
    }
}

@Composable
private fun AuthScaffold(
    title: String,
    language: AppLanguage,
    backLabel: String,
    onLanguageChange: (AppLanguage) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(ObsidianBlack, CyberViolet.copy(alpha = 0.14f), ObsidianBlack)
                )
            )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("auth_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = backLabel,
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    LanguageTogglePill(
                        currentLanguage = language,
                        onLanguageChange = onLanguageChange
                    )
                }

                Spacer(Modifier.height(20.dp))

                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = CardSlate.copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, SubtleBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OmitokPrismEmblem(size = 36.dp)
                            Text(
                                text = title,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(Modifier.height(24.dp))
                        content()
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthErrorAndRateLimitBanner(
    error: String?,
    rateLimitSeconds: Int
) {
    if (error != null || rateLimitSeconds > 0) {
        Spacer(Modifier.height(14.dp))
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = (if (rateLimitSeconds > 0) WarningAmber else ErrorCoral).copy(alpha = 0.14f),
            border = BorderStroke(
                1.dp,
                (if (rateLimitSeconds > 0) WarningAmber else ErrorCoral).copy(alpha = 0.45f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            val text = buildString {
                if (!error.isNullOrBlank()) append(error)
                if (rateLimitSeconds > 0) {
                    if (isNotEmpty()) append(" • ")
                    append("${rateLimitSeconds}s")
                }
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (rateLimitSeconds > 0) WarningAmber else ErrorCoral,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

@Composable
fun omitokFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = NeonCyan,
    unfocusedBorderColor = SubtleBorder,
    focusedLabelColor = NeonCyan,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    cursorColor = NeonCyan,
    focusedContainerColor = ObsidianBlack.copy(alpha = 0.45f),
    unfocusedContainerColor = ObsidianBlack.copy(alpha = 0.30f)
)
