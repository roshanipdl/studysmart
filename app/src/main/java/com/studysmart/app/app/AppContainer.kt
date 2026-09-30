package com.studysmart.app.app

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.studysmart.app.core.common.DefaultDispatcherProvider
import com.studysmart.app.core.common.DefaultIdGenerator
import com.studysmart.app.core.common.DefaultRandomSource
import com.studysmart.app.core.common.DispatcherProvider
import com.studysmart.app.core.common.IdGenerator
import com.studysmart.app.core.common.AndroidMonotonicClock
import com.studysmart.app.core.common.MonotonicClock
import com.studysmart.app.core.common.RandomSource
import com.studysmart.app.core.database.StudySmartDatabase
import com.studysmart.app.core.data.CurriculumRepository
import com.studysmart.app.core.data.DataStorePreferencesRepository
import com.studysmart.app.core.data.PreferencesRepository
import com.studysmart.app.feature.home.CurriculumHomeRepository
import com.studysmart.app.feature.home.HomeRepository
import com.studysmart.app.feature.home.HomeViewModel
import com.studysmart.app.feature.academic.AcademicPeriodViewModel
import com.studysmart.app.feature.chapters.ChaptersViewModel
import com.studysmart.app.feature.courses.CoursesViewModel
import com.studysmart.app.feature.subjects.SubjectsViewModel
import com.studysmart.app.core.model.PreparationOriginType
import com.studysmart.app.core.model.PreparationScopeType
import com.studysmart.app.feature.preparation.PreparationEntryViewModel
import com.studysmart.app.feature.preparation.PreparationRepository
import com.studysmart.app.feature.preparation.PreparationSessionRequest
import com.studysmart.app.feature.preparation.PreparationSessionViewModel
import com.studysmart.app.feature.preparation.PreparationSummaryViewModel
import com.studysmart.app.feature.preparation.RoomPreparationRepository
import com.studysmart.app.feature.review.ReviewRepository
import com.studysmart.app.feature.review.ReviewViewModel
import com.studysmart.app.feature.review.RoomReviewRepository
import com.studysmart.app.feature.readiness.ReadinessRepository
import com.studysmart.app.feature.readiness.RoomReadinessRepository
import com.studysmart.app.feature.test.RoomTestAttemptRepository
import com.studysmart.app.feature.test.TestAttemptRepository
import com.studysmart.app.feature.test.TestAttemptLauncherViewModel
import com.studysmart.app.feature.test.TestAttemptViewModel
import com.studysmart.app.feature.test.TestDefaultsViewModel
import com.studysmart.app.feature.test.TestResultViewModel
import com.studysmart.app.feature.test.AnswerEvaluator
import com.studysmart.app.feature.test.KeywordAnswerEvaluator
import java.time.Clock

/** The manual dependency graph for the local application. */
class AppContainer(
    context: Context,
    val database: StudySmartDatabase = StudySmartDatabase.create(context.applicationContext),
    val dispatchers: DispatcherProvider = DefaultDispatcherProvider,
    val clock: Clock = Clock.systemUTC(),
    val monotonicClock: MonotonicClock = AndroidMonotonicClock,
    val answerEvaluator: AnswerEvaluator = KeywordAnswerEvaluator(),
    val randomSource: RandomSource = DefaultRandomSource(),
    val idGenerator: IdGenerator = DefaultIdGenerator(),
    val preferences: PreferencesRepository = DataStorePreferencesRepository(context),
    val curriculumRepository: CurriculumRepository =
        com.studysmart.app.core.data.RoomCurriculumRepository(database.curriculumDao()),
    val homeRepository: HomeRepository = CurriculumHomeRepository(curriculumRepository),
    val preparationRepository: PreparationRepository = RoomPreparationRepository(
        database = database,
        clock = clock,
        randomSource = randomSource,
        idGenerator = idGenerator,
        dispatchers = dispatchers,
    ),
    val reviewRepository: ReviewRepository = RoomReviewRepository(database.preparationDao()),
    val readinessRepository: ReadinessRepository = RoomReadinessRepository(
        dao = database.readinessDao(),
        clock = clock,
    ),
    val testAttemptRepository: TestAttemptRepository = RoomTestAttemptRepository(
        database = database,
        preferencesRepository = preferences,
        clock = clock,
        randomSource = randomSource,
        idGenerator = idGenerator,
        dispatchers = dispatchers,
        monotonicClock = monotonicClock,
        answerEvaluator = answerEvaluator,
    ),
    val startupCleanupCoordinator: StartupCleanupCoordinator = RoomStartupCleanupCoordinator(
        database = database,
        dispatchers = dispatchers,
    ),
) {
    fun homeViewModel(): HomeViewModel = HomeViewModel(
        homeRepository = homeRepository,
        preferencesRepository = preferences,
        dispatchers = dispatchers,
        curriculumRepository = curriculumRepository,
    )

    fun coursesViewModel(): CoursesViewModel = CoursesViewModel(
        curriculumRepository = curriculumRepository,
        preferencesRepository = preferences,
        dispatchers = dispatchers,
    )

    fun academicPeriodViewModel(courseId: String): AcademicPeriodViewModel = AcademicPeriodViewModel(
        courseId = courseId,
        curriculumRepository = curriculumRepository,
        preferencesRepository = preferences,
        dispatchers = dispatchers,
    )

    fun subjectsViewModel(academicPeriodId: String): SubjectsViewModel = SubjectsViewModel(
        academicPeriodId = academicPeriodId,
        curriculumRepository = curriculumRepository,
        dispatchers = dispatchers,
    )

    fun chaptersViewModel(subjectId: String): ChaptersViewModel = ChaptersViewModel(
        subjectId = subjectId,
        curriculumRepository = curriculumRepository,
        dispatchers = dispatchers,
    )

    fun preparationEntryViewModel(
        scopeType: PreparationScopeType,
        scopeId: String,
        originType: PreparationOriginType,
        originReferenceId: String?,
    ): PreparationEntryViewModel = PreparationEntryViewModel(
        request = PreparationSessionRequest(
            scopeType = scopeType,
            scopeId = scopeId,
            originType = originType,
            originReferenceId = originReferenceId,
            creationRequestId = idGenerator.nextId("preparation-request"),
        ),
        repository = preparationRepository,
        dispatchers = dispatchers,
    )

    fun preparationSessionViewModel(sessionId: String): PreparationSessionViewModel =
        PreparationSessionViewModel(
            sessionId = sessionId,
            repository = preparationRepository,
            idGenerator = idGenerator,
            dispatchers = dispatchers,
        )

    fun preparationSummaryViewModel(sessionId: String): PreparationSummaryViewModel =
        PreparationSummaryViewModel(
            sessionId = sessionId,
            repository = preparationRepository,
            readinessRepository = readinessRepository,
            dispatchers = dispatchers,
        )

    fun reviewViewModel(): ReviewViewModel = ReviewViewModel(
        preferencesRepository = preferences,
        curriculumRepository = curriculumRepository,
        reviewRepository = reviewRepository,
        readinessRepository = readinessRepository,
        preparationRepository = preparationRepository,
        clock = clock,
        idGenerator = idGenerator,
        dispatchers = dispatchers,
    )

    fun testAttemptLauncherViewModel(): TestAttemptLauncherViewModel = TestAttemptLauncherViewModel(
        repository = testAttemptRepository,
        idGenerator = idGenerator,
        dispatchers = dispatchers,
    )

    fun testAttemptViewModel(attemptId: String): TestAttemptViewModel = TestAttemptViewModel(
        attemptId = attemptId,
        repository = testAttemptRepository,
        dispatchers = dispatchers,
        clock = clock,
        monotonicClock = monotonicClock,
    )

    fun testResultViewModel(attemptId: String): TestResultViewModel = TestResultViewModel(
        attemptId = attemptId,
        repository = testAttemptRepository,
        idGenerator = idGenerator,
        dispatchers = dispatchers,
    )

    fun testDefaultsViewModel(): TestDefaultsViewModel = TestDefaultsViewModel(
        preferencesRepository = preferences,
        dispatchers = dispatchers,
    )

    fun viewModelFactory(creator: (AppContainer) -> ViewModel): ViewModelProvider.Factory =
        AppViewModelFactory(this, creator)
}
