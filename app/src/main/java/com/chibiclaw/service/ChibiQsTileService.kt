package com.chibiclaw.service

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.chibiclaw.executor.KillSwitch
import com.chibiclaw.state.ChibiState
import com.chibiclaw.state.ChibiStateMachine
import com.chibiclaw.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Phase 5.6 — Quick Settings tile.
 *
 * Gives the user a 1-tap way to *pause* Chibi (kill switch) or *wake*
 * her from the notification shade. The tile's state mirrors the
 * current [ChibiStateMachine] so it shows ACTIVE when a command is
 * running and INACTIVE when idle or killed.
 *
 * Tap toggles the [KillSwitch]; long-press falls back to MainActivity
 * (the user chose to open the app from the shade).
 */
@RequiresApi(Build.VERSION_CODES.N)
@AndroidEntryPoint
class ChibiQsTileService : TileService() {

    @Inject lateinit var stateMachine: ChibiStateMachine
    @Inject lateinit var killSwitch: KillSwitch

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        val tile = qsTile ?: return
        if (killSwitch.isActive()) {
            killSwitch.reset()
        } else {
            // Pause: activate kill switch AND open the app so the user
            // can resume once they've checked what Chibi was doing.
            killSwitch.activate()
            val launch = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try { startActivityAndCollapse(launch) } catch (_: Exception) {}
        }
        refresh()
    }

    override fun onTileAdded() {
        super.onTileAdded()
        refresh()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val killed = killSwitch.isActive()
        val state = stateMachine.current
        val active = !killed && state != ChibiState.IDLE && state != ChibiState.ERROR_RECOVERY
        tile.state = if (killed) Tile.STATE_INACTIVE
                     else if (active) Tile.STATE_ACTIVE
                     else Tile.STATE_INACTIVE
        tile.label = when {
            killed -> "Chibi: paused"
            active -> "Chibi: working"
            else   -> "Chibi: idle"
        }
        tile.contentDescription = "ChibiClaw quick control"
        tile.updateTile()
    }
}
