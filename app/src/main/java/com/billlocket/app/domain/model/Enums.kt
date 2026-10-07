package com.billlocket.app.domain.model

enum class TransactionType { INCOME, EXPENSE }

enum class AccountType { CASH, BANK, EWALLET }

enum class LocationSource { CURRENT, EXIF, MANUAL }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class AmountVisibility { SHOWN, HIDDEN, OBSCURED }

enum class WidgetOpenAction { TRANSACTION_DETAIL, QUICK_CAPTURE }
