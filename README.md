# My Expense Tracker - Android Application

A complete, modern, offline-first personal income and expense tracker built with **Kotlin**, **Jetpack Compose (Material 3)**, and **Room Database**. Tailored for Indian personal finance tracking with full support for Indian Rupee (₹), DD-MM-YYYY date formats, and zero cloud dependency.

---

## 🌟 Key Features

1. **Dashboard**
   - Hero balance card displaying total net worth (Opening Balance + Total Income − Total Expenses).
   - Monthly income and expenses for current month.
   - Remaining monthly budget and budget utilization progress bar with 80% & 100% threshold alert banners.
   - Today's and this week's expense metrics.
   - 1-Tap Quick-Add chips for frequent daily expenses (Chai, Auto, Lunch, Groceries, Petrol).
   - Interactive Donut chart displaying category spending breakdown.
   - Recent transactions list with direct click-to-edit.

2. **Add, Edit & Delete Transactions**
   - Types: Expense, Income, Transfer (between accounts), and Refund.
   - Fast amount input in ₹ with decimal validation.
   - Category picker with curated icons and colors.
   - Date & Time pickers formatted as `DD-MM-YYYY hh:mm a`.
   - Payment Methods: Cash, UPI, Debit Card, Credit Card, Bank Transfer, and Other.
   - Notes, description, and comma-separated tags.
   - **Credit Card Bill Settlement rule**: Toggle to exclude CC bill payment from expenses to prevent double-counting of CC purchases.
   - Duplicate entry warning (detects matching transaction entered within 60 seconds).
   - Deletion with confirmation dialog.

3. **Monthly Budget Management**
   - Overall monthly budget with utilization status (<80% green, 80-99% amber, ≥100% red).
   - Individual category-level budgets with progress bars.
   - Clear visual distinction between **Budget Remaining** and **Actual Bank Balance**.
   - Month-by-month historical budget selector.

4. **Transaction History & Search**
   - Dedicated transactions screen with search across description, tags, and category name.
   - Time filters: All Time, Today, This Week, This Month.
   - Secondary filters by Type and Payment Method.
   - Sorting: Date (newest/oldest) and Amount (high/low).
   - Day-grouped list with per-day total expense header badges.
   - One-tap CSV Export.

5. **Reports & Analytics**
   - Daily expenses for current week (Mon–Sun bar chart).
   - Annual monthly expenses (Jan–Dec bar chart).
   - Category-wise spending donut chart with legend and percentages.
   - Income vs. Expense comparison visual bars.
   - Spending by Payment Method chart.
   - Top 5 Highest Spending categories ranking.
   - Comparison with previous month (% change badge).

6. **Savings Goals**
   - Track targets with Goal Name, Target Amount, Saved Amount, and Target Date.
   - Automated calculation of **required monthly savings** needed to hit each goal.
   - Deposit / Add funds dialog to record incremental savings.

7. **Recurring Transactions**
   - Automate monthly salary, apartment rent, loan EMIs, subscriptions, and utility bills.
   - Schedules: Daily, Weekly, Monthly.
   - Duplicate-safe background processing on app startup.

8. **Category Customization**
   - 16 Default Expense categories and 6 Income categories.
   - Add custom categories with custom icons from Material Design and color picker.
   - Edit and delete existing categories.

9. **Security, Privacy & Backup**
   - 100% offline-first architecture — zero analytics or network transmission.
   - 4-Digit Security PIN lock with numpad verification.
   - Biometric authentication toggle.
   - Export all transactions to CSV with Android Share Sheet integration.
   - Generate and share formatted monthly expense report.
   - Full JSON database backup and restore.
   - Clear all app data option protected by double confirmation.

---

## 🏗️ Architecture & Technology Stack

- **Platform:** Android (Min SDK 26, Target SDK 34)
- **Language:** Kotlin 2.0.0
- **UI Toolkit:** Jetpack Compose with Material 3 Design
- **Local Persistence:** Room Database 2.6.1 with KSP
- **Architecture Pattern:** MVVM (Model-View-ViewModel) + Repository Pattern + Clean Domain Calculations
- **Reactive Streams:** Kotlin Coroutines & StateFlow
- **Serialization:** Gson for JSON backup/restore
- **Testing:** JUnit 4 & Kotlinx Coroutines Test

---

## 📂 Project Structure

```
MyExpenseTracker/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/expensetracker/app/
│       │   │   ├── MyExpenseTrackerApp.kt
│       │   │   ├── MainActivity.kt
│       │   │   ├── data/
│       │   │   │   ├── model/         (Enums: TransactionType, PaymentMethod, RecurrenceFrequency)
│       │   │   │   ├── local/
│       │   │   │   │   ├── entity/    (Transaction, Category, Budget, Account, Recurring, Goal)
│       │   │   │   │   ├── dao/       (Room DAOs with reactive Flow queries)
│       │   │   │   │   ├── converter/ (Room TypeConverters)
│       │   │   │   │   └── ExpenseDatabase.kt (Prepopulated Room database)
│       │   │   │   └── repository/    (ExpenseRepository)
│       │   │   ├── domain/
│       │   │   │   └── FinancialCalculator.kt (Pure business calculations)
│       │   │   ├── ui/
│       │   │   │   ├── theme/         (Color, Type, Theme)
│       │   │   │   ├── navigation/    (Screen routes, BottomNavBar)
│       │   │   │   ├── components/    (Custom Canvas Charts, BalanceCard, TransactionItem, etc.)
│       │   │   │   └── screens/
│       │   │   │       ├── dashboard/ (DashboardScreen & ViewModel)
│       │   │   │       ├── transaction/ (List & AddEdit Transaction)
│       │   │   │       ├── budget/    (BudgetScreen & ViewModel)
│       │   │   │       ├── reports/   (ReportsScreen & ViewModel)
│       │   │   │       ├── goals/     (SavingsGoalsScreen & ViewModel)
│       │   │   │       ├── recurring/ (RecurringTransactionsScreen & ViewModel)
│       │   │   │       ├── categories/(CategoryManagementScreen & ViewModel)
│       │   │   │       └── settings/  (SettingsScreen, PinLockScreen & ViewModel)
│       │   │   └── util/              (CurrencyFormatter, DateUtils, CsvExporter, BackupHelper)
│       │   └── res/                   (values, drawables, adaptive launcher icons)
│       └── test/java/com/expensetracker/app/
│           ├── FinancialCalculatorTest.kt
│           └── DateUtilsTest.kt
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradlew & gradlew.bat
└── gradle/wrapper/gradle-wrapper.properties
```

---

## 🚀 How to Build and Run

### Option 1: Using Android Studio (Recommended)
1. Open **Android Studio** (Hedgehog, Iguana, Koala, or newer).
2. Select **File > Open...** and navigate to:
   `C:\Users\VigneshThangamuthu\.gemini\antigravity-ide\scratch\MyExpenseTracker`
3. Wait for Android Studio to sync Gradle and download dependencies.
4. Connect an Android mobile device (with USB Debugging enabled) or start an Android Virtual Device (AVD).
5. Click **Run > Run 'app'** (or press Shift + F10).

### Option 2: Using the Command Line
Ensure `JAVA_HOME` is set to JDK 17 or higher:

```powershell
cd C:\Users\VigneshThangamuthu\.gemini\antigravity-ide\scratch\MyExpenseTracker

# Run Unit Tests
.\gradlew test

# Build Debug APK
.\gradlew assembleDebug
```
The output APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 📱 Generating a Signed Release APK for Your Phone

To install a permanent, standalone release APK on your Android device:

### Step 1: Generate a Keystore (Run once in PowerShell or Terminal)
```powershell
keytool -genkey -v -keystore my-release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias my-alias
```
Enter a secure password when prompted.

### Step 2: Build the Release APK in Android Studio
1. In Android Studio, go to the top menu: **Build > Generate Signed Bundle / APK...**
2. Choose **APK** and click **Next**.
3. Point **Key store path** to your `my-release-key.jks` file, enter your password and alias.
4. Select destination folder and choose **release** build variant.
5. Check **V1 (Jar Signature)** and **V2 (Full APK Signature)** and click **Finish**.

### Step 3: Install on Your Phone
1. Transfer the generated `app-release.apk` to your phone via USB cable, Google Drive, or WhatsApp.
2. On your phone, tap the `.apk` file and tap **Install**.
3. Open **My Expense Tracker** and start tracking!
