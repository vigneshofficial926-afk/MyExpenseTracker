package com.expensetracker.app

import android.app.Application
import com.expensetracker.app.data.local.ExpenseDatabase
import com.expensetracker.app.data.repository.ExpenseRepository

class MyExpenseTrackerApp : Application() {

    val database by lazy { ExpenseDatabase.getDatabase(this) }
    val repository by lazy { ExpenseRepository(database, this) }

    override fun onCreate() {
        super.onCreate()
    }
}
