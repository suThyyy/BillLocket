package com.billlocket.app.navigation

import com.billlocket.app.domain.model.AccountId
import com.billlocket.app.domain.model.DraftId
import com.billlocket.app.domain.model.TransactionId
import com.billlocket.app.domain.model.TransactionImageId
import com.billlocket.app.domain.model.TransactionType
import com.billlocket.app.domain.model.TransferId
import java.util.UUID

private val safeRouteId = Regex("[A-Za-z0-9._~-]{1,128}")

fun parseRouteId(value: String?): String? = value?.takeIf(safeRouteId::matches)

fun newDraftId(): DraftId = DraftId(UUID.randomUUID().toString())

sealed interface AppDestination {
    val route: String

    data object Home : AppDestination { override val route = RoutePattern.HOME }
    data object Statistics : AppDestination { override val route = RoutePattern.STATISTICS }
    data class Map(
        val transactionId: TransactionId? = null,
        val imageId: TransactionImageId? = null,
    ) : AppDestination {
        override val route = buildString {
            append(RoutePattern.MAP)
            val params = mutableListOf<String>()
            transactionId?.let { params.add("${RoutePattern.ARG_TRANSACTION_ID}=${it.value.requireRouteId()}") }
            imageId?.let { params.add("${RoutePattern.ARG_IMAGE_ID}=${it.value.requireRouteId()}") }
            if (params.isNotEmpty()) {
                append("?")
                append(params.joinToString("&"))
            }
        }
    }
    data object More : AppDestination { override val route = RoutePattern.MORE }
    data object Accounts : AppDestination { override val route = RoutePattern.ACCOUNTS }
    data object Transfers : AppDestination { override val route = RoutePattern.TRANSFERS }
    data object Settings : AppDestination { override val route = RoutePattern.SETTINGS }
    data object WidgetSettings : AppDestination { override val route = RoutePattern.SETTINGS_WIDGET }
    data object StorageSettings : AppDestination { override val route = RoutePattern.SETTINGS_STORAGE }
    data object BackupSettings : AppDestination { override val route = RoutePattern.SETTINGS_BACKUP }
    data object TransactionSearch : AppDestination { override val route = RoutePattern.TRANSACTION_SEARCH }

    data class Day(val epochDay: Long) : AppDestination {
        override val route = "day/$epochDay"
    }

    data class Capture(val draftId: DraftId) : AppDestination {
        override val route = "capture/${draftId.value.requireRouteId()}"
    }

    data class NewTransaction(val draftId: DraftId) : AppDestination {
        override val route = "transaction/new/${draftId.value.requireRouteId()}"
    }

    data class TransactionDetail(val transactionId: TransactionId) : AppDestination {
        override val route = "transaction/${transactionId.value.requireRouteId()}"
    }

    data class EditTransaction(val transactionId: TransactionId, val draftId: DraftId) : AppDestination {
        override val route =
            "transaction/${transactionId.value.requireRouteId()}/edit/${draftId.value.requireRouteId()}"
    }

    data class ImageLocation(val draftId: DraftId, val imageId: TransactionImageId) : AppDestination {
        override val route =
            "image-location/${draftId.value.requireRouteId()}/${imageId.value.requireRouteId()}"
    }

    data class AccountDetail(val accountId: AccountId) : AppDestination {
        override val route = "account/${accountId.value.requireRouteId()}"
    }

    data class EditAccount(val accountId: AccountId? = null) : AppDestination {
        override val route = accountId?.let { "account/edit?accountId=${it.value.requireRouteId()}" } ?: "account/edit"
    }

    data class EditTransfer(val transferId: TransferId? = null) : AppDestination {
        override val route = transferId?.let { "transfer/edit?transferId=${it.value.requireRouteId()}" } ?: "transfer/edit"
    }

    data class Categories(val transactionType: TransactionType? = null) : AppDestination {
        override val route = transactionType?.let { "categories?transactionType=${it.name}" } ?: "categories"
    }

    data class Budgets(val monthEpochDay: Long? = null) : AppDestination {
        override val route = monthEpochDay?.let { "budgets?monthEpochDay=$it" } ?: "budgets"
    }

    data class MonthlyRecap(val monthEpochDay: Long? = null) : AppDestination {
        override val route = monthEpochDay?.let { "monthly-recap?${RoutePattern.ARG_MONTH_EPOCH_DAY}=$it" } ?: RoutePattern.MONTHLY_RECAP
    }
}

sealed class TopLevelDestination(val route: String) {
    data object Home : TopLevelDestination(RoutePattern.HOME)
    data object Statistics : TopLevelDestination(RoutePattern.STATISTICS)
    data object Accounts : TopLevelDestination(RoutePattern.ACCOUNTS)
    data object Map : TopLevelDestination(RoutePattern.MAP)
    data object More : TopLevelDestination(RoutePattern.MORE)

    companion object {
        val entries: List<TopLevelDestination>
            get() = listOf(Home, Statistics, Accounts, Map)

        fun fromRoute(route: String?): TopLevelDestination? {
            if (route == null) return null
            return entries.firstOrNull { it.route == route }
                ?: if (route == RoutePattern.MAP || route == RoutePattern.MAP_ROUTE) Map else null
        }
    }
}

object RoutePattern {
    const val ARG_EPOCH_DAY = "epochDay"
    const val ARG_DRAFT_ID = "draftId"
    const val ARG_TRANSACTION_ID = "transactionId"
    const val ARG_IMAGE_ID = "imageId"
    const val ARG_ACCOUNT_ID = "accountId"
    const val ARG_TRANSFER_ID = "transferId"
    const val ARG_TRANSACTION_TYPE = "transactionType"
    const val ARG_MONTH_EPOCH_DAY = "monthEpochDay"

    const val HOME = "home"
    const val DAY = "day/{$ARG_EPOCH_DAY}"
    const val STATISTICS = "statistics"
    const val CAPTURE = "capture/{$ARG_DRAFT_ID}"
    const val TRANSACTION_NEW = "transaction/new/{$ARG_DRAFT_ID}"
    const val TRANSACTION_DETAIL = "transaction/{$ARG_TRANSACTION_ID}"
    const val TRANSACTION_EDIT = "transaction/{$ARG_TRANSACTION_ID}/edit/{$ARG_DRAFT_ID}"
    const val TRANSACTION_SEARCH = "transactions/search"
    const val IMAGE_LOCATION = "image-location/{$ARG_DRAFT_ID}/{$ARG_IMAGE_ID}"
    const val MAP = "map"
    const val MAP_ROUTE = "map?$ARG_TRANSACTION_ID={$ARG_TRANSACTION_ID}&$ARG_IMAGE_ID={$ARG_IMAGE_ID}"
    const val MORE = "more"
    const val ACCOUNTS = "accounts"
    const val ACCOUNT_DETAIL = "account/{$ARG_ACCOUNT_ID}"
    const val ACCOUNT_EDIT = "account/edit?$ARG_ACCOUNT_ID={$ARG_ACCOUNT_ID}"
    const val TRANSFERS = "transfers"
    const val TRANSFER_EDIT = "transfer/edit?$ARG_TRANSFER_ID={$ARG_TRANSFER_ID}"
    const val CATEGORIES = "categories?$ARG_TRANSACTION_TYPE={$ARG_TRANSACTION_TYPE}"
    const val BUDGETS = "budgets?$ARG_MONTH_EPOCH_DAY={$ARG_MONTH_EPOCH_DAY}"
    const val SETTINGS = "settings"
    const val SETTINGS_WIDGET = "settings/widget"
    const val SETTINGS_STORAGE = "settings/storage"
    const val SETTINGS_BACKUP = "settings/backup"
    const val MONTHLY_RECAP = "monthly-recap"
    const val MONTHLY_RECAP_ROUTE = "monthly-recap?$ARG_MONTH_EPOCH_DAY={$ARG_MONTH_EPOCH_DAY}"
}

private fun String.requireRouteId(): String =
    requireNotNull(parseRouteId(this)) { "ID is not safe for a navigation route" }
