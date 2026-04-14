package com.chibiclaw.ui.bootstrap

import androidx.lifecycle.ViewModel
import com.chibiclaw.core.ChibiBootstrapper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Phase 10 — thin bridge between [BootstrapScreen] and
 * [ChibiBootstrapper]. Just forwards the state flow and exposes
 * [start] / [retry] so the UI can kick off the pipeline and recover
 * from a failed load.
 *
 * **Phase 10 fix:** no more `onReady` callback — navigation is driven
 * by observing [state] in BootstrapScreen's `LaunchedEffect`. This
 * ensures navigation always runs on the Main thread.
 */
@HiltViewModel
class BootstrapViewModel @Inject constructor(
    private val bootstrapper: ChibiBootstrapper
) : ViewModel() {

    val state: StateFlow<ChibiBootstrapper.State> = bootstrapper.state

    fun start() {
        bootstrapper.bootstrap()
    }

    fun retry() {
        bootstrapper.reset()
        bootstrapper.bootstrap()
    }
}
