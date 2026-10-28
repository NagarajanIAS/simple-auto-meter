package com.nammameter.app.routing

import com.nammameter.app.ui.screens.SettingsViewModel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TileUrlTest {
    @Test
    fun `only https templates with placeholders are accepted`() {
        assertTrue(SettingsViewModel.isValidTileUrl("https://tile.openstreetmap.org/{z}/{x}/{y}.png"))
        assertTrue(SettingsViewModel.isValidTileUrl("https://tiles.example.com/v1/{z}/{x}/{y}@2x.png?key=abc"))
        assertFalse(SettingsViewModel.isValidTileUrl("http://tile.openstreetmap.org/{z}/{x}/{y}.png"))
        assertFalse(SettingsViewModel.isValidTileUrl("https://tile.openstreetmap.org/{z}/{x}.png"))
        assertFalse(SettingsViewModel.isValidTileUrl("https://tile.openstreetmap.org/{z}/{x}/{y} .png"))
        assertFalse(SettingsViewModel.isValidTileUrl("not a url {z}{x}{y}"))
        assertFalse(SettingsViewModel.isValidTileUrl("file:///{z}/{x}/{y}"))
    }
}
