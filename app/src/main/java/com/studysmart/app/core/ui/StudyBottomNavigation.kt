package com.studysmart.app.core.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import com.studysmart.app.core.navigation.AppRoutes

@Composable
fun StudyBottomNavigation(
    currentRoute: String?,
    onDestinationSelected: (String) -> Unit,
) {
    NavigationBar(
        modifier = Modifier.testTag("bottom_navigation"),
        tonalElevation = 0.dp,
    ) {
        AppRoutes.topLevelDestinations.forEach { destination ->
            NavigationBarItem(
                selected = currentRoute == destination.route,
                onClick = { onDestinationSelected(destination.route) },
                icon = {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = destination.label,
                    )
                },
                label = { Text(destination.label) },
                modifier = Modifier.testTag("bottom_navigation_${destination.route}"),
            )
        }
    }
}

private val com.studysmart.app.core.navigation.TopLevelDestination.icon: ImageVector
    get() = when (route) {
        AppRoutes.HOME -> Icons.Outlined.Home
        AppRoutes.COURSES -> Icons.Outlined.School
        AppRoutes.REVIEW -> Icons.Outlined.Schedule
        AppRoutes.PROGRESS -> Icons.Outlined.Insights
        AppRoutes.IMPORTANT -> Icons.Outlined.BookmarkBorder
        else -> Icons.Outlined.Home
    }
