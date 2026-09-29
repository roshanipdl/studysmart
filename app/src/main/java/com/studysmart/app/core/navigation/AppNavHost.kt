package com.studysmart.app.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.studysmart.app.app.AppContainer
import com.studysmart.app.core.ui.MissingContentScreen
import com.studysmart.app.core.ui.PlaceholderScreen
import com.studysmart.app.core.model.PreparationOriginType
import com.studysmart.app.core.model.PreparationScopeType
import com.studysmart.app.core.model.TestAttemptOriginType
import com.studysmart.app.feature.home.HomeScreen
import com.studysmart.app.feature.home.HomeViewModel
import com.studysmart.app.feature.academic.AcademicPeriodScreen
import com.studysmart.app.feature.academic.AcademicPeriodViewModel
import com.studysmart.app.feature.chapters.ChaptersScreen
import com.studysmart.app.feature.chapters.ChaptersViewModel
import com.studysmart.app.feature.courses.CoursesScreen
import com.studysmart.app.feature.courses.CoursesViewModel
import com.studysmart.app.feature.subjects.SubjectsScreen
import com.studysmart.app.feature.subjects.SubjectsViewModel
import com.studysmart.app.feature.preparation.PreparationEntryScreen
import com.studysmart.app.feature.preparation.PreparationSessionEffect
import com.studysmart.app.feature.preparation.PreparationSessionScreen
import com.studysmart.app.feature.preparation.PreparationSessionViewModel
import com.studysmart.app.feature.preparation.PreparationSummaryScreen
import com.studysmart.app.feature.preparation.PreparationSummaryViewModel
import com.studysmart.app.feature.preparation.PreparationEntryViewModel
import com.studysmart.app.feature.review.ReviewEffect
import com.studysmart.app.feature.review.ReviewScreen
import com.studysmart.app.feature.review.ReviewViewModel
import com.studysmart.app.feature.test.TestAttemptLauncherEffect
import com.studysmart.app.feature.test.TestAttemptLauncherViewModel
import com.studysmart.app.feature.test.TestAttemptEffect
import com.studysmart.app.feature.test.TestAttemptViewModel
import com.studysmart.app.feature.test.TestAttemptScreen
import com.studysmart.app.feature.test.isTransient

@Composable
fun AppNavHost(
    navController: NavHostController,
    container: AppContainer,
    startRoute: String = AppRoutes.HOME,
    pendingTopLevelDestination: String? = null,
    onPendingTopLevelDestinationConsumed: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = startRoute,
        modifier = modifier,
    ) {
        composable(AppRoutes.HOME) {
            val homeViewModel: HomeViewModel = viewModel(
                factory = container.viewModelFactory { graph -> graph.homeViewModel() },
            )
            val state by homeViewModel.uiState.collectAsStateWithLifecycle()
            HomeScreen(
                uiState = state,
                onRetry = homeViewModel::retry,
                onSearch = { navController.navigate(AppRoutes.SEARCH) },
                onSettings = { navController.navigate(AppRoutes.SETTINGS) },
                onCourseSelected = { courseId ->
                    homeViewModel.selectCourse(courseId)
                    navController.navigateAcademicPeriod(courseId, AppRoutes.HOME)
                },
            )
        }
        composable(AppRoutes.COURSES) {
            val coursesViewModel: CoursesViewModel = viewModel(
                factory = container.viewModelFactory { graph -> graph.coursesViewModel() },
            )
            val state by coursesViewModel.uiState.collectAsStateWithLifecycle()
            CoursesScreen(
                uiState = state,
                onRetry = coursesViewModel::retry,
                onCourseSelected = { courseId ->
                    coursesViewModel.selectCourse(courseId)
                    navController.navigateAcademicPeriod(courseId, AppRoutes.COURSES)
                },
            )
        }
        composable(AppRoutes.REVIEW) {
            val reviewViewModel: ReviewViewModel = viewModel(
                factory = container.viewModelFactory { graph -> graph.reviewViewModel() },
            )
            val state by reviewViewModel.uiState.collectAsStateWithLifecycle()
            LaunchedEffect(reviewViewModel) {
                reviewViewModel.effects.collect { effect ->
                    when (effect) {
                        is ReviewEffect.OpenDueReview -> navController.navigate(
                            AppRoutes.prepareSession(effect.sessionId),
                        )
                    }
                }
            }
            ReviewScreen(
                state = state,
                onRetry = reviewViewModel::retry,
                onStartDueReview = reviewViewModel::startDueReview,
            )
        }
        composable(AppRoutes.PROGRESS) {
            PlaceholderScreen(title = "Progress", supportingText = "Progress is a Stage One destination.")
        }
        composable(AppRoutes.IMPORTANT) {
            PlaceholderScreen(title = "Important", supportingText = "Bookmarks are a Stage One destination.")
        }
        composable(
            route = AppRoutes.ACADEMIC_PERIOD,
            arguments = listOf(navArgument("courseId") { type = NavType.StringType }),
        ) { entry ->
            val courseId = entry.arguments?.getString("courseId")
            if (courseId.isNullOrBlank()) {
                MissingContentScreen(
                    title = "Academic Period",
                    onBack = { navController.navigateBackOrFallback(AppRoutes.HOME) },
                )
            } else {
                val academicPeriodViewModel: AcademicPeriodViewModel = viewModel(
                    factory = container.viewModelFactory { graph ->
                        graph.academicPeriodViewModel(courseId)
                    },
                )
                val state by academicPeriodViewModel.uiState.collectAsStateWithLifecycle()
                AcademicPeriodScreen(
                    uiState = state,
                    onRetry = academicPeriodViewModel::retry,
                    onBack = { navController.navigateBackOrFallback(AppRoutes.HOME) },
                    onPeriodSelected = { periodId ->
                        academicPeriodViewModel.selectPeriod(periodId) {
                            navController.navigate(AppRoutes.subjects(periodId))
                        }
                    },
                )
            }
        }
        composable(
            route = AppRoutes.SUBJECTS,
            arguments = listOf(navArgument("academicPeriodId") { type = NavType.StringType }),
        ) { entry ->
            val academicPeriodId = entry.arguments?.getString("academicPeriodId")
            if (academicPeriodId.isNullOrBlank()) {
                MissingContentScreen(
                    title = "Subjects",
                    onBack = { navController.navigateBackOrFallback(AppRoutes.HOME) },
                )
            } else {
                val subjectsViewModel: SubjectsViewModel = viewModel(
                    factory = container.viewModelFactory { graph ->
                        graph.subjectsViewModel(academicPeriodId)
                    },
                )
                val state by subjectsViewModel.uiState.collectAsStateWithLifecycle()
                SubjectsScreen(
                    uiState = state,
                    onRetry = subjectsViewModel::retry,
                    onBack = { navController.navigateBackOrFallback(AppRoutes.HOME) },
                    onSubjectSelected = { subjectId ->
                        navController.navigate(AppRoutes.subject(subjectId))
                    },
                )
            }
        }
        composable(
            route = AppRoutes.SUBJECT,
            arguments = listOf(navArgument("subjectId") { type = NavType.StringType }),
        ) { entry ->
            val subjectId = entry.arguments?.getString("subjectId")
            if (subjectId.isNullOrBlank()) {
                MissingContentScreen(
                    title = "Chapters",
                    onBack = { navController.navigateBackOrFallback(AppRoutes.HOME) },
                )
            } else {
                val chaptersViewModel: ChaptersViewModel = viewModel(
                    factory = container.viewModelFactory { graph ->
                        graph.chaptersViewModel(subjectId)
                    },
                )
                val state by chaptersViewModel.uiState.collectAsStateWithLifecycle()
                val testLauncherViewModel: TestAttemptLauncherViewModel = viewModel(
                    factory = container.viewModelFactory { graph -> graph.testAttemptLauncherViewModel() },
                )
                val testState by testLauncherViewModel.uiState.collectAsStateWithLifecycle()
                LaunchedEffect(testLauncherViewModel) {
                    testLauncherViewModel.effects.collect { effect ->
                        when (effect) {
                            is TestAttemptLauncherEffect.Created -> navController.navigate(
                                AppRoutes.mockTest(effect.attemptId),
                            )
                        }
                    }
                }
                ChaptersScreen(
                    uiState = state,
                    onRetry = chaptersViewModel::retry,
                    onBack = { navController.navigateBackOrFallback(AppRoutes.HOME) },
                    onPrepare = { chapterId -> navController.navigate(AppRoutes.prepareChapter(chapterId)) },
                    onTest = testLauncherViewModel::start,
                    testState = testState,
                    onRetryTest = testLauncherViewModel::retry,
                    onOpenTestDefaults = {
                        testLauncherViewModel.dismissNoQuestions()
                        navController.navigate(AppRoutes.TEST_DEFAULTS)
                    },
                    onDismissNoQuestions = testLauncherViewModel::dismissNoQuestions,
                )
            }
        }
        composable(
            route = AppRoutes.PREPARE_CHAPTER,
            arguments = listOf(navArgument("chapterId") { type = NavType.StringType }),
        ) { entry ->
            val chapterId = entry.arguments?.getString("chapterId")
            if (chapterId.isNullOrBlank()) {
                MissingContentScreen(
                    title = "Prepare Chapter",
                    onBack = { navController.navigateBackOrFallback(AppRoutes.HOME) },
                )
            } else {
                val viewModel: PreparationEntryViewModel = viewModel(
                    factory = container.viewModelFactory { graph ->
                        graph.preparationEntryViewModel(
                            scopeType = PreparationScopeType.CHAPTER,
                            scopeId = chapterId,
                            originType = PreparationOriginType.CHAPTER,
                            originReferenceId = chapterId,
                        )
                    },
                )
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                LaunchedEffect(state.sessionId) {
                    state.sessionId?.let { sessionId ->
                        navController.navigate(AppRoutes.prepareSession(sessionId)) {
                            popUpTo(entry.destination.id) { inclusive = true }
                        }
                    }
                }
                PreparationEntryScreen(
                    title = "Prepare chapter",
                    state = state,
                    onRetry = viewModel::retry,
                    onBack = { navController.navigateBackOrFallback(AppRoutes.HOME) },
                )
            }
        }
        composable(
            route = AppRoutes.PREPARE_TOPIC,
            arguments = listOf(navArgument("topicId") { type = NavType.StringType }),
        ) { entry ->
            val topicId = entry.arguments?.getString("topicId")
            if (topicId.isNullOrBlank()) {
                MissingContentScreen(
                    title = "Prepare Topic",
                    onBack = { navController.navigateBackOrFallback(AppRoutes.HOME) },
                )
            } else {
                val viewModel: PreparationEntryViewModel = viewModel(
                    factory = container.viewModelFactory { graph ->
                        graph.preparationEntryViewModel(
                            scopeType = PreparationScopeType.TOPIC,
                            scopeId = topicId,
                            originType = PreparationOriginType.TOPIC,
                            originReferenceId = topicId,
                        )
                    },
                )
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                LaunchedEffect(state.sessionId) {
                    state.sessionId?.let { sessionId ->
                        navController.navigate(AppRoutes.prepareSession(sessionId)) {
                            popUpTo(entry.destination.id) { inclusive = true }
                        }
                    }
                }
                PreparationEntryScreen(
                    title = "Prepare topic",
                    state = state,
                    onRetry = viewModel::retry,
                    onBack = { navController.navigateBackOrFallback(AppRoutes.HOME) },
                )
            }
        }
        composable(
            route = AppRoutes.PREPARE_SESSION,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
        ) { entry ->
            val sessionId = entry.arguments?.getString("sessionId")
            if (sessionId.isNullOrBlank()) {
                MissingContentScreen(
                    title = "Preparation",
                    onBack = { navController.navigateBackOrFallback(AppRoutes.HOME) },
                )
            } else {
                val viewModel: PreparationSessionViewModel = viewModel(
                    factory = container.viewModelFactory { graph -> graph.preparationSessionViewModel(sessionId) },
                )
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                val latestPendingTopLevelDestination by rememberUpdatedState(pendingTopLevelDestination)
                val pendingTabDiscardRequested = remember { mutableStateOf(false) }
                LaunchedEffect(
                    pendingTopLevelDestination,
                    state.isLoading,
                    state.session?.status,
                    state.isSaving,
                ) {
                    val destination = pendingTopLevelDestination ?: return@LaunchedEffect
                    if (state.isLoading || state.isSaving) return@LaunchedEffect
                    if (state.session?.status == com.studysmart.app.core.model.PreparationSessionStatus.ACTIVE) {
                        pendingTabDiscardRequested.value = true
                        viewModel.showDiscardConfirmation()
                    } else if (!pendingTabDiscardRequested.value) {
                        onPendingTopLevelDestinationConsumed()
                        navController.navigateTopLevel(destination)
                    }
                }
                LaunchedEffect(viewModel) {
                    viewModel.effects.collect { effect ->
                        when (effect) {
                            is PreparationSessionEffect.Completed -> navController.navigate(
                                AppRoutes.preparationSummary(effect.sessionId),
                            ) {
                                popUpTo(entry.destination.id) { inclusive = true }
                            }
                            is PreparationSessionEffect.Discarded -> {
                                latestPendingTopLevelDestination?.let { destination ->
                                    pendingTabDiscardRequested.value = false
                                    onPendingTopLevelDestinationConsumed()
                                    navController.navigateTopLevel(destination)
                                } ?: navController.navigateBackOrFallback(
                                    preparationBackRoute(effect.originType, effect.subjectId),
                                )
                            }
                        }
                    }
                }
                PreparationSessionScreen(
                    state = state,
                    onReveal = viewModel::revealAnswer,
                    onRate = viewModel::rate,
                    onRetry = viewModel::retryMutation,
                    onClose = viewModel::showDiscardConfirmation,
                    onDiscard = viewModel::discardSession,
                    onCancelDiscard = {
                        pendingTabDiscardRequested.value = false
                        viewModel.hideDiscardConfirmation()
                        onPendingTopLevelDestinationConsumed()
                    },
                    onLoadRetry = viewModel::retryLoad,
                    onBackFallback = {
                        navController.navigateBackOrFallback(
                            preparationBackRoute(
                                state.session?.originType,
                                state.session?.context?.subjectId,
                            ),
                        )
                    },
                    onOpenSummary = {
                        navController.navigate(AppRoutes.preparationSummary(sessionId))
                    },
                )
            }
        }
        composable(
            route = AppRoutes.PREPARATION_SUMMARY,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
        ) { entry ->
            val sessionId = entry.arguments?.getString("sessionId")
            if (sessionId.isNullOrBlank()) {
                MissingContentScreen(
                    title = "Preparation Summary",
                    onBack = { navController.navigateBackOrFallback(AppRoutes.HOME) },
                )
            } else {
                val viewModel: PreparationSummaryViewModel = viewModel(
                    factory = container.viewModelFactory { graph -> graph.preparationSummaryViewModel(sessionId) },
                )
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                PreparationSummaryScreen(
                    state = state,
                    onRetry = viewModel::retry,
                    onBack = {
                        navController.navigateBackOrFallback(
                            preparationBackRoute(
                                state.session?.originType,
                                state.session?.context?.subjectId,
                            ),
                        )
                    },
                )
            }
        }
        composable(
            route = AppRoutes.MOCK_TEST,
            arguments = listOf(navArgument("testAttemptId") { type = NavType.StringType }),
        ) { entry ->
            val attemptId = entry.arguments?.getString("testAttemptId")
            if (attemptId.isNullOrBlank()) {
                MissingContentScreen(
                    title = "Mock Test",
                    onBack = { navController.navigateBackOrFallback(AppRoutes.HOME) },
                )
            } else {
                val viewModel: TestAttemptViewModel = viewModel(
                    factory = container.viewModelFactory { graph -> graph.testAttemptViewModel(attemptId) },
                )
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                val latestPendingTopLevelDestination by rememberUpdatedState(pendingTopLevelDestination)
                val pendingTabDiscardRequested = remember { mutableStateOf(false) }
                LaunchedEffect(
                    pendingTopLevelDestination,
                    state.isLoading,
                    state.attempt?.attempt?.status,
                    state.isDiscarding,
                ) {
                    val destination = pendingTopLevelDestination ?: return@LaunchedEffect
                    if (state.isLoading || state.isDiscarding) return@LaunchedEffect
                    if (state.attempt?.attempt?.status?.isTransient() == true) {
                        pendingTabDiscardRequested.value = true
                        viewModel.showDiscardConfirmation()
                    } else if (!pendingTabDiscardRequested.value) {
                        onPendingTopLevelDestinationConsumed()
                        navController.navigateTopLevel(destination)
                    }
                }
                LaunchedEffect(viewModel) {
                    viewModel.effects.collect { effect ->
                        when (effect) {
                            is TestAttemptEffect.Discarded -> {
                                pendingTabDiscardRequested.value = false
                                latestPendingTopLevelDestination?.let { destination ->
                                    onPendingTopLevelDestinationConsumed()
                                    navController.navigateTopLevel(destination)
                                } ?: navController.navigateBackOrFallback(
                                    testAttemptBackRoute(effect.originType, effect.subjectId),
                                )
                            }
                        }
                    }
                }
                TestAttemptScreen(
                    state = state,
                    onRetry = viewModel::retry,
                    onClose = viewModel::showDiscardConfirmation,
                    onDiscard = viewModel::discard,
                    onCancelDiscard = {
                        pendingTabDiscardRequested.value = false
                        viewModel.hideDiscardConfirmation()
                        onPendingTopLevelDestinationConsumed()
                    },
                    onBack = {
                        navController.navigateBackOrFallback(
                            testAttemptBackRoute(
                                state.attempt?.attempt?.originType,
                                state.attempt?.attempt?.subjectId,
                            ),
                        )
                    },
                )
            }
        }
        composable(
            route = AppRoutes.TEST_RESULT,
            arguments = listOf(navArgument("testAttemptId") { type = NavType.StringType }),
        ) { entry ->
            ContextualPlaceholder(
                entry = entry,
                argumentName = "testAttemptId",
                title = "Test Result",
                supportingText = "Test results are available after MVP-9.",
                onBack = { navController.navigateBackOrFallback(AppRoutes.HOME) },
            )
        }
        composable(AppRoutes.SEARCH) {
            PlaceholderScreen(title = "Search", onBack = { navController.navigateBackOrFallback(AppRoutes.HOME) })
        }
        composable(AppRoutes.SETTINGS) {
            PlaceholderScreen(title = "Settings", onBack = { navController.navigateBackOrFallback(AppRoutes.HOME) })
        }
        composable(AppRoutes.TEST_DEFAULTS) {
            PlaceholderScreen(title = "Test Defaults", onBack = { navController.navigateBackOrFallback(AppRoutes.SETTINGS) })
        }
        composable(
            route = AppRoutes.CHAPTER,
            arguments = listOf(navArgument("chapterId") { type = NavType.StringType }),
        ) { entry ->
            ContextualPlaceholder(
                entry = entry,
                argumentName = "chapterId",
                title = "Chapter",
                supportingText = "Chapter detail is available in Stage One.",
                onBack = { navController.navigateBackOrFallback(AppRoutes.HOME) },
            )
        }
        composable(
            route = AppRoutes.REVIEW_QUESTIONS,
            arguments = listOf(navArgument("filter") { type = NavType.StringType }),
        ) { entry ->
            val filter = entry.arguments?.getString("filter")
            if (!AppRoutes.isSupportedReviewFilter(filter)) {
                MissingContentScreen(
                    title = "Review Questions",
                    message = "This review filter is not available.",
                    onBack = { navController.navigateBackOrFallback(AppRoutes.REVIEW) },
                )
            } else {
                PlaceholderScreen(
                    title = "Review Questions",
                    supportingText = "Filter: $filter",
                    onBack = { navController.navigateBackOrFallback(AppRoutes.REVIEW) },
                )
            }
        }
    }
}

@Composable
private fun ContextualPlaceholder(
    entry: NavBackStackEntry,
    argumentName: String,
    title: String,
    supportingText: String,
    onBack: () -> Unit,
) {
    if (entry.arguments?.getString(argumentName).isNullOrBlank()) {
        MissingContentScreen(
            title = title,
            onBack = onBack,
        )
    } else {
        PlaceholderScreen(
            title = title,
            supportingText = supportingText,
            onBack = onBack,
        )
    }
}

private fun preparationBackRoute(
    originType: PreparationOriginType?,
    subjectId: String?,
): String = when (originType) {
    PreparationOriginType.CHAPTER,
    PreparationOriginType.TOPIC -> subjectId?.let(AppRoutes::subject) ?: AppRoutes.HOME
    PreparationOriginType.REVIEW -> AppRoutes.REVIEW
    PreparationOriginType.IMPORTANT -> AppRoutes.IMPORTANT
    PreparationOriginType.HOME_CONTINUE -> AppRoutes.HOME
    null -> AppRoutes.HOME
}

private fun testAttemptBackRoute(
    originType: TestAttemptOriginType?,
    subjectId: String?,
): String = when (originType) {
    TestAttemptOriginType.CHAPTER,
    TestAttemptOriginType.NEW_TEST,
    TestAttemptOriginType.RETEST_WEAK,
    -> subjectId?.let(AppRoutes::subject) ?: AppRoutes.HOME
    TestAttemptOriginType.IMPORTANT -> AppRoutes.IMPORTANT
    null -> AppRoutes.HOME
}
