package com.studysmart.app.feature.preparation

data class PreparationChapterCoverage(val eligibleCount: Int, val reviewedCount: Int) {
    init {
        require(eligibleCount >= 0 && reviewedCount in 0..eligibleCount)
    }
    val isCovered: Boolean get() = eligibleCount > 0 && reviewedCount == eligibleCount
}

internal fun projectChapterCoverage(reviewed: List<Boolean>): PreparationChapterCoverage =
    PreparationChapterCoverage(reviewed.size, reviewed.count { it })
