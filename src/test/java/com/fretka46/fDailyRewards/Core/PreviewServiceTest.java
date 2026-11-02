package com.fretka46.fDailyRewards.Core;

import com.fretka46.fDailyRewards.Storage.ConfigManager;
import com.fretka46.fDailyRewards.Storage.Database;
import com.fretka46.fDailyRewards.Storage.MonthPreview;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class PreviewServiceTest {
    final Database db = Database.openMemory();
    final AtomicReference<YearMonth> month = new AtomicReference<>(YearMonth.of(2026, 12));
    final AtomicReference<MonthPreview> preview = new AtomicReference<>(
            new MonthPreview(Map.of("mazlicek", "Wyverna"), List.of("a", "b")));
    final List<String> ran = new ArrayList<>();
    final PreviewService svc = new PreviewService(db, month::get, preview::get, ran::add, s -> {});

    @Test
    void runsOncePerMonthAndSurvivesRestart() {
        assertEquals(2, svc.applyIfNeeded());
        assertEquals(0, svc.applyIfNeeded());
        // "restart": nová služba nad stejnou DB
        var again = new PreviewService(db, month::get, preview::get, ran::add, s -> {});
        assertEquals(0, again.applyIfNeeded());
        assertEquals(List.of("a", "b"), ran);
        assertEquals("2026-12", svc.appliedMonth());

        month.set(YearMonth.of(2027, 1));
        assertEquals(2, svc.applyIfNeeded());
        assertEquals("2027-01", svc.appliedMonth());
    }

    @Test
    void forceApplyAndFailingCommand() {
        svc.applyIfNeeded();
        PreviewService failing = new PreviewService(db, month::get, preview::get,
                c -> { if (c.equals("a")) throw new IllegalStateException("x"); ran.add(c); }, s -> {});
        assertEquals(1, failing.apply());
        assertEquals(List.of("a", "b", "b"), ran);
    }

    @Test
    void fileWithoutCommandsDoesNotMarkMonth() {
        preview.set(MonthPreview.EMPTY);
        assertEquals(0, svc.applyIfNeeded());
        assertNull(svc.appliedMonth());
        // doplněné příkazy (reload) se pak spustí
        preview.set(new MonthPreview(Map.of(), List.of("c")));
        assertEquals(1, svc.applyIfNeeded());
    }

    @Test
    void readsPreviewSectionFromYaml() throws Exception {
        var y = new YamlConfiguration();
        y.loadFromString("preview:\n  names:\n    Mazlicek: Wyverna Wynnifred\n    set: ''\n  commands:\n    - meg npc model citizens:59 add x\n");
        MonthPreview p = ConfigManager.readPreview(y.getConfigurationSection("preview"));
        assertEquals("Wyverna Wynnifred", p.names().get("mazlicek"));
        assertEquals("", p.names().get("set"));
        assertEquals(List.of("meg npc model citizens:59 add x"), p.commands());
        assertSame(MonthPreview.EMPTY, ConfigManager.readPreview(null));
    }
}
