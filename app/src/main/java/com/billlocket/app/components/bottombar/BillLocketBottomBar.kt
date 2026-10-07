package com.billlocket.app.components.bottombar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.billlocket.app.R
import com.billlocket.app.navigation.TopLevelDestination

private data class NavigationItem(
    val destination: TopLevelDestination,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

private val leadingItems = listOf(
    NavigationItem(TopLevelDestination.Home, R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home),
    NavigationItem(TopLevelDestination.Statistics, R.string.nav_statistics, Icons.Filled.BarChart, Icons.Outlined.BarChart),
)

private val trailingItems = listOf(
    NavigationItem(TopLevelDestination.Accounts, R.string.nav_accounts, Icons.Filled.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet),
    NavigationItem(TopLevelDestination.Map, R.string.nav_map, Icons.Filled.Map, Icons.Outlined.Map),
)

@Composable
fun BillLocketBottomBar(
    selectedDestination: TopLevelDestination,
    onDestinationSelected: (TopLevelDestination) -> Unit,
    onCaptureClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().height(72.dp),
            shape = RoundedCornerShape(36.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.98f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
            shadowElevation = 12.dp,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                leadingItems.forEach { item ->
                    DestinationItem(item, selectedDestination, onDestinationSelected, Modifier.weight(1f))
                }
                Box(modifier = Modifier.weight(1.12f), contentAlignment = Alignment.Center) {
                    Surface(
                        onClick = onCaptureClick,
                        modifier = Modifier.size(62.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.background,
                        contentColor = MaterialTheme.colorScheme.primary,
                        border = BorderStroke(4.dp, MaterialTheme.colorScheme.primary),
                        shadowElevation = 6.dp,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.AddAPhoto, stringResource(R.string.nav_capture), Modifier.size(27.dp))
                        }
                    }
                }
                trailingItems.forEach { item ->
                    DestinationItem(item, selectedDestination, onDestinationSelected, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DestinationItem(
    item: NavigationItem,
    selectedDestination: TopLevelDestination,
    onDestinationSelected: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = item.destination == selectedDestination
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier
            .clickable { onDestinationSelected(item.destination) }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
            contentDescription = stringResource(item.labelRes),
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = stringResource(item.labelRes),
            color = tint,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
        if (selected) {
            Box(
                modifier = Modifier
                    .width(16.dp)
                    .height(2.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(1.dp)),
            )
        } else {
            Spacer(modifier = Modifier.height(2.dp))
        }
    }
}
