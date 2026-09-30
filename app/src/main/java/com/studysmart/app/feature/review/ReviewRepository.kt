package com.studysmart.app.feature.review

import com.studysmart.app.core.database.PreparationDao
import java.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

data class DueReviewSummary(
    val dueCount: Int,
    val overdueCount: Int,
)

interface ReviewRepository {
    fun observeDueReview(
        courseId: String,
        academicPeriodId: String,
    ): Flow<DueReviewSummary>
}

@OptIn(ExperimentalCoroutinesApi::class)
class RoomReviewRepository(
    private val dao: PreparationDao,
    private val clock: Clock,
    private val userId: String = LOCAL_USER_ID,
    private val clockRefreshMillis: Long = 60_000L,
) : ReviewRepository {
    init {
        require(clockRefreshMillis > 0L) { "clockRefreshMillis must be positive" }
    }

    override fun observeDueReview(
        courseId: String,
        academicPeriodId: String,
    ): Flow<DueReviewSummary> = flow {
        while (true) {
            emit(clock.instant())
            delay(clockRefreshMillis)
        }
    }.flatMapLatest { now -> dao.observeDueCandidates(
        userId = userId,
        courseId = courseId,
        academicPeriodId = academicPeriodId,
        now = now,
    ).map { candidates ->
        DueReviewSummary(
            dueCount = candidates.size,
            overdueCount = candidates.count { it.nextReviewAt?.isBefore(now) == true },
        )
    } }.distinctUntilChanged()

    private companion object {
        const val LOCAL_USER_ID = "local-user"
    }
}
