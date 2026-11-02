package com.fretka46.fDailyRewards.Storage;

import java.util.List;
import java.util.Map;

/**
 * Sekce {@code preview} měsíčního souboru odměn: ukázka na spawnu (NPC se skinem, setem a mazlíčkem měsíce).
 *
 * @param names    texty pro {@code %fdr_preview_<klíč>%} (např. mazlicek, set, skin)
 * @param commands příkazy z konzole, které se spustí jednou při přechodu na tento měsíc
 */
public record MonthPreview(Map<String, String> names, List<String> commands) {
    public static final MonthPreview EMPTY = new MonthPreview(Map.of(), List.of());

    public MonthPreview {
        names = Map.copyOf(names);
        commands = List.copyOf(commands);
    }
}
