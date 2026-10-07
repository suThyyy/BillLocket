package com.billlocket.app

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.billlocket.app.components.bottombar.BillLocketBottomBar
import com.billlocket.app.domain.model.DraftId
import com.billlocket.app.domain.model.TransactionId
import com.billlocket.app.feature.capture.CaptureScreen
import com.billlocket.app.feature.home.DayGalleryScreen
import com.billlocket.app.feature.home.HomeScreen
import com.billlocket.app.feature.finance.*
import com.billlocket.app.feature.insights.BudgetsScreen
import com.billlocket.app.feature.insights.StatisticsScreen
import com.billlocket.app.feature.location.ImageLocationScreen
import com.billlocket.app.feature.map.MapScreen
import com.billlocket.app.feature.recap.MonthlyRecapScreen
import com.billlocket.app.feature.widget.SettingsHubScreen
import com.billlocket.app.feature.widget.WidgetSettingsScreen
import com.billlocket.app.feature.settings.GeneralSettingsScreen
import com.billlocket.app.feature.settings.StorageBackupScreen
import com.billlocket.app.feature.transaction.detail.TransactionDetailScreen
import com.billlocket.app.feature.transaction.editor.TransactionEditorScreen
import com.billlocket.app.feature.transaction.search.TransactionSearchScreen
import com.billlocket.app.navigation.AppDestination
import com.billlocket.app.navigation.RoutePattern
import com.billlocket.app.navigation.TopLevelDestination
import com.billlocket.app.navigation.newDraftId
import com.billlocket.app.navigation.parseRouteId
import com.billlocket.app.ui.screen.placeholder.PlaceholderDestinationScreen
import com.billlocket.app.widget.WIDGET_ACTION_CAPTURE
import com.billlocket.app.widget.WIDGET_ACTION_DETAIL
import com.billlocket.app.widget.WIDGET_ACTION_TODAY
import com.billlocket.app.widget.WIDGET_ACTION_WEEKLY

@Composable
fun Navigation(navController: NavHostController = rememberNavController(), widgetLaunch: WidgetLaunch? = null) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val selectedTopLevel = TopLevelDestination.entries.firstOrNull { topLevel ->
        currentDestination?.hierarchy?.any { destination ->
            destination.route == topLevel.route ||
                (topLevel == TopLevelDestination.Map && destination.route == RoutePattern.MAP_ROUTE)
        } == true
    }
    LaunchedEffect(widgetLaunch?.token) {
        when (widgetLaunch?.action) {
            WIDGET_ACTION_CAPTURE -> navController.navigate(AppDestination.Capture(newDraftId()).route)
            WIDGET_ACTION_DETAIL -> widgetLaunch.stableId?.let(::parseRouteId)?.let { navController.navigate(AppDestination.TransactionDetail(TransactionId(it)).route) }
            WIDGET_ACTION_TODAY -> {
                val epochDay = widgetLaunch.stableId?.toLongOrNull() ?: java.time.LocalDate.now().toEpochDay()
                navController.navigate(AppDestination.Day(epochDay).route)
            }
            WIDGET_ACTION_WEEKLY -> navController.navigate(AppDestination.Statistics.route)
        }
    }
    Scaffold(
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
        bottomBar = {
            if (selectedTopLevel != null) {
                BillLocketBottomBar(
                    selectedDestination = selectedTopLevel,
                    onDestinationSelected = { navController.navigateTopLevel(it) },
                    onCaptureClick = { navController.navigate(AppDestination.Capture(newDraftId()).route) },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = RoutePattern.HOME,
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            composable(RoutePattern.HOME) {
                HomeScreen(
                    onOpenDay = { navController.navigate(AppDestination.Day(it).route) },
                    onOpenCapture = { navController.navigate(AppDestination.Capture(it).route) },
                    onOpenSearch = { navController.navigate(AppDestination.TransactionSearch.route) },
                    onOpenTransaction = { navController.navigate(AppDestination.TransactionDetail(it).route) },
                    onOpenMonthlyRecap = { navController.navigate(AppDestination.MonthlyRecap(it).route) },
                )
            }
            composable(RoutePattern.STATISTICS) {
                StatisticsScreen(
                    onOpenTransaction = { navController.navigate(AppDestination.TransactionDetail(it).route) },
                    onOpenAllTransactions = { navController.navigate(AppDestination.TransactionSearch.route) },
                    onOpenBudgets = { navController.navigate(AppDestination.Budgets().route) },
                    onOpenMonthlyRecap = { navController.navigate(AppDestination.MonthlyRecap(it).route) },
                )
            }
            composable(
                route = RoutePattern.MAP_ROUTE,
                arguments = listOf(
                    navArgument(RoutePattern.ARG_TRANSACTION_ID) {
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) {
                MapScreen(onOpenTransaction = { navController.navigate(AppDestination.TransactionDetail(it).route) })
            }
            composable(RoutePattern.MORE) {
                MoreScreen(
                    onAccounts = { navController.navigate(AppDestination.Accounts.route) },
                    onCategories = { navController.navigate(AppDestination.Categories().route) },
                    onTransfers = { navController.navigate(AppDestination.Transfers.route) },
                    onBudgets = { navController.navigate(AppDestination.Budgets().route) },
                    onSettings = { navController.navigate(AppDestination.Settings.route) },
                )
            }
            composable(
                route = RoutePattern.DAY,
                arguments = listOf(navArgument(RoutePattern.ARG_EPOCH_DAY) { type = androidx.navigation.NavType.LongType }),
            ) {
                DayGalleryScreen(
                    onBack = navController::navigateUp,
                    onOpenCapture = { navController.navigate(AppDestination.Capture(it).route) },
                    onOpenTransaction = { navController.navigate(AppDestination.TransactionDetail(it).route) },
                )
            }
            composable(RoutePattern.CAPTURE) { entry ->
                val draftId = entry.safeId(RoutePattern.ARG_DRAFT_ID)?.let(::DraftId)
                if (draftId == null) {
                    SafePlaceholder(false, R.string.destination_capture, navController)
                } else {
                    CaptureScreen(
                        onNavigateEditor = { id ->
                            navController.navigate(AppDestination.NewTransaction(id).route) {
                                popUpTo(RoutePattern.CAPTURE) { inclusive = true }
                            }
                        },
                        onNavigateBack = navController::navigateUp,
                    )
                }
            }
            composable(RoutePattern.TRANSACTION_NEW) { entry ->
                val draftId = entry.safeId(RoutePattern.ARG_DRAFT_ID)?.let(::DraftId)
                if (draftId == null) SafePlaceholder(false, R.string.destination_transaction_editor, navController)
                else TransactionEditorScreen(
                    onNavigateCapture = { navController.navigate(AppDestination.Capture(it).route) },
                    onNavigateDetail = { id, _ -> navController.openSavedTransaction(id) },
                    onNavigateBack = navController::navigateUp,
                    onNavigateLocation = { draft, image -> navController.navigate(AppDestination.ImageLocation(draft, image).route) },
                )
            }
            composable(RoutePattern.TRANSACTION_DETAIL) { entry ->
                val transactionId = entry.safeId(RoutePattern.ARG_TRANSACTION_ID)?.let(::TransactionId)
                if (transactionId == null) SafePlaceholder(false, R.string.destination_transaction_detail, navController)
                else TransactionDetailScreen(
                    onNavigateEdit = { transaction, draft ->
                        navController.navigate(AppDestination.EditTransaction(transaction, draft).route)
                    },
                    onNavigateMap = { targetId ->
                        navController.navigate(AppDestination.Map(targetId).route)
                    },
                    onNavigateBack = navController::navigateUp,
                )
            }
            composable(RoutePattern.TRANSACTION_EDIT) { entry ->
                val transactionId = entry.safeId(RoutePattern.ARG_TRANSACTION_ID)?.let(::TransactionId)
                val draftId = entry.safeId(RoutePattern.ARG_DRAFT_ID)?.let(::DraftId)
                if (transactionId == null || draftId == null) {
                    SafePlaceholder(false, R.string.destination_transaction_editor, navController)
                } else TransactionEditorScreen(
                    onNavigateCapture = { navController.navigate(AppDestination.Capture(it).route) },
                    onNavigateDetail = { id, _ -> navController.openSavedTransaction(id) },
                    onNavigateBack = navController::navigateUp,
                    onNavigateLocation = { draft, image -> navController.navigate(AppDestination.ImageLocation(draft, image).route) },
                )
            }
            composable(RoutePattern.TRANSACTION_SEARCH) {
                TransactionSearchScreen(
                    onBack = navController::navigateUp,
                    onOpenTransaction = { navController.navigate(AppDestination.TransactionDetail(it).route) },
                )
            }
            composable(RoutePattern.IMAGE_LOCATION) { entry ->
                val valid = entry.safeId(RoutePattern.ARG_DRAFT_ID) != null && entry.safeId(RoutePattern.ARG_IMAGE_ID) != null
                if (valid) ImageLocationScreen(navController::navigateUp)
                else SafePlaceholder(false, R.string.destination_image_location, navController)
            }
            composable(RoutePattern.ACCOUNTS) {
                AccountsScreen(
                    onCreate = { navController.navigate(AppDestination.EditAccount().route) },
                    onEdit = { navController.navigate(AppDestination.EditAccount(it).route) },
                    onOpen = { navController.navigate(AppDestination.AccountDetail(it).route) },
                    onTransfers = { navController.navigate(AppDestination.Transfers.route) },
                    onCategories = { navController.navigate(AppDestination.Categories().route) },
                    onBudgets = { navController.navigate(AppDestination.Budgets().route) },
                    onSettings = { navController.navigate(AppDestination.Settings.route) },
                )
            }
            composable(RoutePattern.ACCOUNT_DETAIL) { entry ->
                if (entry.safeId(RoutePattern.ARG_ACCOUNT_ID) == null) SafePlaceholder(false, R.string.destination_account_detail, navController)
                else AccountDetailScreen(
                    onBack = navController::navigateUp,
                    onEdit = { navController.navigate(AppDestination.EditAccount(it).route) },
                )
            }
            composable(
                route = RoutePattern.ACCOUNT_EDIT,
                arguments = listOf(navArgument(RoutePattern.ARG_ACCOUNT_ID) { nullable = true; defaultValue = null }),
            ) { entry ->
                val raw = entry.arguments?.getString(RoutePattern.ARG_ACCOUNT_ID)
                if (raw != null && parseRouteId(raw) == null) SafePlaceholder(false, R.string.destination_account_editor, navController)
                else AccountEditorScreen(navController::navigateUp)
            }
            composable(RoutePattern.TRANSFERS) {
                TransfersScreen(
                    onBack = navController::navigateUp,
                    onCreate = { navController.navigate(AppDestination.EditTransfer().route) },
                    onEdit = { navController.navigate(AppDestination.EditTransfer(it).route) },
                )
            }
            composable(
                route = RoutePattern.TRANSFER_EDIT,
                arguments = listOf(navArgument(RoutePattern.ARG_TRANSFER_ID) { nullable = true; defaultValue = null }),
            ) { entry ->
                val raw = entry.arguments?.getString(RoutePattern.ARG_TRANSFER_ID)
                if (raw != null && parseRouteId(raw) == null) SafePlaceholder(false, R.string.destination_transfer_editor, navController)
                else TransferEditorScreen(navController::navigateUp)
            }
            composable(
                route = RoutePattern.CATEGORIES,
                arguments = listOf(navArgument(RoutePattern.ARG_TRANSACTION_TYPE) { nullable = true; defaultValue = null }),
            ) {
                CategoriesScreen(navController::navigateUp)
            }
            composable(
                route = RoutePattern.BUDGETS,
                arguments = listOf(navArgument(RoutePattern.ARG_MONTH_EPOCH_DAY) { type = androidx.navigation.NavType.LongType; defaultValue = -1L }),
            ) {
                BudgetsScreen(navController::navigateUp)
            }
            composable(
                route = RoutePattern.MONTHLY_RECAP_ROUTE,
                arguments = listOf(navArgument(RoutePattern.ARG_MONTH_EPOCH_DAY) { type = androidx.navigation.NavType.LongType; defaultValue = -1L }),
            ) {
                MonthlyRecapScreen(
                    onBack = navController::navigateUp,
                    onOpenTransaction = { id -> navController.navigate(AppDestination.TransactionDetail(id).route) },
                    onOpenCalendar = {
                        navController.navigate(RoutePattern.HOME) {
                            popUpTo(RoutePattern.HOME) { inclusive = false }
                        }
                    },
                )
            }
            composable(RoutePattern.SETTINGS) {
                GeneralSettingsScreen(
                    onBack = navController::navigateUp,
                    onWidget = { navController.navigate(AppDestination.WidgetSettings.route) },
                    onStorage = { navController.navigate(AppDestination.StorageSettings.route) },
                )
            }
            composable(RoutePattern.SETTINGS_WIDGET) {
                WidgetSettingsScreen(navController::navigateUp)
            }
            composable(RoutePattern.SETTINGS_STORAGE) {
                StorageBackupScreen(navController::navigateUp)
            }
            composable(RoutePattern.SETTINGS_BACKUP) {
                StorageBackupScreen(navController::navigateUp)
            }
        }
    }
}

@Composable
private fun SafePlaceholder(valid: Boolean, @Suppress("UNUSED_PARAMETER") titleRes: Int, navController: NavController) {
    check(!valid) { "Implemented destinations must not use a placeholder" }
    PlaceholderDestinationScreen(
        titleRes = R.string.destination_not_found,
        messageRes = R.string.destination_not_found_message,
        onBack = navController::navigateUp,
        onHome = { navController.navigateTopLevel(TopLevelDestination.Home) },
    )
}

private fun NavBackStackEntry.safeId(name: String): String? = arguments?.getString(name)?.let(::parseRouteId)

private fun NavController.navigateTopLevel(destination: TopLevelDestination) {
    val isHome = destination == TopLevelDestination.Home
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = !isHome
    }
}

private fun NavController.openSavedTransaction(id: TransactionId) {
    navigate(AppDestination.TransactionDetail(id).route) {
        popUpTo(RoutePattern.HOME)
        launchSingleTop = true
    }
}
