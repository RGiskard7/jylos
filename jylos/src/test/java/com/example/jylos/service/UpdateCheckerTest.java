package com.example.jylos.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.jylos.service.UpdateChecker.AssetInfo;
import com.google.gson.JsonParser;

class UpdateCheckerTest {

    @Test
    void detectsNewerSemanticVersionNumerically() {
        assertTrue(UpdateChecker.isNewerVersion("v1.10.0", "v1.9.9"));
        assertTrue(UpdateChecker.isNewerVersion("2.4.1", "2.4.0"));
        assertTrue(UpdateChecker.isNewerVersion("v2.5.0", "2.4.9"));
    }

    @Test
    void ignoresLeadingVAndRejectsSameOrOlderVersions() {
        assertFalse(UpdateChecker.isNewerVersion("v2.4.0", "2.4.0"));
        assertFalse(UpdateChecker.isNewerVersion("2.4.0", "v2.4.1"));
        assertFalse(UpdateChecker.isNewerVersion("v2.3.9", "2.4.0"));
    }

    @Test
    void parsesAssetNameUrlSizeAndDigestFromTheReleaseApiPayload() {
        var json = JsonParser.parseString("""
                {
                  "assets": [
                    {
                      "name": "jylos-windows-x64.exe",
                      "browser_download_url": "https://github.com/RGiskard7/jylos/releases/download/v2.6.0/jylos-windows-x64.exe",
                      "size": 123456,
                      "digest": "sha256:abcdef0123456789"
                    }
                  ]
                }
                """).getAsJsonObject();

        List<AssetInfo> assets = UpdateChecker.parseAssets(json);

        assertEquals(1, assets.size());
        AssetInfo asset = assets.get(0);
        assertEquals("jylos-windows-x64.exe", asset.name());
        assertEquals("https://github.com/RGiskard7/jylos/releases/download/v2.6.0/jylos-windows-x64.exe",
                asset.browserDownloadUrl());
        assertEquals(123456L, asset.size());
        assertEquals("sha256:abcdef0123456789", asset.digest());
    }

    @Test
    void toleratesAnAssetWithNoDigestFieldInsteadOfFailingTheWholeParse() {
        var json = JsonParser.parseString("""
                {
                  "assets": [
                    { "name": "jylos.jar", "browser_download_url": "https://example.invalid/jylos.jar", "size": 42 }
                  ]
                }
                """).getAsJsonObject();

        List<AssetInfo> assets = UpdateChecker.parseAssets(json);

        assertEquals(1, assets.size());
        assertNull(assets.get(0).digest(), "a missing digest field must parse as null, not throw");
    }

    @Test
    void aReleaseWithNoAssetsFieldAtAllParsesAsAnEmptyList() {
        var json = JsonParser.parseString("{}").getAsJsonObject();
        assertTrue(UpdateChecker.parseAssets(json).isEmpty());
    }
}
