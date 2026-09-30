package com.studysmart.app.feature.academic

import com.studysmart.app.core.model.CoursePeriodsBrowseModel
import com.studysmart.app.core.model.PeriodSubjectsBrowseModel
import com.studysmart.app.core.model.SubjectChaptersBrowseModel

data class AcademicPeriodUiState(
    val isLoading: Boolean = true,
    val content: CoursePeriodsBrowseModel? = null,
    val isMissingContent: Boolean = false,
    val error: String? = null,
    val isSelecting: Boolean = false,
    val selectionError: String? = null,
)

data class SubjectsUiState(
    val isLoading: Boolean = true,
    val content: PeriodSubjectsBrowseModel? = null,
    val isMissingContent: Boolean = false,
    val error: String? = null,
)

data class ChaptersUiState(
    val isLoading: Boolean = true,
    val content: SubjectChaptersBrowseModel? = null,
    val isMissingContent: Boolean = false,
    val error: String? = null,
)
