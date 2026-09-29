package com.studysmart.app.feature.review

import com.studysmart.app.core.database.PreparationDao
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class DueReviewSummary(
    val dueCount: Int,
    val overdueCount: Int,
)

interface ReviewRepository {
    fun observeDueReview(
        courseId: String,
        academicPeriodId: String,
        now: Instant,
    ): Flow<DueReviewSummary>
}

class RoomReviewRepository(
    private val dao: PreparationDao,
    private val userId: String = LOCAL_USER_ID,
) : ReviewRepository {
    override fun observeDueReview(
        courseId: String,
        academicPeriodId: String,
        now: Instant,
    ): Flow<DueReviewSummary> = dao.observeDueCandidates(
        userId = userId,
        courseId = courseId,
        academicPeriodId = academicPeriodId,
        now = now,
    ).map { candidates ->
        DueReviewSummary(
            dueCount = candidates.size,
            overdueCount = candidates.count { it.nextReviewAt?.isBefore(now) == true },
        )
    }

    private companion object {
        const val LOCAL_USER_ID = "local-user"
    }
}
