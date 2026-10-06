package com.studysmart.app.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.studysmart.app.core.model.AcademicPeriodType
import com.studysmart.app.core.model.QuestionType
import java.time.Instant

@Entity(tableName = "users")
data class UserEntity(
    @androidx.room.PrimaryKey val id: String,
    val displayName: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)

@Entity(
    tableName = "courses",
    indices = [Index(value = ["isActive", "sortOrder"])],
)
data class CourseEntity(
    @androidx.room.PrimaryKey val id: String,
    val code: String,
    val name: String,
    val shortName: String,
    val structureType: AcademicPeriodType,
    val summary: String,
    val isActive: Boolean,
    val sortOrder: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
)

@Entity(
    tableName = "academic_periods",
    foreignKeys = [
        ForeignKey(
            entity = CourseEntity::class,
            parentColumns = ["id"],
            childColumns = ["courseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["courseId", "isActive", "sortOrder"]),
        Index(value = ["courseId", "periodNumber"], unique = true),
    ],
)
data class AcademicPeriodEntity(
    @androidx.room.PrimaryKey val id: String,
    val courseId: String,
    val periodNumber: Int,
    val periodType: AcademicPeriodType,
    val name: String,
    val shortName: String,
    val sortOrder: Int,
    val isActive: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)

@Entity(
    tableName = "subjects",
    foreignKeys = [
        ForeignKey(
            entity = AcademicPeriodEntity::class,
            parentColumns = ["id"],
            childColumns = ["academicPeriodId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["academicPeriodId", "isActive", "sortOrder"]),
        Index(value = ["academicPeriodId", "code"], unique = true),
    ],
)
data class SubjectEntity(
    @androidx.room.PrimaryKey val id: String,
    val academicPeriodId: String,
    val code: String,
    val name: String,
    val description: String,
    val sortOrder: Int,
    val isActive: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)

@Entity(
    tableName = "chapters",
    foreignKeys = [
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["subjectId", "isActive", "sortOrder"]),
        Index(value = ["subjectId", "chapterNumber"], unique = true),
    ],
)
data class ChapterEntity(
    @androidx.room.PrimaryKey val id: String,
    val subjectId: String,
    val chapterNumber: Int,
    val title: String,
    val description: String,
    val sortOrder: Int,
    val isActive: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)

@Entity(
    tableName = "topics",
    foreignKeys = [
        ForeignKey(
            entity = ChapterEntity::class,
            parentColumns = ["id"],
            childColumns = ["chapterId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["chapterId", "isActive", "sortOrder"]),
        Index(value = ["chapterId", "title"], unique = true),
    ],
)
data class TopicEntity(
    @androidx.room.PrimaryKey val id: String,
    val chapterId: String,
    val title: String,
    val description: String,
    val sortOrder: Int,
    val isActive: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)

@Entity(
    tableName = "questions",
    foreignKeys = [
        ForeignKey(
            entity = ChapterEntity::class,
            parentColumns = ["id"],
            childColumns = ["chapterId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TopicEntity::class,
            parentColumns = ["id"],
            childColumns = ["topicId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["chapterId", "isActive", "sortOrder"]),
        Index(value = ["topicId"]),
    ],
)
data class QuestionEntity(
    @androidx.room.PrimaryKey val id: String,
    val chapterId: String,
    val topicId: String?,
    val question: String,
    val answer: String,
    val questionType: QuestionType,
    val difficulty: String,
    val maxScore: Double,
    val explanation: String,
    val isActive: Boolean,
    val sortOrder: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
    @androidx.room.ColumnInfo(defaultValue = "'plain_text'")
    val answerFormat: com.studysmart.app.core.model.AnswerFormat = com.studysmart.app.core.model.AnswerFormat.PLAIN_TEXT,
)
