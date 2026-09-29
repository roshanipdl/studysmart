package com.studysmart.app.feature.home

import com.studysmart.app.core.data.CurriculumRepository
import com.studysmart.app.core.model.AcademicPeriodType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

data class HomeCourseUiModel(
    val id: String,
    val code: String,
    val shortName: String,
    val name: String,
    val periodCount: Int,
    val structureType: AcademicPeriodType = AcademicPeriodType.SEMESTER,
)

interface HomeRepository {
    fun observeCourses(): Flow<List<HomeCourseUiModel>>
}

/** Empty fallback retained for isolated UI tests. */
class EmptyHomeRepository : HomeRepository {
    override fun observeCourses(): Flow<List<HomeCourseUiModel>> = flowOf(emptyList())
}

class CurriculumHomeRepository(
    private val curriculumRepository: CurriculumRepository,
) : HomeRepository {
    override fun observeCourses(): Flow<List<HomeCourseUiModel>> = curriculumRepository.observeCourses().map { courses ->
        courses.map { course ->
            HomeCourseUiModel(
                id = course.id,
                code = course.code,
                shortName = course.shortName,
                name = course.name,
                periodCount = course.periodCount,
                structureType = course.structureType,
            )
        }
    }
}
