# BillLocket — Sổ tay tra cứu và vấn đáp đồ án

> Mục đích: tài liệu tra cứu nhanh khi demo và trả lời câu hỏi của giảng viên. Nội dung được đối chiếu với source code trên nhánh `codex/voice-localization-ai` tại checkpoint BL-047 (`95c7f2c`). BL-048 là vòng kiểm thử thiết bị và quyết định phát hành cuối cùng.

## 0. Cách dùng tài liệu khi báo cáo

- Dùng `Ctrl + F` theo các từ khóa in đậm trong mục “Chỉ mục tra cứu nhanh”.
- Khi được hỏi, đọc phần **Trả lời ngắn** trước. Chỉ mở phần giải thích chi tiết nếu thầy hỏi sâu.
- Không nói AI tự quyết định danh mục. AI chỉ **gợi ý**, người dùng phải bấm **Áp dụng**.
- Không nói app hoàn toàn không cần Internet. Các chức năng tài chính cốt lõi dùng offline; bản đồ nền Google và dịch vụ nhận dạng giọng nói dự phòng có thể cần mạng.
- Không nói mọi kiểm thử thiết bị đã hoàn tất. Automated gate đã đạt; kiểm thử tổng hợp trên máy thật thuộc BL-048.

## 1. Chỉ mục tra cứu nhanh

| Khi thầy hỏi về | Từ khóa nên tìm |
|---|---|
| Ý tưởng và vấn đề giải quyết | `TẦM NHÌN`, `PHOTO-FIRST`, `NHẬT KÝ TÀI CHÍNH` |
| Kiến trúc | `MVVM`, `LAYER`, `REPOSITORY`, `USE CASE`, `HILT` |
| Activity/Fragment/Compose | `SINGLE-ACTIVITY`, `FRAGMENT`, `COMPOSE`, `NAVIGATION` |
| Cơ sở dữ liệu | `ROOM`, `ENTITY`, `DAO`, `FOREIGN KEY`, `FTS4`, `INDEX` |
| Tiền và số dư | `MONEY`, `MINOR UNIT`, `BALANCE`, `TRANSFER` |
| Chụp và lưu ảnh | `CAMERAX`, `MEDIA`, `NORMALIZE`, `EXIF`, `CONTENT URI` |
| Vị trí | `LOCATION`, `PREFETCH`, `30 GIÂY`, `100 M`, `GPS` |
| Bản đồ | `GOOGLE MAPS`, `MARKER`, `CLUSTER`, `API KEY` |
| Widget | `GLANCE`, `WIDGET`, `APPWIDGETPROVIDER` |
| Đa ngôn ngữ | `LOCALE`, `VIETNAMESE`, `ENGLISH`, `FORMATTER` |
| Nhập giọng nói | `SPEECHRECOGNIZER`, `MICROPHONE`, `VOICE` |
| AI nhận diện ảnh | `ML KIT`, `IMAGE LABELING`, `CONFIDENCE`, `WHITELIST` |
| Monthly Recap | `RECAP`, `PRIVACY`, `EXPORT 1080x1350` |
| Sao lưu | `BACKUP`, `ZIP`, `SHA-256`, `ZIP SLIP` |
| Xóa dữ liệu và phục hồi | `SOFT DELETE`, `UNDO`, `RECONCILIATION` |
| Bảo mật và riêng tư | `PRIVACY`, `PERMISSION`, `SECRET`, `FILEPROVIDER` |
| Testing | `UNIT TEST`, `INSTRUMENTED TEST`, `VALIDATION` |
| Môn học áp dụng được gì | `ĐỐI CHIẾU MÔN HỌC` |
| Điểm hạn chế | `GIỚI HẠN`, `BL-048`, `DEFERRED` |

---

## 2. Tóm tắt dự án

### 2.1 TẦM NHÌN và ý tưởng

BillLocket là ứng dụng quản lý thu chi cá nhân theo hướng **photo-first**: ảnh là ký ức chính, dữ liệu tài chính làm cho ký ức đó có thể thống kê và tìm kiếm.

Ứng dụng kết hợp:

- trải nghiệm chụp nhanh với ngôn ngữ hình ảnh vàng–đen đặc trưng của BillLocket;
- luồng quản lý thu chi trực quan, ưu tiên thao tác nhanh và khả năng xem lại theo ảnh;
- lịch ảnh, bản đồ ảnh và widget để việc xem lại chi tiêu giống một nhật ký hơn là bảng tính.

### 2.2 Vấn đề giải quyết

Ứng dụng quản lý chi tiêu truyền thống thường yêu cầu nhập nhiều trường và lịch sử chỉ là danh sách chữ. Người dùng dễ bỏ cuộc vì thao tác lâu và dữ liệu không gợi lại được bối cảnh mua sắm.

BillLocket giảm ma sát bằng cách:

1. mở camera nhanh;
2. cho phép chụp hoặc chọn ảnh;
3. chỉ yêu cầu các trường tài chính thiết yếu;
4. tự gắn vị trí nếu người dùng đã đồng ý và hệ thống lấy được vị trí đủ mới;
5. hiển thị lịch sử dưới dạng lịch ảnh, bản đồ ảnh, widget và Monthly Recap.

### 2.3 Trả lời ngắn khi thầy hỏi “Ứng dụng của em là gì?”

> BillLocket là ứng dụng Android quản lý thu chi cá nhân theo hướng nhật ký hình ảnh. Người dùng chụp món đồ hoặc hóa đơn, nhập số tiền, danh mục và tài khoản; ứng dụng lưu cục bộ bằng Room, sau đó tổng hợp thành lịch, thống kê, bản đồ, widget và Monthly Recap. App ưu tiên riêng tư, hoạt động offline cho nghiệp vụ tài chính và không yêu cầu tài khoản.

### 2.4 Đối tượng sử dụng

- Sinh viên và người trẻ muốn ghi chi tiêu nhanh.
- Người không cần hệ thống kế toán phức tạp.
- Người muốn nhớ “đã mua gì, ở đâu” thay vì chỉ biết “đã tiêu bao nhiêu”.

### 2.5 Phạm vi chức năng hiện tại

- Thu/chi, ảnh, ghi chú, ngày giờ, tài khoản và danh mục.
- Tạo, xem, sửa, tìm kiếm và xóa giao dịch.
- Lịch tháng có thumbnail ảnh và tổng thu/chi.
- Thống kê tuần/tháng/năm, phân bố danh mục và xu hướng.
- Tài khoản, số dư, chuyển khoản giữa các tài khoản.
- Ngân sách và cảnh báo ngưỡng.
- Vị trí riêng cho từng ảnh và bản đồ marker ảnh.
- Ba widget chính: giao dịch gần nhất, tổng hợp hôm nay, tổng hợp tuần.
- Monthly Recap có tùy chọn ẩn số tiền, ghi chú và địa điểm trước khi chia sẻ.
- Backup/restore cục bộ.
- Tiếng Việt, tiếng Anh và ngôn ngữ hệ thống.
- Nhập ghi chú bằng giọng nói.
- AI on-device gợi ý một số danh mục phổ biến từ ảnh.

---

## 3. Công nghệ và cấu hình dự án

### 3.1 TECH STACK

| Thành phần | Công nghệ | Vai trò |
|---|---|---|
| Ngôn ngữ | Kotlin 2.0.21 | Toàn bộ logic Android |
| UI | Jetpack Compose + Material 3 | Xây dựng giao diện khai báo |
| Kiến trúc UI | MVVM + immutable state + action/effect | Tách UI và logic |
| Điều hướng | Navigation Compose 2.7.3 | Điều hướng type-safe ở mức mô hình route |
| Dependency Injection | Hilt 2.51.1 | Cấp repository, DAO và platform adapter |
| Database | Room 2.7.1 + KSP | Lưu dữ liệu tài chính cục bộ |
| Bất đồng bộ | Kotlin Coroutines + Flow | Công việc nền và dữ liệu reactive |
| Camera | CameraX 1.4.2 | Preview, chụp, đổi camera, flash, zoom |
| Ảnh UI | Coil 3.0.4 | Decode và hiển thị ảnh theo URI |
| EXIF | AndroidX ExifInterface 1.3.7 | Đọc orientation và metadata ảnh |
| Bản đồ | Google Maps Compose 6.1.2 | Bản đồ ảnh và chọn vị trí |
| Widget | Jetpack Glance 1.1.1 | Widget màn hình chính |
| Công việc bền vững | WorkManager 2.10.2 | Đối soát và dọn media |
| Giọng nói | Android `SpeechRecognizer` | Chuyển giọng nói thành ghi chú |
| AI ảnh | ML Kit Image Labeling 17.0.9 | Nhận diện nhãn ảnh trên thiết bị |
| Test | JUnit, coroutines-test, AndroidX Test, Espresso, Room testing | Unit và instrumented tests |

### 3.2 BUILD CONFIGURATION

- `applicationId`: `com.billlocket.app`
- `minSdk`: 26 — Android 8.0 trở lên.
- `compileSdk` và `targetSdk`: 35.
- Gradle wrapper: 8.11.1.
- Android Gradle Plugin: 8.9.0.
- Gradle chạy bằng JDK 17.
- Bytecode Java/Kotlin target: JVM 11.
- Phiên bản app hiện tại: `1.0`, version code `1`.

### 3.3 Vì sao Gradle chạy JDK 17 nhưng JVM target là 11?

**Trả lời ngắn:** JDK 17 là môi trường chạy Gradle/AGP được dự án hỗ trợ; target 11 là mức bytecode ứng dụng biên dịch ra. Hai khái niệm này không bắt buộc giống nhau.

### 3.4 OFFLINE-FIRST nghĩa là gì?

**Trả lời ngắn:** Room và file nội bộ là nguồn dữ liệu chính. Tạo giao dịch, xem lịch, thống kê, ngân sách, ảnh và các phép tính tài chính không phụ thuộc server hay tài khoản.

Không nên nói “100% chức năng luôn offline”, vì:

- Google Maps cần mạng để tải bản đồ nền và cần API key hợp lệ;
- SpeechRecognizer dự phòng có thể dùng dịch vụ mạng của thiết bị;
- vị trí cần GPS/network provider của Android, dù không gửi dữ liệu lên backend của BillLocket.

---

## 4. Kiến trúc tổng thể

### 4.1 MVVM

BillLocket áp dụng MVVM theo từng feature:

```text
Compose Screen
    ↓ Action của người dùng
ViewModel
    ↓ Use case / Repository contract
Repository implementation
    ↓
Room / SharedPreferences / Media store / Android platform API

Room Flow / kết quả xử lý
    ↑
ViewModel cập nhật immutable StateFlow
    ↑
Compose tự recomposition
```

### 4.2 Trả lời ngắn khi hỏi “App có theo MVVM không?”

> Có. Mỗi màn hình chính có ViewModel giữ `StateFlow` bất biến, nhận `Action`, gọi use case hoặc repository và phát `Effect` cho sự kiện dùng một lần như điều hướng hoặc xin quyền. Compose chỉ render state và gửi action; không truy cập DAO trực tiếp.

### 4.3 Ba khái niệm STATE – ACTION – EFFECT

- **State:** dữ liệu bền trong vòng đời màn hình, ví dụ `TransactionEditorState`, `CaptureState`, `MapState`.
- **Action:** ý định của người dùng, ví dụ `Save`, `TypeChanged`, `TakePhoto`, `DismissCategorySuggestion`.
- **Effect:** sự kiện dùng một lần, ví dụ mở photo picker, yêu cầu permission, điều hướng sau khi lưu.

Tách Effect khỏi State để tránh việc xoay màn hình hoặc recomposition làm lặp lại điều hướng/hộp thoại.

### 4.4 Các layer

#### Presentation

- Compose screen, component, navigation, ViewModel và UI state.
- Không gọi DAO hoặc thao tác file trực tiếp.
- File tiêu biểu: `Navigation.kt`, các package `feature/*` và `ui/*`.

#### Domain

- Model thuần Kotlin, repository interface, use case, validation và `Outcome`.
- Không phụ thuộc Compose, Room, CameraX hoặc Maps.
- File tiêu biểu: `domain/model`, `domain/repository`, `domain/usecase`.

#### Data

- Room entity, DAO, projection và repository implementation.
- Chuyển đổi Entity ↔ domain model.
- File tiêu biểu: `data/local`, `data/repository`.

#### Platform

- CameraX, hệ thống vị trí, file/EXIF, WorkManager, Glance, SpeechRecognizer, ML Kit và map renderer.
- Được che bởi interface hẹp khi logic cần test bằng fake.

#### Dependency Injection

- Hilt module liên kết interface và implementation.
- Ví dụ: `DatabaseModule`, `MediaModule`, `LocationModule`, `LocaleModule`, `AiModule`.

### 4.5 Vì sao có REPOSITORY?

**Trả lời ngắn:** Repository che chi tiết Room khỏi ViewModel, tạo API thống nhất cho đọc/ghi dữ liệu và giúp thay implementation thật bằng fake trong unit test.

### 4.6 Vì sao có USE CASE?

Use case giữ các quy trình nghiệp vụ gồm nhiều bước, ví dụ:

- promote ảnh rồi mới tạo giao dịch;
- nếu ghi database thất bại thì xóa media vừa promote;
- lưu thành công thì cập nhật widget;
- soft-delete rồi cho phép undo trong 5 giây;
- tính và phát cảnh báo ngân sách.

### 4.7 HILT hoạt động như thế nào?

Hilt tạo dependency graph từ `@Module`, `@Provides`, `@Binds` và inject vào class có `@HiltViewModel`/`@Inject`. Vì ViewModel chỉ biết interface nên code dễ test và không phải tự khởi tạo database hoặc platform service.

### 4.8 SINGLE-ACTIVITY, COMPOSE và FRAGMENT

BillLocket dùng một `MainActivity`, Jetpack Compose và Navigation Compose. Dự án **không dùng Fragment cho luồng chính**.

Nếu thầy hỏi vì sao không dùng Fragment:

> Fragment là kiến thức nền quan trọng với View system. Dự án chọn kiến trúc Compose hiện đại nên Composable đóng vai trò màn hình và Navigation Compose quản lý back stack. Vòng đời vẫn được quản lý qua Activity, Navigation back-stack entry, ViewModel và lifecycle-aware state collection.

---

## 5. Điều hướng

### 5.1 NAVIGATION GRAPH

Các top-level destination:

1. Home/Calendar.
2. Statistics.
3. Accounts.
4. Map.
5. More.

Các route chi tiết gồm capture, tạo/sửa/xem giao dịch, tìm kiếm, vị trí ảnh, tài khoản, chuyển tiền, danh mục, ngân sách, Monthly Recap và Settings.

### 5.2 An toàn route

`AppDestination` xây route từ stable ID. `parseRouteId` chỉ chấp nhận `[A-Za-z0-9._~-]` với độ dài tối đa 128 ký tự. Navigation truyền ID nhỏ, không truyền nguyên entity hay bitmap.

### 5.3 Vì sao không truyền object qua Navigation?

> Object có thể lớn, mutable và không bền khi process bị kill. App truyền ID, sau đó màn hình mới quan sát dữ liệu từ repository/Room — nguồn sự thật duy nhất.

---

## 6. Room database

### 6.1 ROOM là gì?

Room là abstraction chính thức của Android trên SQLite. Room kiểm tra SQL ở compile time, hỗ trợ DAO, Entity, `Flow` và transaction.

Database file: `billlocket.db`, schema version hiện tại: `1`.

### 6.2 Các bảng chính

| Bảng | Mục đích | Quan hệ quan trọng |
|---|---|---|
| `accounts` | Tài khoản tiền mặt/ngân hàng/ví | Tên chuẩn hóa unique |
| `categories` | Danh mục thu hoặc chi | Unique theo type + tên chuẩn hóa |
| `transactions` | Giao dịch thu/chi | FK tới account và category, `RESTRICT` |
| `transaction_images` | Ảnh theo thứ tự và vị trí riêng | FK transaction, `CASCADE` |
| `transfers` | Chuyển tiền giữa hai tài khoản | Hai FK account, `RESTRICT` |
| `budgets` | Ngân sách tháng tổng hoặc theo danh mục | FK category |
| `budget_threshold_events` | Chống gửi lặp cảnh báo 80%/100% | FK budget, `CASCADE` |
| `transaction_search` | FTS4 tìm ghi chú nhanh | Đồng bộ theo transaction |

Quan hệ rút gọn:

```text
accounts 1 ──────── n transactions n ──────── 1 categories
   │                       │
   │                       └────────── 1 ───── n transaction_images
   │
   ├──── 1 ───── n transfers (from_account_id)
   └──── 1 ───── n transfers (to_account_id)

categories 1 ───── 0..n budgets 1 ───── n budget_threshold_events
transactions 1 ─── 0..1 transaction_search (FTS content theo transaction)
```

### 6.3 ENTITY, DAO, DATABASE

- **Entity:** định nghĩa bảng và cột bằng data class.
- **DAO:** định nghĩa câu lệnh SQL bằng annotation như `@Query`, `@Insert`, `@Update`, `@Transaction`.
- **RoomDatabase:** tập hợp entity và cung cấp các DAO.

### 6.4 FOREIGN KEY

- Không cho xóa cứng account/category nếu còn giao dịch tham chiếu (`RESTRICT`).
- Khi xóa cứng transaction, các row ảnh thuộc transaction bị xóa theo (`CASCADE`).
- Archive được ưu tiên cho account/category để lịch sử cũ vẫn đọc được.

### 6.5 INDEX dùng để làm gì?

Index tăng tốc các truy vấn thường xuyên: lọc theo ngày, account, category, type, trạng thái deleted, sắp xếp ảnh và truy vấn vị trí. Đổi lại, mỗi lần ghi phải cập nhật index và database tốn thêm dung lượng.

### 6.6 FTS4

`transaction_search` là bảng Full-Text Search 4 cho ghi chú. Nó giúp tìm kiếm text hiệu quả hơn quét toàn bộ `transactions.note` bằng `%LIKE%` khi dữ liệu lớn.

### 6.7 Ràng buộc ở nhiều tầng

BillLocket không chỉ validate ở UI. Các quy tắc quan trọng được bảo vệ ở nhiều tầng:

1. validator/domain từ chối command sai trước khi ghi;
2. repository kiểm tra quan hệ và chuyển đổi model;
3. foreign key/index bảo vệ quan hệ trong SQLite;
4. trigger tạo bởi `DatabaseInitializer` kiểm tra amount, tọa độ, metadata location, position ảnh và threshold.

Nhờ vậy, kể cả một màn hình có bug, database vẫn khó nhận trạng thái bất hợp lệ.

### 6.8 Tại sao ảnh không lưu BLOB trong Room?

> Room chỉ lưu URI và metadata; bytes ảnh nằm trong app-managed storage. Cách này tránh database phình lớn, giảm RAM khi query, giúp stream/chia sẻ ảnh dễ hơn và tách rõ transaction dữ liệu với media file.

### 6.9 Tiền được lưu như thế nào? MONEY / MINOR UNIT

Tiền được lưu bằng `Long` theo đơn vị nhỏ nhất, không dùng `Double`.

Ví dụ với VND:

```text
125000 đồng → amount_minor = 125000
```

Lý do không dùng số thực: tránh sai số nhị phân kiểu `0.1 + 0.2 != 0.3`, bảo đảm tổng và so sánh tài chính chính xác.

### 6.10 Ngày giờ được lưu như thế nào?

Một giao dịch lưu đồng thời:

- `occurred_at`: epoch milliseconds của thời điểm tuyệt đối;
- `local_date_epoch_day`: ngày địa phương dùng để nhóm lịch;
- `zone_id`: múi giờ tại lúc ghi.

Nhờ đó, đổi múi giờ thiết bị không làm giao dịch nhảy sang ngày khác trên lịch.

---

## 7. Quy tắc tài chính

### 7.1 Giao dịch hợp lệ

- Số tiền phải lớn hơn 0.
- Category phải cùng `TransactionType` với giao dịch.
- Account và category phải tồn tại, đang hoạt động khi tạo mới.
- Currency phải hợp lệ và nhất quán theo phạm vi nghiệp vụ hiện tại.
- Ghi chú tối đa 500 ký tự ở editor.
- Một giao dịch có tối đa 10 ảnh ở capture flow.

### 7.2 Số dư tài khoản BALANCE

Khái niệm:

```text
số dư = số dư đầu kỳ
       + tổng thu
       - tổng chi
       + chuyển vào
       - chuyển ra
```

### 7.3 TRANSFER

Transfer không phải thu/chi và không làm thay đổi tổng tài sản ròng.

```text
Tài khoản A: -X
Tài khoản B: +X
Tổng net worth: không đổi
```

Không cho chuyển từ một tài khoản sang chính nó.

### 7.4 Ngân sách

- Theo tháng.
- Có ngân sách tổng và ngân sách theo category.
- Mặc định cảnh báo ở 80%, vượt ở 100%.
- `budget_threshold_events` ghi nhận ngưỡng đã phát để tránh notification lặp.
- Khi chi tiêu giảm xuống dưới ngưỡng, trạng thái có thể được reset để lần vượt sau phát cảnh báo hợp lệ.

### 7.5 SOFT DELETE và UNDO

Khi người dùng xóa transaction:

1. app gắn `deleted_at`, chưa xóa dữ liệu ngay;
2. tạo `DeleteUndoToken` với cửa sổ mặc định 5 giây;
3. nếu Undo đúng hạn, bỏ `deleted_at`;
4. sau khi chốt xóa mới purge row và dọn media.

Lợi ích: giảm nguy cơ mất dữ liệu vì chạm nhầm và tách thao tác database khỏi dọn file.

---

## 8. Luồng tạo giao dịch end-to-end

### 8.1 Luồng chính

```text
Home → Camera
→ chụp/chọn 0..10 ảnh
→ stage + inspect + normalize
→ lưu TransactionDraft
→ Transaction Editor
→ nhập tiền/type/category/account/note/time
→ tùy chọn voice và AI suggestion
→ validate
→ promote media
→ Room transaction lưu transaction + ordered images
→ đánh dấu media committed
→ refresh widget
→ mở Transaction Detail
```

### 8.2 TransactionDraft

Draft được lưu bằng SharedPreferences dưới dạng JSON để:

- không mất ảnh và dữ liệu đang nhập khi chuyển giữa camera/editor;
- sống qua configuration change hoặc process recreation;
- không đưa dữ liệu chưa xác nhận vào Room;
- lưu thêm metadata gợi ý AI mà không cần migration database.

SharedPreferences draft store dùng `Mutex` để tránh hai coroutine ghi đè nhau. Metadata AI có hàm cập nhật riêng để không ghi đè số tiền hoặc ghi chú người dùng vừa nhập.

### 8.3 Vì sao draft không lưu thẳng Room?

> Draft là dữ liệu tạm và có thể bị hủy. Room chỉ nhận giao dịch đã xác nhận, giúp các thống kê không vô tình tính dữ liệu chưa hoàn chỉnh và làm rollback media rõ ràng hơn.

---

## 9. Camera, ảnh và media ownership

### 9.1 CAMERAX

CameraX cung cấp:

- preview theo lifecycle;
- chụp ảnh;
- đổi camera trước/sau nếu có;
- flash nếu thiết bị hỗ trợ;
- zoom;
- callback thành công/thất bại.

Camera không phải thiết bị bắt buộc trong manifest (`required=false`) vì app vẫn hỗ trợ chọn ảnh hoặc giao dịch không ảnh.

### 9.2 Media pipeline

```text
Capture/Photo Picker
→ staging file thuộc app
→ inspect kích thước, MIME, EXIF
→ sửa orientation
→ normalize/compress nếu cần
→ tính SHA-256 và metadata
→ preview trong draft
→ promote thành canonical app-owned URI khi Save
```

### 9.3 CONTENT URI

Room lưu URI kiểu `content://com.billlocket.app.media/...`, không lưu đường dẫn tuyệt đối. `OwnedMediaContentProvider` chỉ phục vụ media app sở hữu và không export công khai.

### 9.4 EXIF

EXIF có thể chứa orientation, thời điểm chụp và GPS. App dùng EXIF để:

- xoay ảnh đúng hướng;
- lấy candidate location khi import nếu truy cập được;
- không tự coi metadata là xác nhận cuối cùng nếu chưa qua policy của ứng dụng.

Khi xử lý/chia sẻ, metadata nhạy cảm phải tuân theo media policy; vị trí được lưu thành field có kiểm soát trong database chứ không phụ thuộc EXIF gốc.

### 9.5 Giới hạn và chính sách nén ảnh

- File nguồn tối đa 32 MiB.
- Tổng số pixel nguồn tối đa 100 triệu để giảm nguy cơ decode gây hết RAM.
- Lần nén chính: cạnh dài 2560 px, JPEG quality 88.
- Nếu vẫn lớn: fallback cạnh dài 2048 px, quality 75.
- File normalized cuối tối đa 4 MiB.
- MIME đầu ra chuẩn hóa thành JPEG.

Các giới hạn này cân bằng giữa khả năng đọc hóa đơn và dung lượng lưu trữ.

### 9.6 Hai pha STAGE và PROMOTE

- **Stage:** file tạm thuộc draft; hủy draft thì có thể dọn an toàn.
- **Promote:** chuyển sang media chính thức trước khi transaction được ghi.
- Nếu promote một phần rồi bước sau thất bại, use case xóa các file vừa promote để tránh orphan.

### 9.7 MEDIA RECONCILIATION

`MediaReconciliationWorker` dùng WorkManager để đối soát file với reference trong database, dọn staging/orphan theo policy và phải idempotent — chạy lại không làm hỏng dữ liệu hợp lệ.

### 9.8 Chia sẻ ảnh an toàn

App dùng URI và permission tạm thời thay vì gửi đường dẫn file. Monthly Recap dùng `FileProvider` với authority `com.billlocket.app.fileprovider`.

---

## 10. Vị trí ảnh

### 10.1 Nguyên tắc LOCATION

- Vị trí là tùy chọn.
- Consent được lưu theo lựa chọn auto-location, nhưng quyền Android vẫn được xin theo ngữ cảnh.
- Mỗi ảnh có vị trí riêng, không gắn chung ở transaction.
- Không lấy được vị trí không được làm mất ảnh hoặc chặn lưu giao dịch.
- Người dùng có thể sửa, chọn thủ công hoặc xóa vị trí.

### 10.2 Logic lấy vị trí tự động hiện tại

Khi màn hình camera mở và auto-location đã bật, ViewModel gọi `WarmAutomaticLocation` để **prefetch** vị trí song song với lúc người dùng căn ảnh.

Fast path chỉ dùng last-known location khi:

- tuổi vị trí tối đa **30 giây**;
- accuracy tồn tại và tối đa **100 m**.

Nếu không có cache đủ tốt, hệ thống hỏi đồng thời các provider khả dụng:

1. fused trên API hỗ trợ;
2. GPS;
3. network.

Mỗi provider có acquisition window khoảng 4 giây. Kết quả fresh phải:

- không quá 30 giây;
- accuracy tối đa 500 m;
- tọa độ hợp lệ.

Các kết quả hợp lệ được xếp theo accuracy, provider priority và thời gian.

Ở capture flow, toàn bộ bước chờ location sau normalize còn được bọc bởi timeout tối đa **8,5 giây**. Vì các provider được hỏi đồng thời nên thời gian không bị cộng tuần tự thành 4 giây × số provider.

### 10.3 Vì sao không dùng vị trí cũ 10 phút?

Vị trí 10 phút trước có thể thuộc địa điểm khác. App hiện chủ động **không fallback** sang location quá cũ; thà không gắn vị trí còn hơn gắn sai và làm dữ liệu bản đồ mất tin cậy.

### 10.4 Tại sao chụp đôi khi phải chờ?

Prefetch thường giúp vị trí sẵn trước khi bấm chụp. Nếu chưa sẵn, ảnh được normalize rồi chờ một lần lấy vị trí có giới hạn. Trong thời gian đó UI báo processing để tránh người dùng lưu draft trước khi kết quả vị trí về.

### 10.5 Nếu vị trí thất bại

Ảnh vẫn được thêm bình thường và UI báo: không lấy được vị trí chính xác. Người dùng có thể mở màn hình Image Location để:

- dùng vị trí hiện tại;
- chọn điểm trên map;
- nhập latitude/longitude;
- nhập place name;
- xác nhận EXIF candidate;
- xóa vị trí.

### 10.6 Permission vị trí

App khai báo `ACCESS_FINE_LOCATION` và `ACCESS_COARSE_LOCATION`. Nếu không được cấp, nghiệp vụ tài chính, ảnh, lịch và thống kê vẫn hoạt động.

---

## 11. Google Maps và bản đồ ảnh

### 11.1 Công nghệ

Renderer hiện tại là Google Maps Compose. Domain và Room vẫn provider-neutral: chúng chỉ biết `GeoPoint` và `PhotoLocation`, không lưu kiểu dữ liệu Google Maps.

### 11.2 MAP MARKER ảnh

`MapPhoto` chứa:

- image ID và transaction ID;
- URI ảnh;
- tọa độ;
- số tiền;
- category;
- thời gian.

`PhotoMarkerBitmapFactory` tạo bitmap marker từ thumbnail để ảnh xuất hiện trực tiếp trên bản đồ. Khi marker được chọn, UI hiển thị card/preview và có thể mở transaction detail.

### 11.3 CLUSTER

Các ảnh gần nhau được gom theo cell địa lý (`clusterMapPhotos`) để hạn chế marker đè nhau và giảm tải hiển thị. Cluster có tâm và danh sách ảnh thành viên.

### 11.4 Filter bản đồ

Map hỗ trợ lọc theo:

- khoảng thời gian/tháng;
- thu hoặc chi;
- tài khoản;
- danh mục.

### 11.5 API KEY và bảo mật

- Key không commit vào Git.
- Build đọc `MAPS_API_KEY` từ `secrets.properties` qua Secrets Gradle Plugin.
- `local.defaults.properties` chứa giá trị trống để clean clone vẫn build.
- Key production phải giới hạn theo package `com.billlocket.app`, signing certificate và Maps SDK for Android.

Nếu key/mạng/billing không hợp lệ, basemap có thể không hiển thị; dữ liệu giao dịch và tọa độ cục bộ vẫn còn.

### 11.6 Vì sao có thể đổi MapLibre sau này?

Vì entity/domain chỉ lưu tọa độ và metadata độc lập provider. Google Maps type nằm ở presentation/platform. Thay renderer không bắt buộc migration Room.

---

## 12. Widget Android

### 12.1 GLANCE

BillLocket dùng Jetpack Glance — API viết widget theo phong cách Compose nhưng cuối cùng vẫn chạy trên cơ chế AppWidget/RemoteViews của Android.

### 12.2 Các widget

- `LatestTransactionWidget`: ảnh giao dịch gần nhất.
- `TodayRecapWidget`: tổng hợp ngày hiện tại.
- `WeeklyRecapWidget`: tổng hợp tuần.
- `BillLocketWidget`: widget tương thích/nền tảng ban đầu.

### 12.3 Thành phần widget theo kiến thức môn học

- XML `appwidget-provider` trong `res/xml`.
- Layout được dựng bằng Glance.
- Lớp `GlanceAppWidget` chứa nội dung.
- `GlanceAppWidgetReceiver` tương đương vai trò AppWidgetProvider receiver.
- Receiver được khai báo trong Manifest với action `APPWIDGET_UPDATE`.

### 12.4 Widget cập nhật khi nào?

Sau create, update hoặc delete thành công, use case gọi `WidgetUpdatePort.requestRefresh()`. Widget đọc snapshot nhỏ từ repository/database, không dùng chung ViewModel của màn hình.

### 12.5 Quyền riêng tư widget

Người dùng cấu hình hiển thị/ẩn số tiền, hiển thị category và hành động khi bấm. Widget có empty state nếu chưa có ảnh phù hợp.

### 12.6 Trả lời ngắn “Tại sao widget không phải Composable bình thường?”

> Widget sống ngoài process/UI Activity của app và bị giới hạn bởi AppWidget/RemoteViews. Glance cung cấp cú pháp gần Compose nhưng tập component và cơ chế cập nhật khác Compose screen.

---

## 13. Home, Calendar, Search và Statistics

### 13.1 Home/Calendar

- Quan sát dữ liệu theo khoảng tháng bằng Flow.
- Mỗi ngày có total và thumbnail ảnh.
- Chuyển tháng, về hôm nay, lọc thu/chi và account.
- Bấm ngày mở Day Gallery.
- Bấm ảnh/giao dịch mở detail.

### 13.2 Search

- Tìm ghi chú qua FTS4.
- Lọc theo ngày, type, category và account.
- Dùng paging/bounded query thay vì tải vô hạn.

### 13.3 Statistics

- Khoảng tuần, tháng, năm.
- Tổng thu, chi, net.
- Phân bố chi theo category.
- Trend theo ngày/tuần/tháng.
- So sánh với kỳ tương đương trước.
- Lọc theo account.

### 13.4 Derived data

Totals và statistics được tính từ facts trong Room thông qua query/repository, không lưu một biến tổng riêng dễ lệch dữ liệu.

---

## 14. Monthly Recap

### 14.1 Mục đích

Monthly Recap biến dữ liệu tháng thành câu chuyện trực quan gồm cover, tổng quan, category nổi bật, địa điểm và gallery ảnh.

### 14.2 Xây dữ liệu

`buildMonthlyRecap` nhận danh sách `TransactionDetails` của tháng và tạo model tổng hợp. `YearMonth.toRecapRange` tạo khoảng ngày chính xác cho tháng đang xem.

### 14.3 Quyền riêng tư khi chia sẻ

`RecapPrivacy` cho phép bật/tắt:

- số tiền;
- ghi chú;
- địa điểm.

Trước export, `MonthlyRecap.redacted()` tạo `ShareableMonthlyRecap`; vì vậy dữ liệu bị ẩn ngay ở model xuất, không chỉ che bằng UI.

### 14.4 Export

`AndroidMonthlyRecapExporter` render bitmap 1080 × 1350, nền đen và màu vàng thương hiệu, lưu file tạm rồi chia sẻ bằng FileProvider URI.

### 14.5 Demo seeder

Debug source set có `MonthlyRecapDemoSeeder` và Activity riêng để tạo dữ liệu demo. Vì nằm trong `src/debug`, seeder không đi vào release build.

---

## 15. Đa ngôn ngữ và locale

### 15.1 LOCALE hỗ trợ

- `SYSTEM`: theo thiết bị.
- `VIETNAMESE`: `vi-VN`.
- `ENGLISH`: `en-US`.

AndroidX per-app locale dùng `AppCompatDelegate.setApplicationLocales`. Lựa chọn được lưu và phục hồi từ `MainApplication` trước màn hình đầu tiên.

### 15.2 Resource

- `res/values/strings.xml`: chuỗi gốc tiếng Việt.
- `res/values-en/strings.xml`: tiếng Anh.
- `res/xml/locale_config.xml`: khai báo locale hỗ trợ.

### 15.3 Định dạng theo locale

`BillLocketFormatters` xử lý tập trung:

- tiền;
- số;
- ngày;
- giờ 12/24h;
- khoảng cách;
- nhãn thời gian tương đối.

Ví dụ:

- Việt Nam: `1.250.000 ₫`, `31/10/2026`, giờ 24h.
- English: `₫1,250,000`, `10/31/2026`, giờ 12h có AM/PM.

### 15.4 Locale có thay đổi dữ liệu không?

Không. Domain/database vẫn lưu `Long`, epoch time, currency code và text người dùng. Locale chỉ đổi cách trình bày. Tên category custom và note không bị tự dịch.

---

## 16. Ghi chú bằng giọng nói

### 16.1 SPEECHRECOGNIZER

App dùng API `android.speech.SpeechRecognizer`, không dùng OpenAI/Gemini/Google Cloud key.

- API 31+: ưu tiên recognizer on-device nếu có.
- Nếu không có: dùng recognizer hệ thống.
- Recognizer hệ thống có thể cần mạng tùy máy.

### 16.2 Phạm vi

Giọng nói chỉ hỗ trợ trường **ghi chú**. Không tự nhập số tiền, account, date hoặc category.

### 16.3 State machine

```text
Unavailable
Idle
RequestingPermission
Listening(partialText)
Processing
Completed(text)
Error(error)
```

`VoiceNoteCoordinator` giới hạn một session, chống bấm liên tục tạo nhiều recognizer và bỏ qua callback đến muộn.

### 16.4 Ghép text

- Note rỗng: chèn kết quả.
- Note đã có: append với đúng một khoảng trắng.
- Không âm thầm thay thế text cũ.
- Giới hạn 500 ký tự.
- Error/cancel giữ nguyên note trước phiên.

### 16.5 Lifecycle

Recognizer được tạo trong phạm vi composition của editor và `destroy()` trong `DisposableEffect` khi rời màn hình. Điều này tránh leak microphone/service.

### 16.6 Permission

`RECORD_AUDIO` được xin đúng lúc người dùng bấm microphone. Nếu từ chối, ô nhập bàn phím vẫn hoạt động; nếu từ chối vĩnh viễn, app hướng dẫn mở App Settings.

---

## 17. AI gợi ý danh mục từ ảnh

### 17.1 ML KIT IMAGE LABELING

App dùng model bundled `com.google.mlkit:image-labeling:17.0.9`:

- chạy trên thiết bị;
- không cần API key;
- không upload ảnh lên server của BillLocket;
- dùng được offline sau khi cài app;
- đổi lại APK universal tăng khoảng 42.99 MiB.

### 17.2 AI làm gì và không làm gì?

AI chỉ nhận diện nhãn hình ảnh phổ biến rồi **đề xuất category**. AI không:

- đọc tổng tiền trên hóa đơn;
- tự lưu transaction;
- tự thay category người dùng đã chọn;
- gửi ảnh lên Gemini/OpenAI;
- nhận diện chính xác mọi vật thể.

### 17.3 Pipeline AI

```text
Primary normalized image
→ decode bitmap có giới hạn cạnh tối đa 1024 px
→ sửa EXIF orientation
→ ML Kit trả nhãn + confidence
→ chuẩn hóa/deduplicate nhãn
→ loại nhãn generic/nguy hiểm
→ whitelist mapping sang semantic category
→ aggregate confidence
→ kiểm tra threshold và ambiguity
→ resolve semantic key sang stable built-in category ID
→ hiển thị card Apply/Dismiss
```

### 17.4 Nhóm hỗ trợ

- Food/drink.
- Clothing/shopping.
- Transport/fuel.
- Health.
- Study/work.
- Entertainment.
- Home.

### 17.5 Nhãn bị chặn

Person, face, selfie, landscape, paper, receipt, text, screenshot và các nhãn generic không được dùng để ép category. Mục tiêu là thà không gợi ý còn hơn gợi ý sai.

### 17.6 CONFIDENCE

- Aggregate threshold: tối thiểu `0.70`.
- Ít nhất một nhãn hỗ trợ có confidence `0.50`.
- Category đứng đầu phải hơn category thứ hai ít nhất `0.10`.
- Nhiều nhãn cùng nhóm được gộp bằng noisy-or:

```text
combined = 1 - product(1 - confidence_i)
```

Nhãn trùng sau normalize chỉ giữ confidence cao nhất để không khuếch đại giả tạo.

### 17.7 Vì sao mapping bằng ID, không bằng tên “Ăn uống”?

Tên hiển thị có thể đổi ngôn ngữ hoặc người dùng đổi tên. `CategoryResolver` ánh xạ semantic key sang `BuiltInCategoryIds` ổn định, chỉ nhận category EXPENSE đang active.

### 17.8 Bảo vệ lựa chọn người dùng

- Kết quả chỉ hiện dưới dạng suggestion.
- Category chỉ đổi khi bấm Apply.
- Chọn category thủ công sẽ cancel/khóa kết quả AI đến muộn.
- Dismiss không lặp lại cho cùng ảnh trong draft.
- Đổi transaction type làm reset quyết định phù hợp và đánh giá lại eligibility.
- `CancellationException` được truyền tiếp, không bị biến thành “No suggestion”.

### 17.9 Resource ownership

ML Kit task giữ bitmap và labeler đến khi task hoàn tất, kể cả coroutine đã cancel; cleanup có `AtomicBoolean` để không recycle/close hai lần.

### 17.10 Vì sao không dùng cloud AI?

> Với đồ án tài chính có ảnh riêng tư, on-device giảm rủi ro dữ liệu, không cần tài khoản/API key, dùng offline và chi phí vận hành bằng 0. Nhược điểm là model tổng quát, độ chính xác hạn chế và APK lớn hơn.

---

## 18. Backup và restore

### 18.1 Định dạng BACKUP

Backup là ZIP có cấu trúc:

```text
manifest.json
data.json
checksums.sha256
media/<image-id>.jpg   (nếu chọn bao gồm ảnh)
```

Người dùng có thể chọn có/không gồm ảnh và location.

### 18.2 Tính toàn vẹn

- SHA-256 cho `data.json` và media.
- Xác minh format/version.
- Kiểm tra ID, foreign reference, amount và currency.
- Kiểm tra tọa độ hợp lệ.
- Kiểm tra media khai báo khớp file thực tế.

### 18.3 Chống ZIP SLIP và ZIP BOMB

- Từ chối path tuyệt đối, `..`, backslash và entry lạ.
- Canonical path phải nằm trong staging root.
- Tối đa 20.100 entry.
- Tối đa 64 MiB mỗi entry.
- Tối đa 1 GiB toàn archive.

### 18.4 Import an toàn

Import replace dùng staging + recovery journal:

1. extract và validate hoàn toàn;
2. lưu trạng thái `PREPARED`;
3. di chuyển media cũ sang recovery;
4. thay database trong transaction;
5. ghi `DATABASE_READY`/`COMMITTED`;
6. áp dụng settings;
7. refresh widget;
8. nếu lỗi thì rollback media cũ.

Khi app khởi động, `recoverInterruptedImports()` xử lý trường hợp process chết giữa import.

---

## 19. Quyền Android và lý do sử dụng

| Permission | Mục đích | Nếu từ chối |
|---|---|---|
| `CAMERA` | Chụp ảnh CameraX | Vẫn chọn ảnh hoặc tạo giao dịch không ảnh |
| `ACCESS_FINE_LOCATION` | Vị trí chính xác | Có thể dùng coarse/manual hoặc không location |
| `ACCESS_COARSE_LOCATION` | Vị trí gần đúng | Có thể chọn thủ công |
| `POST_NOTIFICATIONS` | Cảnh báo ngân sách trên Android mới | Budget vẫn tính, chỉ không hiện notification |
| `RECORD_AUDIO` | Dictation ghi chú | Gõ bàn phím bình thường |
| `INTERNET` | Map tiles/dịch vụ phụ thuộc mạng | Core tài chính vẫn dùng offline |
| `ACCESS_NETWORK_STATE` | Nhận biết trạng thái kết nối | Không ảnh hưởng dữ liệu cục bộ |
| `VIBRATE` | Phản hồi/hành vi notification | Không ảnh hưởng nghiệp vụ |

Camera và microphone được khai báo `required=false` để thiết bị không có phần cứng vẫn có thể cài nếu các luồng dự phòng còn dùng được.

---

## 20. Bảo mật và riêng tư

### 20.1 PRIVACY BY DEFAULT

- Không có đăng nhập hoặc backend cloud.
- Room/file nội bộ là nguồn dữ liệu.
- Location tùy chọn và xóa được.
- AI ảnh xử lý on-device.
- BillLocket không lưu audio microphone.
- Không log ảnh, full note, raw label list hoặc tọa độ chính xác trong production.

### 20.2 Secrets

Không commit Maps API key. Secret chỉ nằm trong file ignored và key phải bị restriction ở Google Cloud.

### 20.3 Provider

- `OwnedMediaContentProvider`: không exported, phục vụ media app sở hữu.
- `FileProvider`: không exported, cấp quyền URI tạm thời cho thao tác chia sẻ.

### 20.4 Android automatic backup

Manifest đặt `allowBackup=false`; app dùng định dạng portable backup chủ động để kiểm soát dữ liệu, checksum và location.

---

## 21. Concurrency, Flow và xử lý lỗi

### 21.1 COROUTINES

- ViewModel dùng `viewModelScope`.
- File/EXIF/backup/AI chuyển sang dispatcher phù hợp.
- Không dùng `GlobalScope`.
- Job cũ bị cancel khi ảnh primary thay đổi hoặc người dùng chọn category thủ công.

### 21.2 FLOW

Room/repository trả `Flow`; ViewModel combine/map thành UI state. Khi database đổi, UI tự cập nhật mà không phải reload thủ công toàn màn hình.

### 21.3 OUTCOME

`Outcome.Success<T>` và `Outcome.Failure` giúp phân biệt thành công/thất bại mà không dùng exception làm control flow ở presentation.

### 21.4 Nguyên tắc graceful degradation

- Location lỗi → ảnh vẫn lưu.
- AI lỗi → chọn category thủ công.
- Voice lỗi → nhập bàn phím.
- Map key lỗi → dữ liệu tài chính vẫn hoạt động.
- Widget thiếu ảnh → empty state.
- Media normalize lỗi → không đưa file lỗi vào transaction.

---

## 22. Testing và validation

### 22.1 UNIT TEST

Chạy trên JVM, nhanh, tập trung vào:

- value object và validator;
- calendar/date range;
- budget threshold;
- statistics period;
- map clustering/location correctness;
- recap model/privacy;
- locale formatter/resource parity;
- voice state machine, text merger và locale mapping;
- AI classifier, resolver, threshold, race và draft serialization.

### 22.2 INSTRUMENTED TEST

Chạy trong Android runtime/device hoặc emulator:

- Room database và repository contract;
- backup round trip;
- media storage/provider;
- Navigation shell;
- SharedPreferences draft store;
- ML Kit engine smoke test.

### 22.3 Vì sao cần cả hai loại test?

> Unit test kiểm tra logic thuần nhanh và ổn định; instrumented test kiểm tra thành phần phụ thuộc Android thật như Context, Room, ContentProvider và ML Kit runtime.

### 22.4 Automated validation gate

Chạy bằng JDK 17:

```powershell
.\gradlew.bat test
.\gradlew.bat assembleDebug
.\gradlew.bat assembleDebugAndroidTest
.\gradlew.bat assembleRelease
.\gradlew.bat lint
git diff --check
```

Checkpoint BL-047 đã PASS toàn bộ các lệnh trên.

### 22.5 BL-048 còn phải test gì?

- Việt/Anh sau cold start và restart.
- Dictation Việt/Anh, cancel, denied permission.
- Ảnh food/clothing/unknown cho AI; accept/dismiss/manual override.
- AI offline, latency, memory và installed size thực tế.
- Camera, vị trí, map marker ảnh.
- CRUD/search/statistics/accounts/budgets/recap/backup.
- Ba widget sau khi đổi dữ liệu/ngôn ngữ.
- Keyboard/back, font lớn, TalkBack, theme.

---

## 23. Đối chiếu với nội dung môn Lập trình Android nâng cao

### 23.1 Ôn tập Android cơ bản

Áp dụng Activity lifecycle, Manifest, permission, resources, intent, file/provider và build Gradle.

### 23.2 Fragment

Dự án không dùng Fragment vì dùng Compose. Kiến thức tương đương được áp dụng qua:

- composable destination;
- Navigation Compose back stack;
- ViewModel scope;
- lifecycle-aware state collection;
- effect cleanup với `DisposableEffect`.

Nên trả lời trung thực: “Em hiểu Fragment nhưng chọn Compose hiện đại cho project.”

### 23.3 Widget

Áp dụng đầy đủ provider-info XML, receiver, update action, widget UI và cấu hình privacy bằng Jetpack Glance.

### 23.4 Cảm biến

App không khai thác accelerometer/gyroscope. App áp dụng nhóm location service/sensor thông qua GPS, fused/network provider, permission và cập nhật vị trí.

### 23.5 Đa ngôn ngữ

Áp dụng resource Việt/Anh, locale config, per-app language, định dạng tiền/ngày/giờ và persistence.

### 23.6 Vị trí Android

Áp dụng permission, LocationManager, nhiều provider, timeout, cache freshness, accuracy validation, manual correction và reverse geocoding/display address.

### 23.7 Google Maps

Áp dụng API key, Map Compose, style, camera, marker ảnh, cluster, current location, point picker và filter.

### 23.8 Kiến trúc Android

Áp dụng Room Entity/DAO/Database, Repository, ViewModel, Flow thay LiveData, Hilt DI và use case.

### 23.9 Nếu thầy hỏi “Tại sao dùng StateFlow thay LiveData?”

> StateFlow tích hợp tốt với coroutine, luôn có state hiện tại, dễ combine nhiều nguồn và Compose có API collect theo lifecycle. Vai trò kiến trúc tương đương observable state mà môn học trình bày qua LiveData.

---

## 24. Những quyết định và trade-off quan trọng

### 24.1 Compose thay Fragment/View XML

- Ưu: state-driven, ít boilerplate, UI component tái sử dụng tốt.
- Nhược: khác ví dụ truyền thống của môn học và cần quản lý recomposition/lifecycle đúng.

### 24.2 Local-first thay backend

- Ưu: riêng tư, offline, không phí server, demo ổn định.
- Nhược: chưa sync nhiều thiết bị, mất máy cần backup thủ công.

### 24.3 App-owned media thay MediaStore công khai

- Ưu: ownership và cleanup rõ, ảnh không lẫn gallery cá nhân.
- Nhược: gỡ app sẽ mất media nếu chưa backup.

### 24.4 Google Maps thay MapLibre ở renderer hiện tại

- Ưu: SDK Android ổn định, marker/camera Compose thuận tiện.
- Nhược: cần billing-enabled project, API key và mạng; vì vậy core app được tách khỏi map.

### 24.5 ML Kit bundled thay cloud AI

- Ưu: private, offline, không API key/phí request.
- Nhược: tăng APK và model chỉ nhận diện nhãn tổng quát.

### 24.6 Soft-delete thay delete ngay

- Ưu: undo và cleanup an toàn.
- Nhược: cần bước purge/reconciliation bổ sung.

---

## 25. GIỚI HẠN và tính năng hoãn lại

Các giới hạn phải nói rõ nếu được hỏi:

- Chưa có authentication/cloud sync/multi-device.
- Không kết nối ngân hàng hoặc thanh toán thật.
- AI không phải OCR; không đọc tổng tiền, ngày, merchant hoặc line item từ hóa đơn.
- SpeechRecognizer phụ thuộc khả năng của thiết bị; fallback có thể cần mạng.
- Google Maps cần key/billing/mạng để có basemap.
- ML Kit general-purpose có thể không gợi ý hoặc gợi ý chưa chính xác.
- V1 là single-user trên một thiết bị.
- BL-048 vẫn cần device acceptance tổng hợp trước khi merge nhánh feature vào `main`.

### 25.1 Tính năng có thể phát triển sau

- OCR hóa đơn với confidence và conflict resolution.
- Cloud sync mã hóa.
- Nhập số tiền bằng giọng nói.
- Merchant database.
- Custom vision model.
- Báo cáo CSV/PDF nâng cao.
- Shared/group budget.

---

## 26. Bộ câu hỏi vấn đáp nhanh

### Q1. Vì sao tên là BillLocket?

**Trả lời:** “Bill” thể hiện hóa đơn/chi tiêu; “Locket” thể hiện lưu giữ khoảnh khắc bằng ảnh. App kết hợp quản lý tài chính và nhật ký hình ảnh.

### Q2. Điểm khác ứng dụng quản lý chi tiêu bình thường?

**Trả lời:** Ảnh là trung tâm; lịch, map, widget và recap đều dùng ảnh để gợi nhớ bối cảnh giao dịch.

### Q3. Dữ liệu nằm ở đâu?

**Trả lời:** Dữ liệu có cấu trúc nằm trong Room/SQLite; bytes ảnh nằm trong app-managed storage; preference nhỏ nằm trong SharedPreferences.

### Q4. Tại sao không Firebase/Appwrite?

**Trả lời:** Phạm vi V1 là cá nhân, offline-first và private-by-default. Backend làm tăng độ phức tạp auth/sync/conflict mà không cần cho mục tiêu môn học.

### Q5. App có MVVM thật không?

**Trả lời:** Có; screen render StateFlow, gửi Action cho ViewModel, ViewModel gọi use case/repository và phát Effect một lần. DAO không được gọi từ UI.

### Q6. Repository khác DAO thế nào?

**Trả lời:** DAO là API SQL/Room; repository trả domain model, phối hợp DAO và che chi tiết persistence khỏi tầng trên.

### Q7. Vì sao dùng Flow?

**Trả lời:** Flow phát dữ liệu mới khi Room thay đổi; ViewModel biến đổi thành state và Compose tự cập nhật.

### Q8. Vì sao tiền không dùng Double?

**Trả lời:** Tránh sai số floating point. App dùng `Long` minor units và currency code.

### Q9. Vì sao cần `local_date_epoch_day`?

**Trả lời:** Để nhóm lịch ổn định theo ngày người dùng nhập, không bị đổi khi timezone thiết bị thay đổi.

### Q10. Transaction database có atomic không?

**Trả lời:** Các mutation nhiều row dùng Room transaction. Use case còn xử lý cleanup media nếu database write thất bại.

### Q11. Vì sao không lưu bitmap trong Room?

**Trả lời:** Bitmap/BLOB làm database nặng và tốn RAM; app lưu file riêng và URI + metadata trong Room.

### Q12. Nếu chụp ảnh xong app chết thì sao?

**Trả lời:** Ảnh đang ở staging và draft JSON được lưu; reconciliation/recovery có thể xử lý file tạm, còn transaction chỉ vào Room sau Save.

### Q13. Vì sao location đôi khi không có?

**Trả lời:** App chỉ nhận fix đủ mới và hợp lệ; nó ưu tiên không gắn vị trí hơn gắn nhầm vị trí cũ. Ảnh vẫn lưu và người dùng có thể sửa thủ công.

### Q14. Tại sao không luôn dùng lastKnownLocation?

**Trả lời:** lastKnown có thể rất cũ. Fast cache chỉ hợp lệ trong 30 giây và accuracy ≤100 m.

### Q15. Map có làm core app phụ thuộc Google không?

**Trả lời:** Không. Room/domain chỉ lưu tọa độ trung lập; Google Maps chỉ là renderer. Core tài chính vẫn dùng được khi map unavailable.

### Q16. API key có trong APK/repo không?

**Trả lời:** Repo không chứa key thật. Build lấy từ file ignored; key phải restriction theo app và certificate. Client key vẫn có thể được trích nên restriction là bắt buộc.

### Q17. Widget cập nhật ra sao?

**Trả lời:** Sau mutation thành công, use case gọi `WidgetUpdatePort`; Glance widget query snapshot nhỏ và render lại.

### Q18. App có dùng WorkManager không?

**Trả lời:** Có cho media reconciliation/dọn việc bền vững, không dùng cho CRUD tức thời.

### Q19. Đổi ngôn ngữ có đổi dữ liệu không?

**Trả lời:** Không; chỉ resource và formatter đổi. Money/time trong database giữ nguyên.

### Q20. Voice có gửi âm thanh lên server không?

**Trả lời:** BillLocket không lưu hoặc tự upload audio. App gọi recognizer Android; tùy dịch vụ hệ thống, fallback có thể dùng mạng.

### Q21. AI có tự chọn category không?

**Trả lời:** Không. AI chỉ hiển thị suggestion; người dùng phải Apply và lựa chọn thủ công luôn thắng.

### Q22. AI dùng model nào?

**Trả lời:** ML Kit bundled default Image Labeling 17.0.9 chạy on-device.

### Q23. Vì sao AI không nhận diện người?

**Trả lời:** Person/face/selfie là nhãn không có ý nghĩa tài chính và dễ xâm phạm kỳ vọng riêng tư, nên bị block.

### Q24. Confidence được xử lý thế nào?

**Trả lời:** Nhãn cùng semantic group được gộp, cần aggregate ≥0.70, một nhãn ≥0.50 và hơn nhóm thứ hai ≥0.10.

### Q25. Nếu AI lỗi thì sao?

**Trả lời:** Lỗi được xử lý recoverable; editor và Save không bị chặn, người dùng chọn category thủ công.

### Q26. Backup có an toàn không?

**Trả lời:** Có checksum SHA-256, validation cấu trúc/reference, giới hạn size/count và chống path traversal trước khi replace database.

### Q27. Tại sao có soft delete?

**Trả lời:** Để hỗ trợ undo và tránh xóa media không thể phục hồi ngay lập tức.

### Q28. App test những gì?

**Trả lời:** Unit test business logic/ViewModel và instrumented test Room, provider, backup, navigation, draft, ML Kit. Build gate còn gồm debug/release/androidTest APK, lint và diff check.

### Q29. Hiện tại hoàn thiện chưa?

**Trả lời:** Feature implementation và automated validation đến BL-047 đã hoàn tất. BL-048 còn device regression/acceptance trước merge release.

### Q30. Nếu có thêm thời gian sẽ làm gì?

**Trả lời:** Ưu tiên OCR hóa đơn có confidence và xác nhận người dùng, sau đó cloud backup mã hóa; không tự động ghi tài chính khi chưa có review.

---

## 27. Kịch bản demo đề xuất

1. Mở Home, giới thiệu tổng tháng và lịch ảnh.
2. Nhấn camera, chụp một món đồ/đồ ăn.
3. Cho thấy auto-location không chặn ảnh nếu thất bại.
4. Vào editor, nhập số tiền.
5. Demo microphone ở note.
6. Nếu AI gợi ý hợp lý, nhấn Apply; nếu không có, giải thích threshold bảo thủ.
7. Save và mở Transaction Detail.
8. Quay Home để thấy ngày/thumbnail/tổng cập nhật.
9. Mở Statistics.
10. Mở Map để thấy marker ảnh.
11. Mở Monthly Recap và privacy toggle.
12. Cho xem widget đã thêm trên màn hình chính.
13. Đổi Việt/Anh trong Settings.
14. Nếu còn thời gian, mở Backup/Storage.

### 27.1 Chuẩn bị trước demo

- Có ít nhất một giao dịch ảnh + location hợp lệ.
- Có dữ liệu một tháng đủ đa dạng cho recap/statistics.
- Thêm sẵn widget lên launcher.
- Kiểm tra Maps key và mạng.
- Kiểm tra gói speech tiếng Việt/Anh trên máy.
- Có sẵn ảnh đồ ăn, quần áo và ảnh người/unknown để demo AI.
- Tắt thông báo cá nhân và đặt pin/sạc ổn định.
- Giữ APK baseline/tag rollback nếu feature mới gặp sự cố.

---

## 28. Bản đồ file để tra source khi bị hỏi

| Chủ đề | File/package chính |
|---|---|
| Entry point | `MainApplication.kt`, `MainActivity.kt` |
| Navigation | `Navigation.kt`, `navigation/AppDestination.kt` |
| Theme | `ui/theme/*`, `ui/common/BillLocketVisuals.kt` |
| Room | `data/local/BillLocketDatabase.kt`, `entity/Entities.kt`, `dao/Daos.kt` |
| Repository | `domain/repository/RepositoryContracts.kt`, `data/repository/*` |
| Use case | `domain/usecase/FinancialUseCases.kt` |
| Transaction editor | `feature/transaction/editor/*` |
| Capture | `feature/capture/*` |
| Media | `media/AndroidMediaStore.kt`, `MediaReconciliationWorker.kt` |
| Location | `feature/location/ImageLocationFeature.kt` |
| Map | `feature/map/MapFeature.kt`, `PhotoMarkerBitmapFactory.kt` |
| Home/calendar | `feature/home/*` |
| Statistics/budget | `feature/insights/*` |
| Account/category/transfer | `feature/finance/*` |
| Widget | `widget/*`, `feature/widget/*`, `res/xml/*widget*` |
| Monthly Recap | `feature/recap/*` |
| Backup | `backup/AndroidPortableBackup.kt` |
| Locale | `locale/*`, `values/strings.xml`, `values-en/strings.xml` |
| Voice | `feature/transaction/voice/*` |
| AI ảnh | `feature/transaction/ai/*`, `di/AiModule.kt` |
| Manifest/permission | `app/src/main/AndroidManifest.xml` |
| Dependencies | `app/build.gradle.kts`, `gradle/libs.versions.toml` |
| Test | `app/src/test`, `app/src/androidTest` |

---

## 29. Trạng thái Git và rollback phục vụ báo cáo

- Stable report-ready baseline: `771324b`.
- Tag rollback: `baseline/report-ready-2026-10-09`.
- Nhánh tính năng: `codex/voice-localization-ai`.
- Localization commit: `ad41f1e`.
- Voice commit: `1d04311`.
- AI commit: `95c7f2c`.
- Chỉ merge vào `main` sau BL-048.

Ý nghĩa: mỗi nhóm tính năng có checkpoint riêng, có thể review, cherry-pick hoặc rollback độc lập. Không force-push/rewrite stable tag.

---

## 30. Kết luận nói trong 30 giây

> BillLocket là đồ án Android offline-first áp dụng khá đầy đủ kiến thức môn học: Compose/Navigation, MVVM, ViewModel và Flow, Room Entity–DAO–Database, Hilt, CameraX, vị trí, Google Maps, widget, WorkManager, đa ngôn ngữ và permission. Điểm riêng của sản phẩm là biến giao dịch thành ký ức bằng ảnh, bản đồ và recap. Các tiện ích mới như giọng nói và ML Kit đều được tách khỏi core tài chính; nếu chúng lỗi, người dùng vẫn nhập và lưu giao dịch thủ công bình thường. Dữ liệu tài chính dùng số nguyên, media do app sở hữu, location tùy chọn và backup có kiểm tra toàn vẹn.
