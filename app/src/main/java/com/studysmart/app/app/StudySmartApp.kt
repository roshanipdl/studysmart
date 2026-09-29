package com.studysmart.app.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.studysmart.app.core.navigation.AppNavHost
import com.studysmart.app.core.navigation.AppRoutes
import com.studysmart.app.core.navigation.navigateTopLevel
import com.studysmart.app.core.ui.StudyBottomNavigation

@Composable
fun StudySmartApp(
    startRoute: String = AppRoutes.HOME,
    container: AppContainer? = null,
) {
    val context = LocalContext.current
    val appContainer = container ?: remember(context) {
        (context.applicationContext as StudySmartApplication).container
    }
    val startupViewModel: StartupViewModel = viewModel(
        factory = appContainer.viewModelFactory { graph ->
            StartupViewModel(
                cleanupCoordinator = graph.startupCleanupCoordinator,
                dispatcher = graph.dispatchers.main,
            )
        },
    )
    val startupState by startupViewModel.uiState.collectAsStateWithLifecycle()

    if (!startupState.isReady) {
        StartupStateContent(
            state = startupState,
            onRetry = startupViewModel::retry,
        )
        return
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomNavigation = AppRoutes.shouldShowBottomNavigation(currentRoute)
    var pendingTopLevelDestination by remember { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomNavigation) {
                StudyBottomNavigation(
                    currentRoute = currentRoute,
                    onDestinationSelected = { destination ->
                        if (currentRoute == AppRoutes.PREPARE_SESSION || currentRoute == AppRoutes.MOCK_TEST) {
                            pendingTopLevelDestination = destination
                        } else {
                            pendingTopLevelDestination = null
                            navController.navigateTopLevel(destination)
                        }
                    },
                )
            }
        },
    ) { contentPadding ->
        AppNavHost(
            navController = navController,
            container = appContainer,
            startRoute = startRoute,
            pendingTopLevelDestination = pendingTopLevelDestination,
            onPendingTopLevelDestinationConsumed = {
                pendingTopLevelDestination = null
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .testTag("app_nav_host"),
        )
    }
}

@Composable
private fun StartupStateContent(
    state: StartupUiState,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(if (state.isLoading) "startup_loading" else "startup_error"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.isLoading) {
                CircularProgressIndicator()
                Text("Preparing StudySmart", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text("Something went wrong", style = MaterialTheme.typography.titleLarge)
                Text(
                    state.errorMessage ?: "Unable to initialize StudySmart.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onRetry) {
                    Text("Retry")
                }
            }
        }
    }
}
