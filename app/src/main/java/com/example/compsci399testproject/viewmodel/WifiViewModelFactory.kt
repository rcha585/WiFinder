package com.example.compsci399testproject.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class WifiScannerViewModelFactory(
    private val application: Context
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WifiViewModel::class.java)) {
            return WifiViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}