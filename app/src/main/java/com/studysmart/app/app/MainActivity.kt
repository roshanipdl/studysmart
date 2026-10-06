package com.studysmart.app.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.studysmart.app.core.ui.StudySmartTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.studysmart.app.core.data.StudyPreferences

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val preferences by (application as StudySmartApplication).container.preferences.preferences
                .collectAsStateWithLifecycle(initialValue = StudyPreferences())
            StudySmartTheme(darkTheme = preferences.theme.isDark(androidx.compose.foundation.isSystemInDarkTheme())) {
                StudySmartApp()
            }
        }
    }
}
