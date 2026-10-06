package com.studysmart.app.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.studysmart.app.core.model.TestDefaults
import com.studysmart.app.core.model.PreparationDefaults
import com.studysmart.app.core.model.TestQuestionSource
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

data class SelectedAcademicContext(
    val courseId: String,
    val academicPeriodId: String,
)

data class StudyPreferences(
    val selectedContext: SelectedAcademicContext? = null,
    val selectedCourseIdOverride: String? = null,
    val selectedAcademicPeriodIdOverride: String? = null,
    val testDefaults: TestDefaults = TestDefaults(),
    val preparationDefaults: PreparationDefaults = PreparationDefaults(),
    val dailyCardGoal: Int = com.studysmart.app.core.model.DailyCardGoal.DEFAULT,
    val theme: com.studysmart.app.core.model.ThemePreference = com.studysmart.app.core.model.ThemePreference.SYSTEM,
) {
    val selectedCourseId: String?
        get() = selectedCourseIdOverride ?: selectedContext?.courseId

    val selectedAcademicPeriodId: String?
        get() = selectedAcademicPeriodIdOverride ?: selectedContext?.academicPeriodId
}

interface PreferencesRepository {
    suspend fun updateTheme(theme: com.studysmart.app.core.model.ThemePreference) { throw UnsupportedOperationException("Theme updates unavailable") }
    val preferences: Flow<StudyPreferences>

    suspend fun selectCourse(courseId: String)

    suspend fun setSelectedContext(courseId: String, academicPeriodId: String)

    suspend fun clearSelectedCourse()

    suspend fun clearSelectedPeriod()

    /** Compare and clear atomically so stale validation cannot erase a newer selection. */
    suspend fun clearSelectedCourseIfUnchanged(courseId: String, academicPeriodId: String?): Boolean

    suspend fun clearSelectedPeriodIfUnchanged(courseId: String, academicPeriodId: String): Boolean

    suspend fun clearSelectedContext()

    suspend fun updateTestDefaults(defaults: TestDefaults) = Unit

    suspend fun updatePreparationDefaults(defaults: PreparationDefaults)

    suspend fun updateDailyCardGoal(goal: Int) { throw UnsupportedOperationException("Daily goal updates unavailable") }
}

private val Context.studySmartPreferencesDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "user_preferences")

class DataStorePreferencesRepository(
    private val dataStore: DataStore<Preferences>,
) : PreferencesRepository {
    constructor(context: Context) : this(
        context.applicationContext.studySmartPreferencesDataStore,
    )

    override val preferences: Flow<StudyPreferences> = dataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                throw error
            }
        }
        .map { values ->
            val courseId = values[SELECTED_COURSE_ID]
            val periodId = values[SELECTED_ACADEMIC_PERIOD_ID]
            StudyPreferences(
                theme = com.studysmart.app.core.model.ThemePreference.fromStored(runCatching { values[THEME] }.getOrNull()),
                selectedContext = if (!courseId.isNullOrBlank() && !periodId.isNullOrBlank()) {
                    SelectedAcademicContext(courseId, periodId)
                } else {
                    null
                },
                selectedCourseIdOverride = courseId,
                selectedAcademicPeriodIdOverride = periodId,
                testDefaults = readTestDefaults(values),
                preparationDefaults = PreparationDefaults(
                    questionCount = runCatching { values[PREPARATION_QUESTION_COUNT] }.getOrNull()
                        ?.takeIf { it in PreparationDefaults.MIN_QUESTION_COUNT..PreparationDefaults.MAX_QUESTION_COUNT }
                        ?: PreparationDefaults.DEFAULT_QUESTION_COUNT,
                ),
                dailyCardGoal = com.studysmart.app.core.model.DailyCardGoal.normalize(runCatching { values[DAILY_CARD_GOAL] }.getOrNull()),
            )
        }

    override suspend fun selectCourse(courseId: String) {
        require(courseId.isNotBlank()) { "courseId must not be blank" }
        dataStore.edit { values ->
            values[SELECTED_COURSE_ID] = courseId
            values.remove(SELECTED_ACADEMIC_PERIOD_ID)
        }
    }

    override suspend fun setSelectedContext(courseId: String, academicPeriodId: String) {
        require(courseId.isNotBlank()) { "courseId must not be blank" }
        require(academicPeriodId.isNotBlank()) { "academicPeriodId must not be blank" }
        dataStore.edit { values ->
            values[SELECTED_COURSE_ID] = courseId
            values[SELECTED_ACADEMIC_PERIOD_ID] = academicPeriodId
        }
    }

    override suspend fun clearSelectedCourse() {
        dataStore.edit { values ->
            values.remove(SELECTED_COURSE_ID)
            values.remove(SELECTED_ACADEMIC_PERIOD_ID)
        }
    }

    override suspend fun clearSelectedPeriod() {
        dataStore.edit { values -> values.remove(SELECTED_ACADEMIC_PERIOD_ID) }
    }

    override suspend fun clearSelectedCourseIfUnchanged(
        courseId: String,
        academicPeriodId: String?,
    ): Boolean {
        var cleared = false
        dataStore.edit { values ->
            if (values[SELECTED_COURSE_ID] == courseId &&
                values[SELECTED_ACADEMIC_PERIOD_ID] == academicPeriodId
            ) {
                values.remove(SELECTED_COURSE_ID)
                values.remove(SELECTED_ACADEMIC_PERIOD_ID)
                cleared = true
            }
        }
        return cleared
    }

    override suspend fun clearSelectedPeriodIfUnchanged(
        courseId: String,
        academicPeriodId: String,
    ): Boolean {
        var cleared = false
        dataStore.edit { values ->
            if (values[SELECTED_COURSE_ID] == courseId &&
                values[SELECTED_ACADEMIC_PERIOD_ID] == academicPeriodId
            ) {
                values.remove(SELECTED_ACADEMIC_PERIOD_ID)
                cleared = true
            }
        }
        return cleared
    }

    override suspend fun clearSelectedContext() {
        clearSelectedCourse()
    }

    override suspend fun updateTestDefaults(defaults: TestDefaults) {
        dataStore.edit { values ->
            values[DEFAULT_QUESTION_COUNT] = defaults.questionCount
            values[DEFAULT_TIMED_MODE] = defaults.timedMode
            values[DEFAULT_DURATION_MINUTES] = defaults.durationMinutes
            values[DEFAULT_QUESTION_SOURCE] = defaults.questionSource.name.lowercase()
        }
    }

    override suspend fun updatePreparationDefaults(defaults: PreparationDefaults) {
        dataStore.edit { values -> values[PREPARATION_QUESTION_COUNT] = defaults.questionCount }
    }

    override suspend fun updateDailyCardGoal(goal: Int) {
        require(goal in com.studysmart.app.core.model.DailyCardGoal.MIN..com.studysmart.app.core.model.DailyCardGoal.MAX)
        dataStore.edit { it[DAILY_CARD_GOAL] = goal }
    }

    override suspend fun updateTheme(theme: com.studysmart.app.core.model.ThemePreference) {
        dataStore.edit { it[THEME] = theme.name }
    }

    private fun readTestDefaults(values: Preferences): TestDefaults {
        val count = runCatching { values[DEFAULT_QUESTION_COUNT] }
            .getOrNull()
            ?.takeIf { it in TestDefaults.MIN_QUESTION_COUNT..TestDefaults.MAX_QUESTION_COUNT }
            ?: TestDefaults.DEFAULT_QUESTION_COUNT
        val timed = runCatching { values[DEFAULT_TIMED_MODE] }.getOrNull() ?: TestDefaults.DEFAULT_TIMED_MODE
        val duration = runCatching { values[DEFAULT_DURATION_MINUTES] }
            .getOrNull()
            ?.takeIf { it in TestDefaults.MIN_DURATION_MINUTES..TestDefaults.MAX_DURATION_MINUTES }
            ?: TestDefaults.DEFAULT_DURATION_MINUTES
        val source = runCatching { values[DEFAULT_QUESTION_SOURCE] }
            .getOrNull()
            ?.let { raw -> runCatching { TestQuestionSource.valueOf(raw.uppercase()) }.getOrNull() }
            ?: TestDefaults.DEFAULT_QUESTION_SOURCE
        return TestDefaults(
            questionCount = count,
            timedMode = timed,
            durationMinutes = duration,
            questionSource = source,
        )
    }

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val PREPARATION_QUESTION_COUNT = intPreferencesKey("preparation_review_question_count")
        val DAILY_CARD_GOAL = intPreferencesKey("daily_card_goal")
        val SELECTED_COURSE_ID = stringPreferencesKey("selected_course_id")
        val SELECTED_ACADEMIC_PERIOD_ID = stringPreferencesKey("selected_academic_period_id")
        val DEFAULT_QUESTION_COUNT = intPreferencesKey("default_question_count")
        val DEFAULT_TIMED_MODE = booleanPreferencesKey("default_timed_mode")
        val DEFAULT_DURATION_MINUTES = intPreferencesKey("default_duration_minutes")
        val DEFAULT_QUESTION_SOURCE = stringPreferencesKey("default_question_source")
    }
}
