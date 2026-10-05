package com.studysmart.app.core.model

/** One saved learning pool never crosses a course or academic-period boundary. */
data class SavedQuestionScope(
    val courseId: String,
    val academicPeriodId: String,
    val subjectId: String? = null,
    val chapterId: String? = null,
) {
    init {
        require(courseId.isNotBlank() && academicPeriodId.isNotBlank())
        require(subjectId == null || subjectId.isNotBlank())
        require(chapterId == null || chapterId.isNotBlank())
        require(chapterId == null || subjectId != null)
    }
}
