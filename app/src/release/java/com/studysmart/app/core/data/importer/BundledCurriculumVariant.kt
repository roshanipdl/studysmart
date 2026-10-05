package com.studysmart.app.core.data.importer

import android.content.Context
import com.studysmart.app.core.common.DispatcherProvider
import com.studysmart.app.core.database.StudySmartDatabase
import java.time.Clock

@Suppress("UNUSED_PARAMETER")
internal fun bundledCurriculumImporter(
    context: Context,
    database: StudySmartDatabase,
    dispatchers: DispatcherProvider,
    clock: Clock,
): CurriculumImporter = NoOpCurriculumImporter
