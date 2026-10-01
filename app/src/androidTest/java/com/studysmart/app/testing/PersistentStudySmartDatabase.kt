package com.studysmart.app.testing

import android.content.Context
import androidx.room.Room
import com.studysmart.app.app.AppContainer
import com.studysmart.app.core.common.MonotonicClock
import com.studysmart.app.core.common.RandomSource
import com.studysmart.app.core.data.PreferencesRepository
import com.studysmart.app.core.data.SelectedAcademicContext
import com.studysmart.app.core.data.StudyPreferences
import com.studysmart.app.core.database.DatabaseSeed
import com.studysmart.app.core.database.MIGRATION_1_2
import com.studysmart.app.core.database.MIGRATION_2_3
import com.studysmart.app.core.database.MIGRATION_3_4
import com.studysmart.app.core.database.MIGRATION_4_5
import com.studysmart.app.core.database.StudySmartDatabase
import com.studysmart.app.core.model.TestDefaults
import com.studysmart.app.core.model.TestQuestionSource
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Caller owns the uniquely named file and must close/delete it after the test. */
fun createPersistentStudySmartDatabase(context: Context, name: String): StudySmartDatabase {
    require(name.startsWith("mvp10-test-")) { "Only isolated MVP-10 test databases are allowed." }
    return Room.databaseBuilder(context, StudySmartDatabase::class.java, name)
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
        .addCallback(DatabaseSeed.callback(includeVariantSeed = false))
        .build()
}

fun coreAlphaTestContainer(context: Context, database: StudySmartDatabase): AppContainer = AppContainer(
    context = context,
    database = database,
    clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC),
    monotonicClock = object : MonotonicClock {
        override fun elapsedRealtimeMillis(): Long = 10_000L
        override fun bootId(): String = "mvp10-test-boot"
    },
    randomSource = object : RandomSource {
        override fun nextInt(until: Int): Int = 0
        override fun nextLong(): Long = 17L
    },
    preferences = CoreAlphaTestPreferences(),
)

private class CoreAlphaTestPreferences : PreferencesRepository {
    private val state = MutableStateFlow(StudyPreferences(
        selectedContext = SelectedAcademicContext("course-bca", "period-bca-semester-1"),
        testDefaults = TestDefaults(questionSource = TestQuestionSource.ALL),
    ))
    override val preferences: Flow<StudyPreferences> = state.asStateFlow()
    override suspend fun selectCourse(courseId: String) {
        state.value = state.value.copy(selectedContext = null, selectedCourseIdOverride = courseId)
    }
    override suspend fun setSelectedContext(courseId: String, academicPeriodId: String) {
        state.value = state.value.copy(
            selectedContext = SelectedAcademicContext(courseId, academicPeriodId),
            selectedCourseIdOverride = null, selectedAcademicPeriodIdOverride = null,
        )
    }
    override suspend fun clearSelectedCourse() = clearSelectedContext()
    override suspend fun clearSelectedPeriod() {
        state.value = state.value.copy(
            selectedCourseIdOverride = state.value.selectedCourseId,
            selectedContext = null, selectedAcademicPeriodIdOverride = null,
        )
    }
    override suspend fun clearSelectedContext() {
        state.value = state.value.copy(
            selectedContext = null, selectedCourseIdOverride = null, selectedAcademicPeriodIdOverride = null,
        )
    }
    override suspend fun updatePreparationDefaults(defaults: com.studysmart.app.core.model.PreparationDefaults) {
        state.value = state.value.copy(preparationDefaults = defaults)
    }

    override suspend fun updateTestDefaults(defaults: TestDefaults) {
        state.value = state.value.copy(testDefaults = defaults)
    }

    override suspend fun clearSelectedCourseIfUnchanged(courseId: String, academicPeriodId: String?): Boolean {
        if (state.value.selectedCourseId != courseId || state.value.selectedAcademicPeriodId != academicPeriodId) return false
        clearSelectedCourse()
        return true
    }

    override suspend fun clearSelectedPeriodIfUnchanged(courseId: String, academicPeriodId: String): Boolean {
        if (state.value.selectedCourseId != courseId || state.value.selectedAcademicPeriodId != academicPeriodId) return false
        clearSelectedPeriod()
        return true
    }
}
