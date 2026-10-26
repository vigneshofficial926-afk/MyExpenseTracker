package com.expensetracker.app

import android.app.Application
import com.expensetracker.app.data.local.ExpenseDatabase
import com.expensetracker.app.data.repository.ExpenseRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MyExpenseTrackerApp : Application() {

    val database by lazy { ExpenseDatabase.getDatabase(this) }
    val repository by lazy { ExpenseRepository(database, this) }

    override fun onCreate() {
        super.onCreate()
        CoroutineScope(Dispatchers.IO).launch {
            if (database.categoryDao().getCount() == 0 && database.accountDao().getCount() == 0)
                ExpenseDatabase.populateCleanDefaultData(database)
        }
    }
}
