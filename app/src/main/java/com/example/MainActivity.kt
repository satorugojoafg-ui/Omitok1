package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.AddBox
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.ui.OmitokViewModel
import com.example.ui.components.OmitokPrismEmblem
import com.example.ui.navigation.FeedRoute
import com.example.ui.navigation.LoginRoute
import com.example.ui.navigation.ProfileRoute
import com.example.ui.navigation.RegisterRoute
import com.example.ui.navigation.SearchRoute
import com.example.ui.navigation.SettingsRoute
import com.example.ui.navigation.UploadRoute
import com.example.ui.navigation.WelcomeRoute
import com.example.ui.screens.FeedScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.RegisterScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.UploadScreen
import com.example.ui.screens.WelcomeScreen
import com.example.ui.theme.DeepSpace
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.OmitokTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: OmitokViewModel = viewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            OmitokTheme(language = uiState.language) {
                if (uiState.isBootstrapping) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(ObsidianBlack)
                    ) {
                        OmitokPrismEmblem(size = 72.dp)
                        CircularProgressIndicator(
                            color = NeonCyan,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 64.dp)
                        )
                    }
                } else {
                    OmitokAppShell(viewModel = viewModel)
                }
            }
        }
    }
}

private data class TopLevelTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val route: Any,
    val testTag: String
)

@Composable
private fun OmitokAppShell(viewModel: OmitokViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val strings = uiState.strings
    val context = LocalContext.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.snackbarMessage) {
        val msg = uiState.snackbarMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = navBackStackEntry?.destination

    val isAuthOrWelcome = currentDest == null ||
        currentDest.hasRoute<WelcomeRoute>() ||
        currentDest.hasRoute<LoginRoute>() ||
        currentDest.hasRoute<RegisterRoute>()

    val tabs = remember(strings) {
        listOf(
            TopLevelTab(
                label = strings.navFeed,
                selectedIcon = Icons.Rounded.Home,
                unselectedIcon = Icons.Outlined.Home,
                route = FeedRoute(),
                testTag = "nav_tab_feed"
            ),
            TopLevelTab(
                label = strings.navSearch,
                selectedIcon = Icons.Rounded.Search,
                unselectedIcon = Icons.Outlined.Search,
                route = SearchRoute,
                testTag = "nav_tab_search"
            ),
            TopLevelTab(
                label = strings.navUpload,
                selectedIcon = Icons.Rounded.AddBox,
                unselectedIcon = Icons.Outlined.AddBox,
                route = UploadRoute,
                testTag = "nav_tab_upload"
            ),
            TopLevelTab(
                label = strings.navProfile,
                selectedIcon = Icons.Rounded.Person,
                unselectedIcon = Icons.Outlined.Person,
                route = ProfileRoute(username = null),
                testTag = "nav_tab_profile"
            ),
            TopLevelTab(
                label = strings.navSettings,
                selectedIcon = Icons.Rounded.Settings,
                unselectedIcon = Icons.Outlined.Settings,
                route = SettingsRoute,
                testTag = "nav_tab_settings"
            )
        )
    }

    fun isTabSelected(tab: TopLevelTab): Boolean = when (tab.route) {
        is FeedRoute -> currentDest?.hasRoute<FeedRoute>() == true
        is SearchRoute -> currentDest?.hasRoute<SearchRoute>() == true
        is UploadRoute -> currentDest?.hasRoute<UploadRoute>() == true
        is ProfileRoute -> currentDest?.hasRoute<ProfileRoute>() == true
        is SettingsRoute -> currentDest?.hasRoute<SettingsRoute>() == true
        else -> false
    }

    fun navigateTopLevel(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val startDestination: Any = remember {
        if (uiState.isLoggedIn) FeedRoute() else WelcomeRoute
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = ObsidianBlack,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!isAuthOrWelcome && !useNavigationRail) {
                    NavigationBar(
                        containerColor = DeepSpace.copy(alpha = 0.96f),
                        tonalElevation = 0.dp
                    ) {
                        tabs.forEach { tab ->
                            val selected = isTabSelected(tab)
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTopLevel(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = {
                                    Text(
                                        text = tab.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = NeonCyan,
                                    selectedTextColor = NeonCyan,
                                    indicatorColor = NeonCyan.copy(alpha = 0.16f),
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (!isAuthOrWelcome && useNavigationRail) {
                    NavigationRail(
                        containerColor = DeepSpace,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        tabs.forEach { tab ->
                            val selected = isTabSelected(tab)
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopLevel(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = {
                                    Text(
                                        text = tab.label,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = NeonCyan,
                                    selectedTextColor = NeonCyan,
                                    indicatorColor = NeonCyan.copy(alpha = 0.16f),
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = startDestination,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    composable<WelcomeRoute> {
                        WelcomeScreen(
                            uiState = uiState,
                            onLanguageChange = viewModel::setLanguage,
                            onRefreshServer = viewModel::checkServerStatus,
                            onNavigateLogin = { navController.navigate(LoginRoute) },
                            onNavigateRegister = { navController.navigate(RegisterRoute) },
                            onWatchFeed = {
                                navController.navigate(FeedRoute()) {
                                    popUpTo<WelcomeRoute> { inclusive = true }
                                }
                            }
                        )
                    }

                    composable<LoginRoute> {
                        LoginScreen(
                            uiState = uiState,
                            onLanguageChange = viewModel::setLanguage,
                            onBack = { navController.popBackStack() },
                            onSubmitLogin = { username, password ->
                                viewModel.login(username, password) {
                                    navController.navigate(FeedRoute()) {
                                        popUpTo<WelcomeRoute> { inclusive = true }
                                    }
                                }
                            },
                            onSwitchToRegister = {
                                navController.navigate(RegisterRoute) {
                                    popUpTo<LoginRoute> { inclusive = true }
                                }
                            },
                            onClearError = viewModel::clearAuthError
                        )
                    }

                    composable<RegisterRoute> {
                        RegisterScreen(
                            uiState = uiState,
                            onLanguageChange = viewModel::setLanguage,
                            onBack = { navController.popBackStack() },
                            onSubmitRegister = { username, password, confirm ->
                                viewModel.register(username, password, confirm) {
                                    navController.navigate(FeedRoute()) {
                                        popUpTo<WelcomeRoute> { inclusive = true }
                                    }
                                }
                            },
                            onSwitchToLogin = {
                                navController.navigate(LoginRoute) {
                                    popUpTo<RegisterRoute> { inclusive = true }
                                }
                            },
                            onClearError = viewModel::clearAuthError
                        )
                    }

                    composable<FeedRoute> { backStackEntry ->
                        val route = backStackEntry.toRoute<FeedRoute>()
                        FeedScreen(
                            uiState = uiState,
                            initialVideoId = route.initialVideoId,
                            httpClient = viewModel.streamingHttpClient,
                            onRefreshFeed = viewModel::loadVideos,
                            onOpenCreatorProfile = { username ->
                                navController.navigate(ProfileRoute(username = username))
                            },
                            onDeleteVideo = viewModel::deleteVideo,
                            onReportVideo = viewModel::reportVideo,
                            onNavigateUpload = { navigateTopLevel(UploadRoute) }
                        )
                    }

                    composable<SearchRoute> {
                        SearchScreen(
                            uiState = uiState,
                            onQueryChange = viewModel::updateSearchQuery,
                            onSubmitSearch = viewModel::submitSearch,
                            onClearRecents = viewModel::clearRecentSearches,
                            onOpenVideo = { videoId ->
                                navController.navigate(FeedRoute(initialVideoId = videoId))
                            },
                            onOpenProfile = { username ->
                                navController.navigate(ProfileRoute(username = username))
                            }
                        )
                    }

                    composable<UploadRoute> {
                        UploadScreen(
                            uiState = uiState,
                            onVideoPicked = { uri ->
                                viewModel.onVideoPicked(context.contentResolver, uri)
                            },
                            onSubmitUpload = { title, description, publishNow ->
                                viewModel.uploadVideo(
                                    contentResolver = context.contentResolver,
                                    title = title,
                                    description = description,
                                    publishImmediately = publishNow,
                                    onSuccess = {
                                        navigateTopLevel(FeedRoute())
                                    }
                                )
                            },
                            onNavigateLogin = { navController.navigate(LoginRoute) }
                        )
                    }

                    composable<ProfileRoute> { backStackEntry ->
                        val route = backStackEntry.toRoute<ProfileRoute>()
                        ProfileScreen(
                            uiState = uiState,
                            requestedUsername = route.username,
                            onLoadProfile = viewModel::loadProfile,
                            onUpdateProfile = viewModel::updateOwnProfile,
                            onSignOut = {
                                viewModel.logout {
                                    navController.navigate(WelcomeRoute) {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            },
                            onNavigateLogin = { navController.navigate(LoginRoute) },
                            onPlayVideo = { videoId ->
                                navController.navigate(FeedRoute(initialVideoId = videoId))
                            },
                            onDeleteVideo = viewModel::deleteVideo,
                            onBack = if (route.username != null) {
                                { navController.popBackStack() }
                            } else null
                        )
                    }

                    composable<SettingsRoute> {
                        SettingsScreen(
                            uiState = uiState,
                            onLanguageChange = viewModel::setLanguage,
                            onUpdatePlayback = viewModel::updatePlaybackPreferences,
                            onUpdateBaseUrl = viewModel::updateBaseUrl,
                            onTestServer = viewModel::checkServerStatus,
                            onProbeEndpoints = viewModel::probeEndpoints
                        )
                    }
                }
            }
        }
    }
}
