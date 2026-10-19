package com.billlocket.app.debug

import com.billlocket.app.domain.model.InstantAtZone
import com.billlocket.app.domain.model.TransactionType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Pure deterministic planner for generating the September 2026 Monthly Recap demo dataset.
 *
 * Characteristics:
 * - Deterministic, reproducible, and idempotent
 * - 49 transactions in each of September and October 2026
 * - Every transaction has exactly one source photo
 * - The two months use different amounts, times, and location distributions
 * - Uses Vietnam local time (Asia/Ho_Chi_Minh)
 */
object MonthlyRecapSeedPlanner {

    const val DEMO_MARKER = "DEMO_MONTHLY_RECAP_2026_09_10"
    val ZONE_VIETNAM: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")

    const val MAX_IMAGE_BOUND = 49
    const val MIN_RECOMMENDED_IMAGES = 49

    data class FakeLocation(
        val name: String,
        val latitude: Double,
        val longitude: Double,
    )

    val FAKE_LOCATIONS: List<FakeLocation> = listOf(
        FakeLocation("Hồ Hoàn Kiếm", 21.0285, 105.8542),
        FakeLocation("Tây Hồ", 21.0583, 105.8120),
        FakeLocation("Cầu Giấy", 21.0362, 105.7906),
        FakeLocation("Ba Đình", 21.0338, 105.8141),
        FakeLocation("Hai Bà Trưng", 21.0064, 105.8411),
        FakeLocation("Thanh Xuân", 20.9959, 105.8067),
        FakeLocation("Hà Đông", 20.9712, 105.7789),
        FakeLocation("Long Biên", 21.0458, 105.8932),
        FakeLocation("Nam Từ Liêm", 21.0120, 105.7607),
        FakeLocation("Đống Đa", 21.0181, 105.8291),
    )

    data class TransactionBlueprint(
        val date: LocalDate,
        val time: LocalTime,
        val type: TransactionType,
        val preferredCategoryName: String,
        val fallbackCategoryName: String,
        val amountMinor: Long,
        val baseNote: String,
        val targetPhotoCount: Int,
    )

    val BLUEPRINTS: List<TransactionBlueprint> = listOf(
        TransactionBlueprint(
            date = LocalDate.of(2026, 9, 1),
            time = LocalTime.of(8, 30),
            type = TransactionType.INCOME,
            preferredCategoryName = "Lương",
            fallbackCategoryName = "Thu nhập khác",
            amountMinor = 25_000_000L,
            baseNote = "Lương tháng 9 công ty chuyển khoản",
            targetPhotoCount = 0,
        ),
        TransactionBlueprint(
            date = LocalDate.of(2026, 9, 2),
            time = LocalTime.of(12, 15),
            type = TransactionType.EXPENSE,
            preferredCategoryName = "Ăn uống",
            fallbackCategoryName = "Chi tiêu",
            amountMinor = 145_000L,
            baseNote = "Bún chả phố cổ trưa ngày lễ",
            targetPhotoCount = 1,
        ),
        TransactionBlueprint(
            date = LocalDate.of(2026, 9, 4),
            time = LocalTime.of(18, 45),
            type = TransactionType.EXPENSE,
            preferredCategoryName = "Mua sắm",
            fallbackCategoryName = "Ăn uống",
            amountMinor = 620_000L,
            baseNote = "Mua sắm đồ gia dụng siêu thị",
            targetPhotoCount = 1,
        ),
        TransactionBlueprint(
            date = LocalDate.of(2026, 9, 5),
            time = LocalTime.of(9, 20),
            type = TransactionType.EXPENSE,
            preferredCategoryName = "Cà phê",
            fallbackCategoryName = "Ăn uống",
            amountMinor = 65_000L,
            baseNote = "Cà phê sáng ven hồ",
            targetPhotoCount = 1,
        ),
        TransactionBlueprint(
            date = LocalDate.of(2026, 9, 7),
            time = LocalTime.of(19, 30),
            type = TransactionType.EXPENSE,
            preferredCategoryName = "Ăn uống",
            fallbackCategoryName = "Chi tiêu",
            amountMinor = 480_000L,
            baseNote = "Ăn tối lẩu cùng bạn bè",
            targetPhotoCount = 2,
        ),
        TransactionBlueprint(
            date = LocalDate.of(2026, 9, 9),
            time = LocalTime.of(8, 10),
            type = TransactionType.EXPENSE,
            preferredCategoryName = "Di chuyển",
            fallbackCategoryName = "Xăng xe",
            amountMinor = 90_000L,
            baseNote = "Đổ xăng xe máy đi làm",
            targetPhotoCount = 1,
        ),
        TransactionBlueprint(
            date = LocalDate.of(2026, 9, 11),
            time = LocalTime.of(12, 30),
            type = TransactionType.EXPENSE,
            preferredCategoryName = "Ăn uống",
            fallbackCategoryName = "Chi tiêu",
            amountMinor = 85_000L,
            baseNote = "Cơm trưa văn phòng",
            targetPhotoCount = 1,
        ),
        TransactionBlueprint(
            date = LocalDate.of(2026, 9, 12),
            time = LocalTime.of(15, 40),
            type = TransactionType.EXPENSE,
            preferredCategoryName = "Sách",
            fallbackCategoryName = "Học tập",
            amountMinor = 280_000L,
            baseNote = "Mua sách kỹ thuật và sổ tay",
            targetPhotoCount = 1,
        ),
        TransactionBlueprint(
            date = LocalDate.of(2026, 9, 14),
            time = LocalTime.of(20, 0),
            type = TransactionType.EXPENSE,
            preferredCategoryName = "Giải trí",
            fallbackCategoryName = "Ăn uống",
            amountMinor = 210_000L,
            baseNote = "Vé xem phim cuối tuần",
            targetPhotoCount = 1,
        ),
        TransactionBlueprint(
            date = LocalDate.of(2026, 9, 15),
            time = LocalTime.of(10, 0),
            type = TransactionType.EXPENSE,
            preferredCategoryName = "Điện",
            fallbackCategoryName = "Hóa đơn",
            amountMinor = 850_000L,
            baseNote = "Thanh toán tiền điện sinh hoạt tháng 8",
            targetPhotoCount = 0,
        ),
        TransactionBlueprint(
            date = LocalDate.of(2026, 9, 16),
            time = LocalTime.of(19, 15),
            type = TransactionType.EXPENSE,
            preferredCategoryName = "Ăn uống",
            fallbackCategoryName = "Chi tiêu",
            amountMinor = 560_000L,
            baseNote = "Buffet nướng tối giữa tuần",
            targetPhotoCount = 2,
        ),
        TransactionBlueprint(
            date = LocalDate.of(2026, 9, 18),
            time = LocalTime.of(16, 30),
            type = TransactionType.EXPENSE,
            preferredCategoryName = "Sức khỏe",
            fallbackCategoryName = "Thuốc men",
            amountMinor = 320_000L,
            baseNote = "Mua vitamin và thực phẩm bổ sung",
            targetPhotoCount = 1,
        ),
        TransactionBlueprint(
            date = LocalDate.of(2026, 9, 19),
            time = LocalTime.of(14, 0),
            type = TransactionType.EXPENSE,
            preferredCategoryName = "Nước",
            fallbackCategoryName = "Hóa đơn",
            amountMinor = 125_000L,
            baseNote = "Tiền nước sinh hoạt gia đình",
            targetPhotoCount = 0,
        ),
        // Day 2026-09-20: 3 transactions (Deterministic Most Active Day)
        TransactionBlueprint(
            date = LocalDate.of(2026, 9, 20),
            time = LocalTime.of(8, 30),
            type = TransactionType.EXPENSE,
            preferredCategoryName = "Ăn uống",
            fallbackCategoryName = "Chi tiêu",
            amountMinor = 95_000L,
            baseNote = "Phở bò và quẩy sáng chủ nhật",
            targetPhotoCount = 1,
        ),
        TransactionBlueprint(
            date = LocalDate.of(2026, 9, 20),
            time = LocalTime.of(14, 15),
            type = TransactionType.EXPENSE,
            preferredCategoryName = "Cà phê",
            fallbackCategoryName = "Ăn uống",
            amountMinor = 75_000L,
            baseNote = "Trà sữa và bánh ngọt chiều",
            targetPhotoCount = 1,
        ),
        TransactionBlueprint(
            date = LocalDate.of(2026, 9, 20),
            time = LocalTime.of(19, 45),
            type = TransactionType.EXPENSE,
            preferredCategoryName = "Mua sắm",
            fallbackCategoryName = "Ăn uống",
            amountMinor = 1_450_000L,
            baseNote = "Mua sắm trang phục thu đông",
            targetPhotoCount = 3,
        ),
        TransactionBlueprint(
            date = LocalDate.of(2026, 9, 22),
            time = LocalTime.of(9, 0),
            type = TransactionType.EXPENSE,
            preferredCategoryName = "Di chuyển",
            fallbackCategoryName = "Ăn uống",
            amountMinor = 110_000L,
            baseNote = "Taxi đi gặp đối tác",
            targetPhotoCount = 1,
        ),
        TransactionBlueprint(
            date = LocalDate.of(2026, 9, 25),
            time = LocalTime.of(18, 20),
            type = TransactionType.EXPENSE,
            preferredCategoryName = "Ăn uống",
            fallbackCategoryName = "Chi tiêu",
            amountMinor = 380_000L,
            baseNote = "Bữa tối gia đình sum họp",
            targetPhotoCount = 1,
        ),
        TransactionBlueprint(
            date = LocalDate.of(2026, 9, 27),
            time = LocalTime.of(10, 30),
            type = TransactionType.EXPENSE,
            preferredCategoryName = "Nhà cửa",
            fallbackCategoryName = "Mua sắm",
            amountMinor = 430_000L,
            baseNote = "Đồ trang trí phòng khách",
            targetPhotoCount = 1,
        ),
        TransactionBlueprint(
            date = LocalDate.of(2026, 9, 30),
            time = LocalTime.of(20, 0),
            type = TransactionType.EXPENSE,
            preferredCategoryName = "Ăn uống",
            fallbackCategoryName = "Chi tiêu",
            amountMinor = 1_470_000L,
            baseNote = "Tiệc liên hoan tổng kết tháng 9",
            targetPhotoCount = 2,
        ),
    )

    data class PlannedPhoto(
        val fileName: String,
        val location: FakeLocation,
    )

    data class PlannedTransaction(
        val occurred: InstantAtZone,
        val localDate: LocalDate,
        val type: TransactionType,
        val preferredCategoryName: String,
        val fallbackCategoryName: String,
        val amountMinor: Long,
        val note: String,
        val photos: List<PlannedPhoto>,
    )

    data class SeedPlan(
        val transactions: List<PlannedTransaction>,
        val totalPhotosPlanned: Int,
        val availableImagesCount: Int,
        val isWarningFewImages: Boolean,
        val totalIncomeMinor: Long,
        val totalExpenseMinor: Long,
        val mostActiveDate: LocalDate,
        val mostActiveDateTransactionCount: Int,
    )

    /**
     * Builds a deterministic seed plan from available image filenames.
     * Filenames are sorted alphabetically to avoid filesystem ordering anomalies.
     */
    fun plan(availableImageFileNames: List<String>): SeedPlan {
        require(availableImageFileNames.isNotEmpty()) {
            "Không tìm thấy ảnh demo nào trong thư mục đầu vào."
        }
        require(availableImageFileNames.size == 49) {
            "Bộ ảnh demo phải có đúng 49 ảnh, hiện có ${availableImageFileNames.size}."
        }

        val sortedImages = availableImageFileNames.sorted()
        val plannedTransactions = buildList {
            listOf(
                LocalDate.of(2026, 9, 1) to 30,
                LocalDate.of(2026, 10, 1) to 31,
            ).forEachIndexed { monthIndex, (monthStart, monthDays) ->
                repeat(49) { index ->
                    val date = monthStart.plusDays((index * 3L + monthIndex * 2L) % monthDays)
                    val type = if (index == 0) TransactionType.INCOME else TransactionType.EXPENSE
                    val amount = if (type == TransactionType.INCOME) {
                        25_000_000L + monthIndex * 1_500_000L
                    } else {
                        (75_000L + ((index * 47_000L + monthIndex * 31_000L) % 1_900_000L))
                    }
                    val time = LocalTime.of(
                        7 + ((index * 2 + monthIndex) % 14),
                        (index * 11 + monthIndex * 7) % 60,
                    )
                    val locationPool = if (monthIndex == 0) FAKE_LOCATIONS else FAKE_LOCATIONS.asReversed()
                    val location = locationPool[index % locationPool.size]
                    val dateTime = LocalDateTime.of(date, time).atZone(ZONE_VIETNAM)
                    add(
                        PlannedTransaction(
                            occurred = InstantAtZone(
                                dateTime.toInstant().toEpochMilli(),
                                date.toEpochDay(),
                                ZONE_VIETNAM.id,
                            ),
                            localDate = date,
                            type = type,
                            preferredCategoryName = if (type == TransactionType.INCOME) "Lương" else
                                listOf("Ăn uống", "Mua sắm", "Di chuyển", "Cà phê", "Giải trí")[index % 5],
                            fallbackCategoryName = if (type == TransactionType.INCOME) "Thu nhập khác" else "Ăn uống",
                            amountMinor = amount,
                            note = "Giao dịch demo ${if (monthIndex == 0) "tháng 9" else "tháng 10"} #${index + 1} [$DEMO_MARKER]",
                            photos = listOf(
                                PlannedPhoto(
                                    sortedImages[index],
                                    location,
                                ),
                            ),
                        ),
                    )
                }
            }
        }

        val totalIncome = plannedTransactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amountMinor }
        val totalExpense = plannedTransactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountMinor }
        val mostActive = plannedTransactions.groupBy { it.localDate }.maxByOrNull { it.value.size }

        return SeedPlan(
            transactions = plannedTransactions,
            totalPhotosPlanned = plannedTransactions.sumOf { it.photos.size },
            availableImagesCount = availableImageFileNames.size,
            isWarningFewImages = false,
            totalIncomeMinor = totalIncome,
            totalExpenseMinor = totalExpense,
            mostActiveDate = mostActive?.key ?: LocalDate.of(2026, 9, 20),
            mostActiveDateTransactionCount = mostActive?.value?.size ?: 0,
        )
    }

    fun isDemoTransaction(note: String?): Boolean =
        note?.contains(DEMO_MARKER) == true || note?.contains("DEMO_MONTHLY_RECAP_2026_09") == true
}
