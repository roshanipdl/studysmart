package com.studysmart.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AcademicPeriodTypeTest {
    @Test
    fun semesterUsesSemesterCopy() {
        assertEquals("Choose Semester", AcademicPeriodType.SEMESTER.screenTitle())
        assertEquals("Semester-based course", AcademicPeriodType.SEMESTER.structureLabel())
    }

    @Test
    fun yearUsesYearCopy() {
        assertEquals("Choose Year", AcademicPeriodType.YEAR.screenTitle())
        assertEquals("Year-based course", AcademicPeriodType.YEAR.structureLabel())
    }
}
