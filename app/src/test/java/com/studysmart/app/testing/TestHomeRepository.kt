package com.studysmart.app.testing

import com.studysmart.app.feature.home.HomeCourseUiModel
import com.studysmart.app.feature.home.HomeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class TestHomeRepository(
    private val courses: List<HomeCourseUiModel> = emptyList(),
    private val failure: Throwable? = null,
) : HomeRepository {
    override fun observeCourses(): Flow<List<HomeCourseUiModel>> = flow {
        failure?.let { throw it }
        emit(courses)
    }
}
