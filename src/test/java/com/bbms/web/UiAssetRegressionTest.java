package com.bbms.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class UiAssetRegressionTest {
    @Test
    void inventoryZeroStockToggleHasItsIntendedClassRule() throws IOException {
        String css = resource("/static/css/app.css");
        assertTrue(css.contains(".check-row{display:flex;gap:9px;align-items:center}"));
        assertFalse(java.util.regex.Pattern.compile("(?<![\\w.-])check-row\\s*\\{").matcher(css).find());
    }

    @Test
    void dashboardStatsCollapseAtTabletAndMobileBreakpoints() throws IOException {
        String css = resource("/static/css/app.css");
        int tablet = css.indexOf("@media (max-width: 1100px)");
        assertTrue(tablet >= 0);
        String tabletRules = css.substring(tablet, css.indexOf("@media (max-width: 760px)", tablet));
        assertTrue(tabletRules.contains(".stats-grid"));
        assertTrue(tabletRules.contains("repeat(2, minmax(0, 1fr))"));
        int mobile = css.indexOf("@media (max-width: 540px)");
        assertTrue(mobile > tablet);
        assertTrue(css.substring(mobile).contains(".stats-grid"));
    }

    private String resource(String name) throws IOException {
        try (var stream = getClass().getResourceAsStream(name)) {
            assertNotNull(stream, name + " must be packaged");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
