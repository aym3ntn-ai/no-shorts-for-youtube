package com.noshorts.android

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast

/** Quick-settings tile to pause and resume blocking without opening the app. */
class BlockerTileService : TileService() {

    private val settings by lazy { Settings(this) }

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        if (!Settings.isAccessibilityServiceEnabled(this)) {
            Toast.makeText(this, R.string.tile_unavailable, Toast.LENGTH_LONG).show()
            refresh()
            return
        }
        settings.enabled = !settings.enabled
        refresh()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val serviceOn = Settings.isAccessibilityServiceEnabled(this)
        tile.state = when {
            !serviceOn -> Tile.STATE_UNAVAILABLE
            settings.enabled -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.label = getString(R.string.tile_label)
        tile.updateTile()
    }
}
