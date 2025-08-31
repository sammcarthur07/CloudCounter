package com.sam.cloudcounter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class SessionStatsViewModelFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SessionStatsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SessionStatsViewModel() as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
