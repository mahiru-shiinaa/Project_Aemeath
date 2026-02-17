package com.aemeath.app.ui.main

import androidx.lifecycle.ViewModel
import com.aemeath.app.data.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    val isSetupComplete = preferencesRepository.isSetupComplete
    val theme = preferencesRepository.theme
}