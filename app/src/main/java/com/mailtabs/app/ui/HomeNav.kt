package com.mailtabs.app.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.mailtabs.app.R

/** Разделы нижней навигации — только реально работающие. */
enum class HomeTab(@StringRes val title: Int, val icon: ImageVector, val selectedIcon: ImageVector) {
    MAIL(R.string.tab_mail, Icons.Outlined.Mail, Icons.Filled.Mail),
    CALENDAR(R.string.tab_calendar, Icons.Outlined.CalendarMonth, Icons.Filled.CalendarMonth),
    SETTINGS(R.string.settings_title, Icons.Outlined.Settings, Icons.Filled.Settings),
}

@Composable
fun HomeNavigationBar(current: HomeTab, onSelect: (HomeTab) -> Unit, unreadMail: Int = 0) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        HomeTab.entries.forEach { tab ->
            val selected = tab == current
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(tab) },
                icon = {
                    if (tab == HomeTab.MAIL && unreadMail > 0) {
                        BadgedBox(badge = { Badge { Text(if (unreadMail > 99) "99+" else unreadMail.toString()) } }) {
                            Icon(if (selected) tab.selectedIcon else tab.icon, null)
                        }
                    } else {
                        Icon(if (selected) tab.selectedIcon else tab.icon, null)
                    }
                },
                label = { Text(stringResource(tab.title)) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            )
        }
    }
}
