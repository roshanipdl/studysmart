package com.studysmart.app.app

import android.app.Application
import com.studysmart.app.core.common.DefaultDispatcherProvider
import com.studysmart.app.core.database.StudySmartDatabase
import com.studysmart.app.core.data.importer.bundledCurriculumImporter
import java.time.Clock

class StudySmartApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = createContainer(this)
    }

    internal fun createContainer(context: Application): AppContainer {
        val database = StudySmartDatabase.create(context)
        val clock = Clock.systemUTC()
        return AppContainer(context, database = database, clock = clock,
            curriculumImporter = bundledCurriculumImporter(context, database, DefaultDispatcherProvider, clock))
    }
}
