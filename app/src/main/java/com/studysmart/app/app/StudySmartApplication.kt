package com.studysmart.app.app

import android.app.Application

class StudySmartApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = createContainer(this)
    }

    internal fun createContainer(context: Application): AppContainer = AppContainer(context)
}
