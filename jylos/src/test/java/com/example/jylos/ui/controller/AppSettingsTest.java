package com.example.jylos.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AppSettingsTest {

    @Test
    void shouldReloadVaultLiveWhenFilesystemPathChangesInsideFilesystemMode() {
        AppSettings.StorageSwitchPlan plan = AppSettings.planStorageSwitch(
                "filesystem",
                "/vaults/one",
                "filesystem",
                "/vaults/two");

        assertTrue(plan.changed());
        assertTrue(plan.reloadVaultLive());
        assertEquals("filesystem", plan.storageType());
        assertEquals("/vaults/two", plan.filesystemPath());
    }

    @Test
    void shouldNotTreatSameFilesystemPathAsChange() {
        AppSettings.StorageSwitchPlan plan = AppSettings.planStorageSwitch(
                "filesystem",
                "/vaults/one",
                "filesystem",
                "/vaults/one");

        assertFalse(plan.changed());
        assertFalse(plan.reloadVaultLive());
    }

    @Test
    void shouldKeepRestartFlowWhenEnteringFilesystemFromSqlite() {
        AppSettings.StorageSwitchPlan plan = AppSettings.planStorageSwitch(
                "sqlite",
                "",
                "filesystem",
                "/vaults/one");

        assertTrue(plan.changed());
        assertFalse(plan.reloadVaultLive());
        assertEquals("filesystem", plan.storageType());
        assertEquals("/vaults/one", plan.filesystemPath());
    }

    @Test
    void shouldKeepRestartFlowWhenLeavingFilesystemForSqlite() {
        AppSettings.StorageSwitchPlan plan = AppSettings.planStorageSwitch(
                "filesystem",
                "/vaults/one",
                "sqlite",
                "");

        assertTrue(plan.changed());
        assertFalse(plan.reloadVaultLive());
        assertEquals("sqlite", plan.storageType());
        assertEquals("", plan.filesystemPath());
    }
}
