package com.studysmart.app.core.navigation

import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.activity.compose.BackHandler
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
import com.studysmart.app.feature.test.TestResultEffect
import com.studysmart.app.feature.test.TestResultScreen
import com.studysmart.app.feature.test.TestResultOrigin
import com.studysmart.app.feature.test.TestResultViewModel
import com.studysmart.app.feature.test.TestDefaultsScreen
import com.studysmart.app.feature.test.TestDefaultsViewModel
import com.studysmart.app.feature.test.isTransient
import com.studysmart.app.feature.settings.SettingsScreen

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
                onRetrySelection = homeViewModel::retrySelection,
                onSearch = { navController.navigate(AppRoutes.SEARCH) },
                onSettings = { navController.navigate(AppRoutes.SETTINGS) },
                onCourseSelected = { courseId ->
                    homeViewModel.selectCourse(courseId) {
                        navController.navigateAcademicPeriod(courseId, AppRoutes.HOME)
                    }
                },
            )
        }
        composable(AppRoutes.COURSES) { entry ->
            val coursesViewModel: CoursesViewModel = viewModel(
                factory = container.viewModelFactory { graph -> graph.coursesViewModel() },
            )
            val state by coursesViewModel.uiState.collectAsStateWithLifecycle()
            CoursesScreen(
                uiState = state,
                onRetry = coursesViewModel::retry,
                onRetrySelection = coursesViewModel::retrySelection,
                onCourseSelected = { courseId ->
                    coursesViewModel.selectCourse(courseId) {
                        navController.navigateAcademicPeriod(courseId, AppRoutes.COURSES,
                            entry.savedStateHandle.get<String>(CONTEXT_RETURN_KEY))
                    }
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
            val progressViewModel: com.studysmart.app.feature.progress.ProgressViewModel = viewModel(factory = container.viewModelFactory { it.progressViewModel() })
            val state by progressViewModel.uiState.collectAsStateWithLifecycle()
            androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { progressViewModel.onForeground() }
            LaunchedEffect(progressViewModel) {
                progressViewModel.sessions.collect {
                    navController.navigate(AppRoutes.prepareSession(it))
                    progressViewModel.acknowledgeSession(it)
                }
            }
            com.studysmart.app.feature.progress.ProgressScreen(state,
                onChooseContext = { navController.navigateContextSelection(AppRoutes.PROGRESS) },
                onBrowse = { navController.navigateTopLevel(AppRoutes.COURSES) },
                onRetry = progressViewModel::retry, onStartDue = progressViewModel::startDue, onRetryDue = progressViewModel::retryDue,
                onSettings = { navController.navigate(AppRoutes.SETTINGS) })
        }
        composable(AppRoutes.IMPORTANT) {
            val importantViewModel: com.studysmart.app.feature.important.ImportantViewModel = viewModel(
                factory = androidx.lifecycle.viewmodel.viewModelFactory {
                    initializer { container.importantViewModel(createSavedStateHandle()) }
                },
            )
            val state by importantViewModel.uiState.collectAsStateWithLifecycle()
            androidx.compose.runtime.DisposableEffect(importantViewModel) {
                importantViewModel.refresh()
                onDispose { importantViewModel.leaveScreen() }
            }
            LaunchedEffect(importantViewModel) {
                importantViewModel.effects.collect { effect ->
                    when (effect) {
                        is com.studysmart.app.feature.important.ImportantEffect.OpenPreparation -> navController.navigate(AppRoutes.prepareSession(effect.sessionId))
                        is com.studysmart.app.feature.important.ImportantEffect.OpenTest -> navController.navigate(AppRoutes.mockTest(effect.attemptId))
                    }
                }
            }
            com.studysmart.app.feature.important.ImportantScreen(state,
                onChooseContext = { navController.navigateContextSelection(AppRoutes.IMPORTANT) },
                onSubject = importantViewModel::selectSubject,
                onChapter = importantViewModel::selectChapter,
                onRefresh = importantViewModel::refresh,
                onRemove = importantViewModel::remove,
                onRetryRemove = importantViewModel::retryRemoval,
                onOpenQuestion = { row -> navController.navigateChapterDetail(row.chapterId, row.topicId, row.questionId) },
                onPrepare = importantViewModel::prepareSaved,
                onTest = importantViewModel::testSaved,
                onRetryLaunch = importantViewModel::retryLaunch,
                onOpenDefaults = { navController.navigate(AppRoutes.TEST_DEFAULTS) },
            )
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
                    onRetrySelection = academicPeriodViewModel::retrySelection,
                    onBack = { navController.navigateBackOrFallback(AppRoutes.HOME) },
                    onPeriodSelected = { periodId ->
                        academicPeriodViewModel.selectPeriod(periodId) {
                            val returnRoute = contextReturnDestination(entry.savedStateHandle.get<String>(CONTEXT_RETURN_KEY))
                            if (returnRoute != null) navController.navigateTopLevel(returnRoute)
                            else navController.navigate(destinationAfterPeriodSelection(periodId, null))
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
                            is TestAttemptLauncherEffect.Created -> {
                                navController.navigate(AppRoutes.mockTest(effect.attemptId))
                                testLauncherViewModel.onCreatedNavigationHandled()
                            }
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
                            val previous = navController.previousBackStackEntry
                            val popDestination = if (previous?.destination?.route == AppRoutes.PREPARATION_SUMMARY) {
                                previous.destination.id
                            } else entry.destination.id
                            popUpTo(popDestination) { inclusive = true }
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
                    state.ratingFeedback,
                    state.completionReady,
                ) {
                    val destination = pendingTopLevelDestination ?: return@LaunchedEffect
                    if (state.isLoading || state.isSaving || state.ratingFeedback != null || state.completionReady) return@LaunchedEffect
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
                LaunchedEffect(state.completionReady) {
                    if (state.completionReady) {
                        val pendingDestination = latestPendingTopLevelDestination
                        if (pendingDestination != null) {
                            pendingTabDiscardRequested.value = false
                            onPendingTopLevelDestinationConsumed()
                            navController.navigateTopLevel(pendingDestination)
                        } else {
                            navController.navigate(AppRoutes.preparationSummary(sessionId)) {
                                popUpTo(entry.destination.id) { inclusive = true }
                            }
                        }
                    }
                }
                PreparationSessionScreen(
                    state = state,
                    onReveal = viewModel::revealAnswer,
                    onRatingAnimationFinished = viewModel::finishRatingAnimation,
                    onRate = viewModel::rate,
                    onBookmark = viewModel::toggleBookmark,
                    onRetryBookmark = viewModel::retryBookmark,
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
                    onRetryReadiness = viewModel::retryReadiness,
                    onRetryCoverage = viewModel::retryCoverage,
                    onContinue = {
                        val chapterId = state.session?.chapterId
                        if (chapterId != null && state.session?.scopeType != PreparationScopeType.IMPORTANT && navController.currentBackStackEntry == entry &&
                            !state.isCoverageLoading && !state.isChapterUnavailable && state.chapterCoverage?.eligibleCount != 0) {
                            navController.navigate(AppRoutes.prepareChapter(chapterId)) { launchSingleTop = true }
                        }
                    },
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
                val latestResolvedAttemptOrigin by rememberUpdatedState(state.resolvedOrigin)
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
                            TestAttemptEffect.Discarded -> {
                                pendingTabDiscardRequested.value = false
                                latestPendingTopLevelDestination?.let { destination ->
                                    onPendingTopLevelDestinationConsumed()
                                    navController.navigateTopLevel(destination)
                                } ?: navController.navigateToOrigin(
                                    latestResolvedAttemptOrigin?.let(::testAttemptBackRoute) ?: AppRoutes.HOME,
                                )
                            }
                            is TestAttemptEffect.Submitted -> navController.navigate(
                                AppRoutes.testResult(effect.attemptId),
                            ) {
                                popUpTo(entry.destination.id) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    }
                }
                val activeAttemptIsTransient = state.attempt?.attempt?.status?.isTransient() == true
                BackHandler {
                    when {
                        activeAttemptIsTransient -> viewModel.showDiscardConfirmation()
                        state.canNavigateBack -> navController.navigateToOrigin(
                            state.resolvedOrigin?.let(::testAttemptBackRoute) ?: AppRoutes.HOME,
                        )
                    }
                }
                TestAttemptScreen(
                    state = state,
                    onRetry = viewModel::retry,
                    onRetryOrigin = viewModel::retryOrigin,
                    onRetryMutation = viewModel::retryMutation,
                    onAnswerChanged = viewModel::onAnswerChanged,
                    onFlag = viewModel::toggleFlag,
                    onPrevious = viewModel::previousQuestion,
                    onNext = viewModel::nextQuestion,
                    onSelectQuestion = viewModel::selectQuestion,
                    onFlushDraft = viewModel::flushDraft,
                    onRequestSubmit = viewModel::requestSubmit,
                    onConfirmSubmit = viewModel::confirmSubmit,
                    onCancelSubmit = viewModel::cancelSubmitConfirmation,
                    onConfirmExpiredSubmit = viewModel::confirmExpiredSubmit,
                    onDiscardAfterExpiry = viewModel::discardAfterExpiry,
                    onClose = viewModel::showDiscardConfirmation,
                    onDiscard = viewModel::discard,
                    onCancelDiscard = {
                        pendingTabDiscardRequested.value = false
                        viewModel.hideDiscardConfirmation()
                        onPendingTopLevelDestinationConsumed()
                    },
                    onBack = {
                        if (state.canNavigateBack) {
                            navController.navigateToOrigin(
                                state.resolvedOrigin?.let(::testAttemptBackRoute) ?: AppRoutes.HOME,
                            )
                        }
                    },
                    onViewResult = { navController.navigate(AppRoutes.testResult(attemptId)) },
                    onAppBackgrounded = viewModel::onAppBackgrounded,
                    onAppForegrounded = viewModel::onAppForegrounded,
                )
            }
        }
        composable(
            route = AppRoutes.TEST_RESULT,
            arguments = listOf(navArgument("testAttemptId") { type = NavType.StringType }),
        ) { entry ->
            val attemptId = entry.arguments?.getString("testAttemptId")
            if (attemptId.isNullOrBlank()) {
                MissingContentScreen(
                    title = "Test result",
                    onBack = { navController.navigateBackOrFallback(AppRoutes.HOME) },
                )
            } else {
                val viewModel: TestResultViewModel = viewModel(
                    factory = container.viewModelFactory { graph -> graph.testResultViewModel(attemptId) },
                )
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                val resultBackRoute = testResultBackRoute(state.origin)
                val navigateBackToResultOrigin = {
                    if (state.canNavigateBack) navController.navigateToOrigin(resultBackRoute)
                }
                BackHandler { navigateBackToResultOrigin() }
                LaunchedEffect(viewModel) {
                    viewModel.effects.collect { effect ->
                        when (effect) {
                            is TestResultEffect.OpenAttempt -> navController.navigate(
                                AppRoutes.mockTest(effect.attemptId),
                            ) {
                                popUpTo(entry.destination.id) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    }
                }
                TestResultScreen(
                    state = state,
                    onRetry = viewModel::retry,
                    onRetryOrigin = viewModel::retryOrigin,
                    onRetryFollowUp = viewModel::retryFollowUp,
                    onRetryActionContext = viewModel::retryActionContext,
                    onToggleQuestion = viewModel::toggleQuestion,
                    onBookmark = viewModel::toggleBookmark,
                    onRetryBookmark = viewModel::retryBookmark,
                    onRetestWeak = viewModel::startRetestWeak,
                    onNewTest = viewModel::startNewTest,
                    onBack = navigateBackToResultOrigin,
                )
            }
        }
        composable(AppRoutes.SEARCH) {
            val searchViewModel: com.studysmart.app.feature.search.SearchViewModel = viewModel(
                factory = androidx.lifecycle.viewmodel.viewModelFactory {
                    initializer { container.searchViewModel(createSavedStateHandle()) }
                },
            )
            val state by searchViewModel.uiState.collectAsStateWithLifecycle()
            com.studysmart.app.feature.search.SearchScreen(state, searchViewModel::setQuery, searchViewModel::retry,
                onResult = { result ->
                    when (result) {
                        is com.studysmart.app.core.data.SearchResult.Subject -> navController.navigate(AppRoutes.subject(result.id))
                        is com.studysmart.app.core.data.SearchResult.Chapter -> navController.navigateChapterDetail(result.id, highlightChapter = true)
                        is com.studysmart.app.core.data.SearchResult.Topic -> navController.navigateChapterDetail(result.chapterId, topicId = result.id)
                        is com.studysmart.app.core.data.SearchResult.Question -> navController.navigateChapterDetail(result.chapterId, result.topicId, result.id)
                    }
                }, onBack = { navController.navigateBackOrFallback(AppRoutes.HOME) })
        }
        composable(AppRoutes.SETTINGS) {
            val viewModel: com.studysmart.app.feature.settings.SettingsViewModel = viewModel(
                factory = container.viewModelFactory { graph -> graph.settingsViewModel() },
            )
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            LaunchedEffect(pendingTopLevelDestination, state.isSaving, state.isSaveError) {
                val destination = pendingTopLevelDestination ?: return@LaunchedEffect
                if (!state.isSaving && !state.isSaveError) {
                    onPendingTopLevelDestinationConsumed()
                    navController.navigateTopLevel(destination)
                }
            }
            val navigateBack = {
                if (!state.isSaving) {
                    onPendingTopLevelDestinationConsumed()
                    navController.navigateBackOrFallback(AppRoutes.HOME)
                }
            }
            BackHandler(onBack = navigateBack)
            SettingsScreen(
                state = state,
                onBack = navigateBack,
                onOpenTestDefaults = { navController.navigate(AppRoutes.TEST_DEFAULTS) },
                onRetry = viewModel::retry,
                onDecrementCount = viewModel::decrementQuestionCount,
                    onIncrementCount = viewModel::incrementQuestionCount,
                    onDecrementGoal = viewModel::decrementDailyGoal,
                    onIncrementGoal = viewModel::incrementDailyGoal,
            )
        }
        composable(AppRoutes.TEST_DEFAULTS) {
            val viewModel: TestDefaultsViewModel = viewModel(
                factory = container.viewModelFactory { graph -> graph.testDefaultsViewModel() },
            )
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            LaunchedEffect(pendingTopLevelDestination, state.isSaving, state.isSaveError) {
                val destination = pendingTopLevelDestination ?: return@LaunchedEffect
                if (!state.isSaving && !state.isSaveError) {
                    onPendingTopLevelDestinationConsumed()
                    navController.navigateTopLevel(destination)
                }
            }
            val navigateBack = {
                if (!state.isSaving) {
                    if (state.isSaveError || pendingTopLevelDestination != null) {
                        onPendingTopLevelDestinationConsumed()
                    }
                    navController.navigateBackOrFallback(AppRoutes.SETTINGS)
                }
            }
            BackHandler(onBack = navigateBack)
            TestDefaultsScreen(
                state = state,
                onBack = navigateBack,
                onRetry = viewModel::retry,
                onDecrementCount = viewModel::decrementQuestionCount,
                onIncrementCount = viewModel::incrementQuestionCount,
                onSetTimedMode = viewModel::setTimedMode,
                onDecrementDuration = { state.defaults?.let { viewModel.setDurationMinutes(it.durationMinutes - 1) } },
                onIncrementDuration = { state.defaults?.let { viewModel.setDurationMinutes(it.durationMinutes + 1) } },
                onSetSource = viewModel::setQuestionSource,
            )
        }
        composable(
            route = AppRoutes.CHAPTER,
            arguments = listOf(navArgument("chapterId") { type = NavType.StringType }),
        ) { entry ->
            val chapterId = entry.arguments?.getString("chapterId")
            if (chapterId.isNullOrBlank()) {
                MissingContentScreen(title = "Chapter", onBack = { navController.navigateBackOrFallback(AppRoutes.COURSES) })
            } else {
                val highlight = com.studysmart.app.feature.chapters.ChapterHighlight(
                    topicId = entry.savedStateHandle.get<String>(CHAPTER_HIGHLIGHT_TOPIC_KEY),
                    questionId = entry.savedStateHandle.get<String>(CHAPTER_HIGHLIGHT_QUESTION_KEY),
                    chapter = entry.savedStateHandle.get<Boolean>(CHAPTER_HIGHLIGHT_HEADER_KEY) == true,
                )
                val detailViewModel: com.studysmart.app.feature.chapters.ChapterDetailViewModel = viewModel(
                    factory = container.viewModelFactory { it.chapterDetailViewModel(chapterId, highlight) },
                )
                val state by detailViewModel.uiState.collectAsStateWithLifecycle()
                val launcher: TestAttemptLauncherViewModel = viewModel(
                    factory = container.viewModelFactory { it.testAttemptLauncherViewModel() },
                )
                val testState by launcher.uiState.collectAsStateWithLifecycle()
                LaunchedEffect(launcher) {
                    launcher.effects.collect { effect ->
                        when (effect) {
                            is TestAttemptLauncherEffect.Created -> {
                                navController.navigate(AppRoutes.mockTest(effect.attemptId))
                                launcher.onCreatedNavigationHandled()
                            }
                        }
                    }
                }
                val navigateBack: () -> Unit = {
                    if (navController.previousBackStackEntry?.destination?.route == AppRoutes.SEARCH) {
                        navController.popBackStack()
                    } else {
                        navController.navigateToAcademicParent(state.parent)
                    }
                }
                BackHandler(onBack = navigateBack)
                com.studysmart.app.feature.chapters.ChapterDetailScreen(
                    state, navigateBack, detailViewModel::retry,
                    onPrepare = { navController.navigate(AppRoutes.prepareChapter(chapterId)) },
                    onTest = { launcher.start(chapterId) }, testState = testState,
                    onRetryTest = launcher::retry,
                    onOpenTestDefaults = {
                        launcher.dismissNoQuestions()
                        navController.navigate(AppRoutes.TEST_DEFAULTS)
                    },
                    onDismissNoQuestions = launcher::dismissNoQuestions,
                )
            }
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

private fun testAttemptBackRoute(origin: TestResultOrigin?): String = when (origin?.originType) {
    TestAttemptOriginType.CHAPTER,
    TestAttemptOriginType.NEW_TEST,
    TestAttemptOriginType.RETEST_WEAK,
    -> origin.subjectId?.let(AppRoutes::subject) ?: AppRoutes.HOME
    TestAttemptOriginType.IMPORTANT -> AppRoutes.IMPORTANT
    null -> AppRoutes.HOME
}

private fun testResultBackRoute(origin: com.studysmart.app.feature.test.TestResultOrigin?): String =
    when (origin?.originType) {
        TestAttemptOriginType.CHAPTER -> origin.subjectId?.let(AppRoutes::subject) ?: AppRoutes.HOME
        TestAttemptOriginType.IMPORTANT -> AppRoutes.IMPORTANT
        TestAttemptOriginType.NEW_TEST,
        TestAttemptOriginType.RETEST_WEAK,
        null,
        -> AppRoutes.HOME
    }
