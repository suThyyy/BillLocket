package com.billlocket.app.locale

import android.content.Context
import androidx.annotation.StringRes
import com.billlocket.app.R
import com.billlocket.app.data.local.DatabaseInitializer
import com.billlocket.app.domain.model.BuiltInCategoryIds
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.StateFlow

/**
 * Localizes only untouched built-in financial names at the presentation/data boundary.
 *
 * The database remains the source of truth and is never rewritten on a language change.
 * A user-renamed built-in account/category is detected because its stored name no longer
 * matches either canonical Vietnamese or English name, and is therefore preserved verbatim.
 */
@Singleton
class FinancialNameLocalizer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val localeController: AppLocaleController,
) {
    val languageChanges: StateFlow<AppLanguage> = localeController.current

    fun accountName(id: String, storedName: String): String {
        val entry = FinancialNameCatalog.account(id) ?: return storedName
        return localizeIfCanonical(storedName, entry)
    }

    fun categoryName(id: String, storedName: String): String {
        val entry = FinancialNameCatalog.category(id) ?: return storedName
        return localizeIfCanonical(storedName, entry)
    }

    private fun localizeIfCanonical(storedName: String, entry: FinancialNameEntry): String {
        if (entry.canonicalNames.none { it.equals(storedName.trim(), ignoreCase = true) }) {
            return storedName
        }
        val locale = when (localeController.current.value) {
            AppLanguage.VIETNAMESE -> Locale.forLanguageTag(AppLocaleControllerImpl.TAG_VIETNAMESE)
            AppLanguage.ENGLISH -> Locale.forLanguageTag(AppLocaleControllerImpl.TAG_ENGLISH)
            AppLanguage.SYSTEM -> {
                val configuration = android.content.res.Resources.getSystem().configuration
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    configuration.locales[0] ?: Locale.getDefault()
                } else {
                    @Suppress("DEPRECATION")
                    configuration.locale ?: Locale.getDefault()
                }
            }
        }
        val configuration = android.content.res.Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        return context.createConfigurationContext(configuration).getString(entry.stringRes)
    }
}

data class FinancialNameEntry(
    @StringRes val stringRes: Int,
    val canonicalNames: Set<String>,
)

object FinancialNameCatalog {
    private fun entry(@StringRes resource: Int, vietnamese: String, english: String) =
        FinancialNameEntry(resource, setOf(vietnamese, english))

    private val accountNames = mapOf(
        DatabaseInitializer.INITIAL_ACCOUNT_ID to entry(R.string.builtin_account_cash, "Tiền mặt", "Cash"),
    )

    private val categoryNames = mapOf(
        BuiltInCategoryIds.FOOD to entry(R.string.builtin_category_food, "Ăn uống", "Food & Drink"),
        BuiltInCategoryIds.SHOPPING to entry(R.string.builtin_category_shopping, "Mua sắm", "Shopping"),
        BuiltInCategoryIds.TRANSPORT to entry(R.string.builtin_category_transport, "Di chuyển", "Transport"),
        BuiltInCategoryIds.HOME to entry(R.string.builtin_category_home, "Nhà cửa", "Home"),
        BuiltInCategoryIds.COFFEE to entry(R.string.builtin_category_coffee, "Cà phê", "Coffee"),
        BuiltInCategoryIds.CLOTHING to entry(R.string.builtin_category_clothing, "Quần áo", "Clothing"),
        BuiltInCategoryIds.FUEL to entry(R.string.builtin_category_fuel, "Xăng xe", "Fuel"),
        BuiltInCategoryIds.PARKING to entry(R.string.builtin_category_parking, "Gửi xe", "Parking"),
        BuiltInCategoryIds.ELECTRICITY to entry(R.string.builtin_category_electricity, "Điện", "Electricity"),
        BuiltInCategoryIds.WATER to entry(R.string.builtin_category_water, "Nước", "Water"),
        BuiltInCategoryIds.INTERNET to entry(R.string.builtin_category_internet, "Internet", "Internet"),
        BuiltInCategoryIds.PHONE to entry(R.string.builtin_category_phone, "Điện thoại", "Phone"),
        BuiltInCategoryIds.STUDY to entry(R.string.builtin_category_study, "Học tập", "Education"),
        BuiltInCategoryIds.BOOKS to entry(R.string.builtin_category_books, "Sách", "Books"),
        BuiltInCategoryIds.BEAUTY to entry(R.string.builtin_category_beauty, "Làm đẹp", "Beauty"),
        BuiltInCategoryIds.HEALTH to entry(R.string.builtin_category_health, "Sức khỏe", "Health"),
        BuiltInCategoryIds.MEDICINE to entry(R.string.builtin_category_medicine, "Thuốc men", "Medicine"),
        BuiltInCategoryIds.ENTERTAINMENT to entry(R.string.builtin_category_entertainment, "Giải trí", "Entertainment"),
        BuiltInCategoryIds.TRAVEL to entry(R.string.builtin_category_travel, "Du lịch", "Travel"),
        BuiltInCategoryIds.GIFTS to entry(R.string.builtin_category_gifts, "Quà tặng", "Gifts"),
        BuiltInCategoryIds.FAMILY to entry(R.string.builtin_category_family, "Gia đình", "Family"),
        BuiltInCategoryIds.CHILDREN to entry(R.string.builtin_category_children, "Con cái", "Children"),
        BuiltInCategoryIds.PETS to entry(R.string.builtin_category_pets, "Thú cưng", "Pets"),
        BuiltInCategoryIds.WORK to entry(R.string.builtin_category_work, "Công việc", "Work"),
        BuiltInCategoryIds.STATIONERY to entry(R.string.builtin_category_stationery, "Văn phòng phẩm", "Stationery"),
        BuiltInCategoryIds.SERVICE_FEE to entry(R.string.builtin_category_service_fee, "Phí dịch vụ", "Service fees"),
        BuiltInCategoryIds.INSURANCE to entry(R.string.builtin_category_insurance, "Bảo hiểm", "Insurance"),
        BuiltInCategoryIds.INSTALLMENT to entry(R.string.builtin_category_installment, "Trả góp", "Installments"),
        BuiltInCategoryIds.DEBT to entry(R.string.builtin_category_debt, "Nợ", "Debt"),
        BuiltInCategoryIds.TAX_FEES to entry(R.string.builtin_category_tax_fees, "Thuế/Phí", "Taxes/Fees"),
        BuiltInCategoryIds.OTHER_EXPENSE to entry(R.string.builtin_category_other_expense, "Khác", "Other"),
        BuiltInCategoryIds.SALARY to entry(R.string.builtin_category_salary, "Lương", "Salary"),
        BuiltInCategoryIds.BONUS to entry(R.string.builtin_category_bonus, "Thưởng", "Bonus"),
        BuiltInCategoryIds.SIDE_JOB to entry(R.string.builtin_category_side_job, "Làm thêm", "Side job"),
        BuiltInCategoryIds.BUSINESS to entry(R.string.builtin_category_business, "Kinh doanh", "Business"),
        BuiltInCategoryIds.FREELANCE to entry(R.string.builtin_category_freelance, "Freelance", "Freelance"),
        BuiltInCategoryIds.INVESTMENT to entry(R.string.builtin_category_investment, "Đầu tư", "Investment"),
        BuiltInCategoryIds.SAVINGS_INTEREST to entry(R.string.builtin_category_savings_interest, "Lãi tiết kiệm", "Savings interest"),
        BuiltInCategoryIds.DIVIDENDS to entry(R.string.builtin_category_dividends, "Cổ tức", "Dividends"),
        BuiltInCategoryIds.REFUND to entry(R.string.builtin_category_refund, "Hoàn tiền", "Refund"),
        BuiltInCategoryIds.ITEM_SALES to entry(R.string.builtin_category_item_sales, "Bán đồ", "Item sales"),
        BuiltInCategoryIds.INCOME_GIFTS to entry(R.string.builtin_category_income_gifts, "Quà tặng", "Gifts"),
        BuiltInCategoryIds.ALLOWANCE to entry(R.string.builtin_category_allowance, "Trợ cấp", "Allowance"),
        BuiltInCategoryIds.DEBT_REPAYMENT to entry(R.string.builtin_category_debt_repayment, "Thu nợ", "Debt repayment"),
        BuiltInCategoryIds.OTHER_INCOME to entry(R.string.builtin_category_other_income, "Khác", "Other"),
    )

    fun account(id: String): FinancialNameEntry? = accountNames[id]

    fun category(id: String): FinancialNameEntry? = categoryNames[id]
}
