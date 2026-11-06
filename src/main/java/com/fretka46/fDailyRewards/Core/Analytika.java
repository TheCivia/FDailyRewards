package com.fretka46.fDailyRewards.Core;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Událost pro CiviaAnalytics bez závislosti na něm: plugin se hledá za běhu a volá přes reflexi.
 * Když neběží, volání nic neudělá. Nikdy nevyhazuje, sběr nesmí ovlivnit hru.
 * Vlastnosti jsou dvojice klíč, hodnota; klíče predmet a hodnota jdou do vlastních sloupců. Nikdy sem nepatří text od hráče.
 * Šablona pro všechny naše pluginy, zkopírovat a změnit balíček.
 */
public final class Analytika {

    private static Plugin plugin;
    private static Method track;
    /** jiná verze pluginu bez metody track: mlčet do další instance, ne do restartu */
    private static boolean bezMetody;
    private static long zalogovano;

    private Analytika() {}

    public static void track(Player hrac, String udalost, Object... vlastnosti) {
        try {
            Plugin p = Bukkit.getPluginManager().getPlugin("CiviaAnalytics");
            if (p == null || !p.isEnabled()) return;
            if (p != plugin) { // po reloadu je to nová instance
                plugin = p;
                track = null;
                bezMetody = false;
            }
            if (bezMetody) return;
            if (track == null) track = p.getClass().getMethod("track", Player.class, String.class, Map.class);
            Map<String, Object> m = new LinkedHashMap<>();
            for (int i = 0; i + 1 < vlastnosti.length; i += 2) m.put(String.valueOf(vlastnosti[i]), vlastnosti[i + 1]);
            track.invoke(p, hrac, udalost, m);
        } catch (NoSuchMethodException e) {
            bezMetody = true;
            Bukkit.getLogger().warning("[Analytika] CiviaAnalytics nemá metodu track, události se neposílají.");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            // přechodná chyba jednoho volání nesmí vypnout ostatní; do logu nejvýš jednou za minutu
            long ted = System.currentTimeMillis();
            if (ted - zalogovano > 60_000L) {
                zalogovano = ted;
                Bukkit.getLogger().warning("[Analytika] Událost " + udalost + " selhala: " + e);
            }
        }
    }
}
