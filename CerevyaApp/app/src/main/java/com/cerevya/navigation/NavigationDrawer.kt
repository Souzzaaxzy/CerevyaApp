package com.cerevya.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.material3.Divider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cerevya.ui.components.DrawerItem
import com.cerevya.ui.components.DrawerMenuItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun NavigationDrawerContent(
    drawerState: DrawerState,
    scope: CoroutineScope,
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    ModalDrawerSheet(
        modifier = Modifier.fillMaxHeight()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(vertical = 24.dp)
        ) {
            Text(
                text = "Cerevya",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )
            
            Text(
                text = "Seu segundo cérebro digital",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            
            Divider(
                modifier = Modifier.padding(vertical = 16.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )

            DrawerMenuItem.entries.forEach { item ->
                val route = when (item) {
                    DrawerMenuItem.CHAT -> Screen.Chat.route
                    DrawerMenuItem.MEMORIES -> Screen.Memory.route
                    DrawerMenuItem.SETTINGS -> Screen.Settings.route
                }
                
                DrawerItem(
                    item = item,
                    isSelected = currentRoute == route,
                    onClick = {
                        scope.launch {
                            drawerState.close()
                        }
                        onNavigate(route)
                    }
                )
            }
        }
    }
}
