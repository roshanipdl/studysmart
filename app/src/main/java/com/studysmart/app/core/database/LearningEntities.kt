package com.studysmart.app.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.studysmart.app.core.model.LearningState
import com.studysmart.app.core.model.PreparationOriginType
import com.studysmart.app.core.model.PreparationScopeType
import com.studysmart.app.core.model.PreparationSessionStatus
import com.studysmart.app.core.model.QuestionType
import com.studysmart.app.core.model.Rating
import java.time.Instant

@Entity(
    tableName = "preparation_sessions",
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
        Index(value = ["courseId", "status"]),
        Index(value = ["academicPeriodId", "status"]),
        Index(value = ["subjectId", "status"]),
        Index(value = ["chapterId", "status"]),
        Index(value = ["topicId", "status"]),
    ],
)
data class PreparationSessionEntity(
    @androidx.room.PrimaryKey val id: String,
    val userId: String,
    val courseId: String,
    val academicPeriodId: String,
    val subjectId: String?,
    val chapterId: String?,
    val topicId: String?,
    val scopeType: PreparationScopeType,
    val scopeReferenceId: String?,
    val originType: PreparationOriginType,
    val originReferenceId: String?,
    val creationRequestId: String,
    val selectionSeed: Long?,
    val status: PreparationSessionStatus,
    val startedAt: Instant,
    val completedAt: Instant?,
    val updatedAt: Instant,
    val uniqueQuestionCount: Int,
    val reviewCount: Int,
)

@Entity(
    tableName = "preparation_session_items",
    foreignKeys = [
        ForeignKey(
            entity = PreparationSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
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
        Index(value = ["sessionId", "queueSequence", "isCompleted"]),
        Index(value = ["sessionId", "questionId"], unique = true),
        Index(value = ["sessionId", "originalOrder"], unique = true),
        Index(value = ["questionId"]),
    ],
)
data class PreparationSessionItemEntity(
    @androidx.room.PrimaryKey val id: String,
    val sessionId: String,
    val questionId: String,
    val questionSnapshot: String,
    val answerSnapshot: String,
    val questionTypeSnapshot: QuestionType,
    val originalOrder: Int,
    val queueSequence: Int,
    val presentationCount: Int,
    val hardRequeueUsed: Boolean,
    val isRevealed: Boolean,
    val isCompleted: Boolean,
    val lastRating: Rating?,
    val lastPresentedAt: Instant?,
    @androidx.room.ColumnInfo(defaultValue = "'plain_text'")
    val answerFormatSnapshot: com.studysmart.app.core.model.AnswerFormat = com.studysmart.app.core.model.AnswerFormat.PLAIN_TEXT,
)

@Entity(
    tableName = "review_records",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PreparationSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["preparationSessionId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = QuestionEntity::class,
            parentColumns = ["id"],
            childColumns = ["questionId"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["userId", "questionId", "reviewedAt"]),
        Index(value = ["preparationSessionId"]),
        Index(value = ["questionId"]),
    ],
)
data class ReviewRecordEntity(
    @androidx.room.PrimaryKey val id: String,
    val userId: String,
    val preparationSessionId: String?,
    val questionId: String,
    val rating: Rating,
    val responseTimeMs: Long?,
    val reviewedAt: Instant,
)

@Entity(
    tableName = "review_schedules",
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
        Index(value = ["userId", "nextReviewAt"]),
    ],
)
data class ReviewScheduleEntity(
    val userId: String,
    val questionId: String,
    val learningState: LearningState,
    val lastReviewedAt: Instant?,
    val nextReviewAt: Instant?,
    val intervalSeconds: Long,
    val repetitionCount: Int,
    val lapseCount: Int,
)
