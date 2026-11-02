package com.fretka46.fDailyRewards.Core;

import com.fretka46.fDailyRewards.Storage.Database;
import com.fretka46.fDailyRewards.Storage.MonthPreview;

import java.time.YearMonth;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Přepnutí ukázky na spawnu (NPC, modely) na začátku měsíce odměn. Příkazy ze sekce {@code preview.commands}
 * se spustí jednou za měsíc; spuštěný měsíc se pamatuje v {@code meta.preview_month}, takže je denní restart
 * nespustí znovu. Soubor bez příkazů (např. rewardsTemplate.yml) nic nespouští a měsíc neoznačí.
 */
public final class PreviewService {
    static final String META_KEY = "preview_month";

    private final Database db;
    private final Supplier<YearMonth> loadedMonth;
    private final Supplier<MonthPreview> preview;
    private final Consumer<String> dispatch;
    private final Consumer<String> log;

    public PreviewService(Database db, Supplier<YearMonth> loadedMonth, Supplier<MonthPreview> preview,
                          Consumer<String> dispatch, Consumer<String> log) {
        this.db = db;
        this.loadedMonth = loadedMonth;
        this.preview = preview;
        this.dispatch = dispatch;
        this.log = log;
    }

    /** Měsíc, pro který už příkazy proběhly, nebo {@code null}. */
    public String appliedMonth() {
        return db.meta(META_KEY);
    }

    /** Spustí příkazy, pokud pro načtený měsíc ještě neproběhly. Vrací počet spuštěných příkazů. */
    public int applyIfNeeded() {
        YearMonth month = loadedMonth.get();
        if (month == null || month.toString().equals(appliedMonth())) return 0;
        return apply();
    }

    /** Spustí příkazy načteného měsíce bez ohledu na to, zda už proběhly (admin příkaz). */
    public int apply() {
        YearMonth month = loadedMonth.get();
        MonthPreview p = preview.get();
        if (month == null || p.commands().isEmpty()) return 0;
        int ok = 0;
        for (String cmd : p.commands()) {
            try {
                dispatch.accept(cmd);
                ok++;
            } catch (RuntimeException ex) {
                log.accept("Preview: příkaz '" + cmd + "' selhal: " + ex.getMessage());
            }
        }
        db.setMeta(META_KEY, month.toString());
        log.accept("Preview měsíce " + month + ": spuštěno " + ok + "/" + p.commands().size() + " příkazů.");
        return ok;
    }
}
