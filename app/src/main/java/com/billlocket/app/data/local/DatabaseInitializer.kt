package com.billlocket.app.data.local

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseInitializer {
    const val INITIAL_ACCOUNT_ID = "00000000-0000-4000-8000-000000000001"
    const val FOOD_CATEGORY_ID = "10000000-0000-4000-8000-000000000001"
    const val SHOPPING_CATEGORY_ID = "10000000-0000-4000-8000-000000000002"
    const val TRANSPORT_CATEGORY_ID = "10000000-0000-4000-8000-000000000003"
    const val HOME_CATEGORY_ID = "10000000-0000-4000-8000-000000000004"
    const val SALARY_CATEGORY_ID = "20000000-0000-4000-8000-000000000001"
    const val BONUS_CATEGORY_ID = "20000000-0000-4000-8000-000000000002"

    val callback =
        object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                installGuards(db)
                seedDefaults(db)
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                db.setForeignKeyConstraintsEnabled(true)
                installGuards(db)
                seedDefaults(db)
            }
        }

    private fun installGuards(db: SupportSQLiteDatabase) {
        guard(
            db,
            table = "accounts",
            condition =
                "trim(NEW.name) = '' OR trim(NEW.normalized_name) = '' OR NEW.type NOT IN ('CASH','BANK','EWALLET') OR NEW.sort_order < 0 OR length(NEW.currency_code) <> 3",
        )
        guard(
            db,
            table = "categories",
            condition =
                "trim(NEW.name) = '' OR trim(NEW.normalized_name) = '' OR NEW.transaction_type NOT IN ('INCOME','EXPENSE') OR NEW.sort_order < 0",
        )
        guard(
            db,
            table = "transactions",
            condition =
                "NEW.amount_minor <= 0 OR NEW.type NOT IN ('INCOME','EXPENSE') OR length(NEW.currency_code) <> 3 OR trim(NEW.zone_id) = '' OR EXISTS (SELECT 1 FROM categories c WHERE c.id = NEW.category_id AND c.transaction_type <> NEW.type)",
        )
        guard(
            db,
            table = "transaction_images",
            condition =
                "NEW.position < 0 OR NEW.width_px <= 0 OR NEW.height_px <= 0 OR NEW.size_bytes < 0 OR trim(NEW.content_uri) = '' OR trim(NEW.mime_type) = '' OR (NEW.latitude IS NULL) <> (NEW.longitude IS NULL) OR NEW.latitude < -90 OR NEW.latitude > 90 OR NEW.longitude < -180 OR NEW.longitude > 180 OR (NEW.accuracy_meters IS NOT NULL AND NEW.accuracy_meters <= 0) OR (NEW.latitude IS NOT NULL AND (NEW.location_source NOT IN ('CURRENT','EXIF','MANUAL') OR NEW.location_confirmed_at IS NULL)) OR (NEW.latitude IS NULL AND (NEW.accuracy_meters IS NOT NULL OR NEW.place_name IS NOT NULL OR NEW.location_source IS NOT NULL OR NEW.location_confirmed_at IS NOT NULL))",
        )
        guard(
            db,
            table = "transfers",
            condition =
                "NEW.amount_minor <= 0 OR NEW.from_account_id = NEW.to_account_id OR length(NEW.currency_code) <> 3 OR trim(NEW.zone_id) = ''",
        )
        guard(
            db,
            table = "budgets",
            condition =
                "NEW.limit_minor <= 0 OR NEW.warning_percent < 1 OR NEW.warning_percent > 99 OR length(NEW.currency_code) <> 3 OR (NEW.category_id IS NULL AND NEW.scope_key <> 'OVERALL') OR (NEW.category_id IS NOT NULL AND NEW.scope_key <> NEW.category_id) OR EXISTS (SELECT 1 FROM categories c WHERE c.id = NEW.category_id AND c.transaction_type <> 'EXPENSE')",
        )
        guard(
            db,
            table = "budget_threshold_events",
            condition = "NEW.threshold_percent < 1 OR NEW.threshold_percent > 100",
        )
    }

    private fun guard(db: SupportSQLiteDatabase, table: String, condition: String) {
        listOf("insert" to "INSERT", "update" to "UPDATE").forEach { (suffix, operation) ->
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS ${table}_${suffix}_guard
                BEFORE $operation ON $table
                BEGIN
                    SELECT CASE WHEN $condition THEN RAISE(ABORT, 'invalid $table row') END;
                END
                """.trimIndent()
            )
        }
    }

    private fun seedDefaults(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            INSERT OR IGNORE INTO accounts
              (id, name, normalized_name, type, opening_balance_minor, currency_code, sort_order, is_archived, created_at, updated_at)
            VALUES (?, 'Tiền mặt', 'tiền mặt', 'CASH', 0, 'VND', 0, 0, 0, 0)
            """.trimIndent(),
            arrayOf(INITIAL_ACCOUNT_ID),
        )
        val categories =
            listOf(
                // 31 Expense Categories
                arrayOf(FOOD_CATEGORY_ID, "Ăn uống", "ăn uống", "EXPENSE", 0xFFFFB300L, "restaurant", 0),
                arrayOf(SHOPPING_CATEGORY_ID, "Mua sắm", "mua sắm", "EXPENSE", 0xFFFF7043L, "shopping_bag", 1),
                arrayOf(TRANSPORT_CATEGORY_ID, "Di chuyển", "di chuyển", "EXPENSE", 0xFF42A5F5L, "directions_bus", 2),
                arrayOf(HOME_CATEGORY_ID, "Nhà cửa", "nhà cửa", "EXPENSE", 0xFF66BB6AL, "home", 3),
                arrayOf("10000000-0000-4000-8000-000000000005", "Cà phê", "cà phê", "EXPENSE", 0xFF8D6E63L, "local_cafe", 4),
                arrayOf("10000000-0000-4000-8000-000000000006", "Quần áo", "quần áo", "EXPENSE", 0xFFFFD54FL, "checkroom", 5),
                arrayOf("10000000-0000-4000-8000-000000000007", "Xăng xe", "xăng xe", "EXPENSE", 0xFFFF5722L, "local_gas_station", 6),
                arrayOf("10000000-0000-4000-8000-000000000008", "Gửi xe", "gửi xe", "EXPENSE", 0xFF29B6F6L, "local_parking", 7),
                arrayOf("10000000-0000-4000-8000-000000000009", "Điện", "điện", "EXPENSE", 0xFFFFCA28L, "bolt", 8),
                arrayOf("10000000-0000-4000-8000-00000000000a", "Nước", "nước", "EXPENSE", 0xFF26C6DAL, "water_drop", 9),
                arrayOf("10000000-0000-4000-8000-00000000000b", "Internet", "internet", "EXPENSE", 0xFF26A69AL, "wifi", 10),
                arrayOf("10000000-0000-4000-8000-00000000000c", "Điện thoại", "điện thoại", "EXPENSE", 0xFF78909CL, "phone_android", 11),
                arrayOf("10000000-0000-4000-8000-00000000000d", "Học tập", "học tập", "EXPENSE", 0xFF5C6BC0L, "school", 12),
                arrayOf("10000000-0000-4000-8000-00000000000e", "Sách", "sách", "EXPENSE", 0xFFA1887FL, "menu_book", 13),
                arrayOf("10000000-0000-4000-8000-00000000000f", "Làm đẹp", "làm đẹp", "EXPENSE", 0xFFEC407AL, "spa", 14),
                arrayOf("10000000-0000-4000-8000-000000000010", "Sức khỏe", "sức khỏe", "EXPENSE", 0xFFEF5350L, "fitness_center", 15),
                arrayOf("10000000-0000-4000-8000-000000000011", "Thuốc men", "thuốc men", "EXPENSE", 0xFFFF7043L, "medical_services", 16),
                arrayOf("10000000-0000-4000-8000-000000000012", "Giải trí", "giải trí", "EXPENSE", 0xFFAB47BCL, "sports_esports", 17),
                arrayOf("10000000-0000-4000-8000-000000000013", "Du lịch", "du lịch", "EXPENSE", 0xFF29B6F6L, "flight", 18),
                arrayOf("10000000-0000-4000-8000-000000000014", "Quà tặng", "quà tặng", "EXPENSE", 0xFFF06292L, "redeem", 19),
                arrayOf("10000000-0000-4000-8000-000000000015", "Gia đình", "gia đình", "EXPENSE", 0xFFFFA726L, "family_restroom", 20),
                arrayOf("10000000-0000-4000-8000-000000000016", "Con cái", "con cái", "EXPENSE", 0xFFFFB74DL, "child_care", 21),
                arrayOf("10000000-0000-4000-8000-000000000017", "Thú cưng", "thú cưng", "EXPENSE", 0xFF81C784L, "pets", 22),
                arrayOf("10000000-0000-4000-8000-000000000018", "Công việc", "công việc", "EXPENSE", 0xFF78909CL, "work", 23),
                arrayOf("10000000-0000-4000-8000-000000000019", "Văn phòng phẩm", "văn phòng phẩm", "EXPENSE", 0xFF4DB6ACL, "edit_note", 24),
                arrayOf("10000000-0000-4000-8000-00000000001a", "Phí dịch vụ", "phí dịch vụ", "EXPENSE", 0xFF42A5F5L, "receipt_long", 25),
                arrayOf("10000000-0000-4000-8000-00000000001b", "Bảo hiểm", "bảo hiểm", "EXPENSE", 0xFF66BB6AL, "verified_user", 26),
                arrayOf("10000000-0000-4000-8000-00000000001c", "Trả góp", "trả góp", "EXPENSE", 0xFFFFB300L, "credit_score", 27),
                arrayOf("10000000-0000-4000-8000-00000000001d", "Nợ", "nợ", "EXPENSE", 0xFFEF5350L, "credit_card", 28),
                arrayOf("10000000-0000-4000-8000-00000000001e", "Thuế/Phí", "thuế/phí", "EXPENSE", 0xFF90A4AEL, "account_balance", 29),
                arrayOf("10000000-0000-4000-8000-00000000001f", "Khác", "khác", "EXPENSE", 0xFF9E9E9EL, "more_horiz", 30),

                // 14 Income Categories
                arrayOf(SALARY_CATEGORY_ID, "Lương", "lương", "INCOME", 0xFF26A69AL, "payments", 0),
                arrayOf(BONUS_CATEGORY_ID, "Thưởng", "thưởng", "INCOME", 0xFF7E57C2L, "card_giftcard", 1),
                arrayOf("20000000-0000-4000-8000-000000000003", "Làm thêm", "làm thêm", "INCOME", 0xFF42A5F5L, "schedule", 2),
                arrayOf("20000000-0000-4000-8000-000000000004", "Kinh doanh", "kinh doanh", "INCOME", 0xFFFFA726L, "store", 3),
                arrayOf("20000000-0000-4000-8000-000000000005", "Freelance", "freelance", "INCOME", 0xFF26C6DAL, "laptop", 4),
                arrayOf("20000000-0000-4000-8000-000000000006", "Đầu tư", "đầu tư", "INCOME", 0xFF66BB6AL, "show_chart", 5),
                arrayOf("20000000-0000-4000-8000-000000000007", "Lãi tiết kiệm", "lãi tiết kiệm", "INCOME", 0xFFFFD54FL, "savings", 6),
                arrayOf("20000000-0000-4000-8000-000000000008", "Cổ tức", "cổ tức", "INCOME", 0xFF81C784L, "trending_up", 7),
                arrayOf("20000000-0000-4000-8000-000000000009", "Hoàn tiền", "hoàn tiền", "INCOME", 0xFFFF7043L, "replay", 8),
                arrayOf("20000000-0000-4000-8000-00000000000a", "Bán đồ", "bán đồ", "INCOME", 0xFFFFB74DL, "sell", 9),
                arrayOf("20000000-0000-4000-8000-00000000000b", "Quà tặng", "quà tặng", "INCOME", 0xFFF06292L, "redeem", 10),
                arrayOf("20000000-0000-4000-8000-00000000000c", "Trợ cấp", "trợ cấp", "INCOME", 0xFF4DB6ACL, "volunteer_activism", 11),
                arrayOf("20000000-0000-4000-8000-00000000000d", "Thu nợ", "thu nợ", "INCOME", 0xFF5C6BC0L, "price_check", 12),
                arrayOf("20000000-0000-4000-8000-00000000000e", "Khác", "khác", "INCOME", 0xFF9E9E9EL, "more_horiz", 13),
            )
        categories.forEach { values ->
            db.execSQL(
                """
                INSERT OR IGNORE INTO categories
                  (id, name, normalized_name, transaction_type, color_argb, icon_key, sort_order, is_default, is_archived, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, 1, 0, 0, 0)
                """.trimIndent(),
                values,
            )
        }
    }
}
