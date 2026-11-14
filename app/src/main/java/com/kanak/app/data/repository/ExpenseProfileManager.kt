package com.kanak.app.data.repository

import com.kanak.app.model.ExpenseProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Kept in memory intentionally: opening Kanak always starts in My Expenses.
 */
class ExpenseProfileManager {
    private val mutableActiveProfile = MutableStateFlow(ExpenseProfile.MY_EXPENSES)
    val activeProfile: StateFlow<ExpenseProfile> = mutableActiveProfile.asStateFlow()

    fun select(profile: ExpenseProfile) {
        mutableActiveProfile.value = profile
    }

    fun returnToMyExpenses() = select(ExpenseProfile.MY_EXPENSES)
}
