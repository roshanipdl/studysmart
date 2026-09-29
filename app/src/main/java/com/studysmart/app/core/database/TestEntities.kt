package com.studysmart.app.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.studysmart.app.core.model.TestAttemptOriginType
import com.studysmart.app.core.model.TestAttemptScopeType
import com.studysmart.app.core.model.TestAttemptStatus
import com.studysmart.app.core.model.TestQuestionSource
import com.studysmart.app.core.model.QuestionType
import java.time.Instant

@Entity(
    tableName = "question_keywords",
    primaryKeys = ["questionId", "normalizedKeyword"],
    foreignKeys = [
        ForeignKey(
            entity = QuestionEntity::class,
            parentColumns = ["id"],
            childColumns = ["questionId"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["questionId", "sortOrder"]),
    ],
)
data class QuestionKeywordEntity(
    val questionId: String,
    val normalizedKeyword: String,
    val weight: Double,
    val sortOrder: Int,
)

@Entity(
    tableName = "user_question_flags",
    primaryKeys = ["userId", "questionId"],
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = QuestionEntity::class,
            parentColumns = ["id"],
            childColumns = ["questionId"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["questionId"]),
    ],
)
data class UserQuestionFlagEntity(
    val userId: String,
    val questionId: String,
    val isImportant: Boolean,
    val createdAt: Instant,
)

@Entity(
    tableName = "test_attempts",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CourseEntity::class,
            parentColumns = ["id"],
            childColumns = ["courseId"],
            onDelete = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = AcademicPeriodEntity::class,
            parentColumns = ["id"],
            childColumns = ["academicPeriodId"],
            onDelete = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = ChapterEntity::class,
            parentColumns = ["id"],
            childColumns = ["chapterId"],
            onDelete = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = TopicEntity::class,
            parentColumns = ["id"],
            childColumns = ["topicId"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["creationRequestId"], unique = true),
        Index(value = ["userId", "status"]),
        Index(value = ["courseId", "academicPeriodId", "status"]),
        Index(value = ["subjectId", "status"]),
        Index(value = ["chapterId", "status"]),
        Index(value = ["topicId", "status"]),
        Index(value = ["scopeType", "scopeReferenceId"]),
    ],
)
data class TestAttemptEntity(
    @androidx.room.PrimaryKey val id: String,
    val userId: String,
    val courseId: String,
    val academicPeriodId: String,
    val subjectId: String?,
    val chapterId: String?,
    val topicId: String?,
    val scopeType: TestAttemptScopeType,
    val scopeReferenceId: String?,
    val originType: TestAttemptOriginType,
    val originReferenceId: String?,
    val creationRequestId: String,
    val requestedQuestionCount: Int,
    val actualQuestionCount: Int,
    val questionSource: TestQuestionSource,
    val selectionSeed: Long?,
    val isTimed: Boolean,
    val timeLimitSeconds: Long?,
    val startedAt: Instant?,
    val submittedAt: Instant?,
    val completedAt: Instant?,
    val timeTakenSeconds: Long?,
    val score: Double?,
    val maxScore: Double?,
    val percentage: Double?,
    val status: TestAttemptStatus,
    val completionReason: String?,
    val evaluatorVersion: String?,
)

@Entity(
    tableName = "test_attempt_questions",
    foreignKeys = [
        ForeignKey(
            entity = TestAttemptEntity::class,
            parentColumns = ["id"],
            childColumns = ["testAttemptId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = QuestionEntity::class,
            parentColumns = ["id"],
            childColumns = ["questionId"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["testAttemptId", "questionId"], unique = true),
        Index(value = ["testAttemptId", "displayOrder"], unique = true),
        Index(value = ["questionId"]),
    ],
)
data class TestAttemptQuestionEntity(
    @androidx.room.PrimaryKey val id: String,
    val testAttemptId: String,
    val questionId: String,
    val displayOrder: Int,
    val questionTextSnapshot: String,
    val referenceAnswerSnapshot: String,
    val questionTypeSnapshot: QuestionType,
    val maxScoreSnapshot: Double,
    val keywordSnapshotJson: String,
    val keywordSnapshotVersion: Int,
)

@Entity(
    tableName = "test_answers",
    foreignKeys = [
        ForeignKey(
            entity = TestAttemptQuestionEntity::class,
            parentColumns = ["id"],
            childColumns = ["testAttemptQuestionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["testAttemptQuestionId"], unique = true),
    ],
)
data class TestAnswerEntity(
    @androidx.room.PrimaryKey val id: String,
    val testAttemptQuestionId: String,
    val answerText: String,
    val selectedOption: String?,
    val isFlagged: Boolean,
    val timeSpentSeconds: Long?,
    val keywordMatchPercent: Double?,
    val normalizedAnswer: String?,
    val matchedKeywordsJson: String?,
    val missingKeywordsJson: String?,
    val score: Double?,
    val maxScore: Double?,
    val requiresReview: Boolean?,
    val updatedAt: Instant,
)
