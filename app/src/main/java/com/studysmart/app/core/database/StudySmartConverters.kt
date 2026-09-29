package com.studysmart.app.core.database

import androidx.room.TypeConverter
import com.studysmart.app.core.model.AcademicPeriodType
import com.studysmart.app.core.model.LearningState
import com.studysmart.app.core.model.PreparationOriginType
import com.studysmart.app.core.model.PreparationScopeType
import com.studysmart.app.core.model.PreparationSessionStatus
import com.studysmart.app.core.model.QuestionType
import com.studysmart.app.core.model.Rating
import com.studysmart.app.core.model.TestAttemptOriginType
import com.studysmart.app.core.model.TestAttemptScopeType
import com.studysmart.app.core.model.TestAttemptStatus
import com.studysmart.app.core.model.TestQuestionSource
import java.time.Instant

class StudySmartConverters {
    @TypeConverter
    fun academicPeriodTypeToStorage(value: AcademicPeriodType): String = value.name.lowercase()

    @TypeConverter
    fun storageToAcademicPeriodType(value: String): AcademicPeriodType =
        AcademicPeriodType.valueOf(value.uppercase())

    @TypeConverter
    fun questionTypeToStorage(value: QuestionType): String = value.name.lowercase()

    @TypeConverter
    fun storageToQuestionType(value: String): QuestionType = QuestionType.valueOf(value.uppercase())

    @TypeConverter
    fun ratingToStorage(value: Rating?): String? = value?.name?.lowercase()

    @TypeConverter
    fun storageToRating(value: String?): Rating? = value?.let { Rating.valueOf(it.uppercase()) }

    @TypeConverter
    fun learningStateToStorage(value: LearningState): String = value.name.lowercase()

    @TypeConverter
    fun storageToLearningState(value: String): LearningState = LearningState.valueOf(value.uppercase())

    @TypeConverter
    fun preparationScopeTypeToStorage(value: PreparationScopeType): String = value.name.lowercase()

    @TypeConverter
    fun storageToPreparationScopeType(value: String): PreparationScopeType =
        PreparationScopeType.valueOf(value.uppercase())

    @TypeConverter
    fun preparationOriginTypeToStorage(value: PreparationOriginType): String = value.name.lowercase()

    @TypeConverter
    fun storageToPreparationOriginType(value: String): PreparationOriginType =
        PreparationOriginType.valueOf(value.uppercase())

    @TypeConverter
    fun preparationSessionStatusToStorage(value: PreparationSessionStatus): String = value.name.lowercase()

    @TypeConverter
    fun storageToPreparationSessionStatus(value: String): PreparationSessionStatus =
        PreparationSessionStatus.valueOf(value.uppercase())

    @TypeConverter
    fun testQuestionSourceToStorage(value: TestQuestionSource): String = value.name.lowercase()

    @TypeConverter
    fun storageToTestQuestionSource(value: String): TestQuestionSource =
        TestQuestionSource.valueOf(value.uppercase())

    @TypeConverter
    fun testAttemptScopeTypeToStorage(value: TestAttemptScopeType): String = value.name.lowercase()

    @TypeConverter
    fun storageToTestAttemptScopeType(value: String): TestAttemptScopeType =
        TestAttemptScopeType.valueOf(value.uppercase())

    @TypeConverter
    fun testAttemptOriginTypeToStorage(value: TestAttemptOriginType): String = value.name.lowercase()

    @TypeConverter
    fun storageToTestAttemptOriginType(value: String): TestAttemptOriginType =
        TestAttemptOriginType.valueOf(value.uppercase())

    @TypeConverter
    fun testAttemptStatusToStorage(value: TestAttemptStatus): String = value.name.lowercase()

    @TypeConverter
    fun storageToTestAttemptStatus(value: String): TestAttemptStatus =
        TestAttemptStatus.valueOf(value.uppercase())

    @TypeConverter
    fun instantToStorage(value: Instant): Long = value.toEpochMilli()

    @TypeConverter
    fun storageToInstant(value: Long): Instant = Instant.ofEpochMilli(value)
}
