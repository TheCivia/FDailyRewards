package com.fretka46.fDailyRewards.Utils;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Locale;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Výplata peněz z odměn přes Vault místo konzolového {@code cmi money give}.
 * <p>
 * Odměny v {@code rewardsTemplate.yml} a {@code rok/*.yml} zůstávají čitelné ({@code cmi money give %player_name% N -s}), plugin je za běhu
 * přeloží na {@link Economy#depositPlayer(OfflinePlayer, double)}. CiviaAnalytics (proxy Vaultu) pak u změny
 * zůstatku zapíše zdroj {@code FDailyRewards} místo {@code CMI}.
 * <p>
 * Třída sahá na API Vaultu, smí se načíst jen když je Vault zapnutý ({@link #zkus} to hlídá přes jméno pluginu
 * dřív, než JVM třídu Economy potřebuje).
 */
public final class VaultPenize {

    /** {@code cmi money give <hráč> <částka> [-s]}, nic dalšího za tím (jinak se nechá konzoli). */
    static final Pattern PRIKAZ = Pattern.compile(
            "^cmi\\s+money\\s+give\\s+([A-Za-z0-9_]{1,16})\\s+(\\d{1,12}(?:\\.\\d{1,2})?)(?:\\s+-s)?\\s*$",
            Pattern.CASE_INSENSITIVE);

    private VaultPenize() {}

    /** Rozpoznaný příkaz výplaty: jméno hráče a částka, nebo null. Bez Vaultu, kvůli testům. */
    public static Vyplata rozpoznej(String prikaz) {
        if (prikaz == null) return null;
        String cmd = prikaz.strip();
        if (cmd.startsWith("/")) cmd = cmd.substring(1);
        if (!cmd.toLowerCase(Locale.ROOT).startsWith("cmi ")) return null;
        Matcher m = PRIKAZ.matcher(cmd);
        if (!m.matches()) return null;
        double castka = Double.parseDouble(m.group(2));
        if (!(castka > 0) || !Double.isFinite(castka)) return null;
        return new Vyplata(m.group(1), castka);
    }

    public record Vyplata(String hrac, double castka) {}

    /**
     * Zkusí příkaz provést přes Vault. Volat na hlavním vlákně.
     * @return true = vyplaceno přes Vault, příkaz už nespouštět; false = spustit původní příkaz konzolí
     */
    public static boolean zkus(String prikaz, Logger log) {
        Vyplata v = rozpoznej(prikaz);
        if (v == null) return false;
        if (!Bukkit.getPluginManager().isPluginEnabled("Vault")) return false;
        return Vnitrek.vyplat(v, null, log);
    }

    /** Konzolový příkaz z configu: výplata peněz přes Vault, ostatní {@code dispatchCommand}. Hlavní vlákno. */
    public static void spust(String prikaz) {
        spust(prikaz, null);
    }

    /**
     * Jako {@link #spust(String)}, ale výplata jde přímo hráči {@code komu}, když příkaz zní na jeho přesné jméno
     * (nehledá se znovu podle jména bez ohledu na velikost písmen, v offline mode by to mohl být jiný účet).
     */
    public static void spust(String prikaz, OfflinePlayer komu) {
        Vyplata v = rozpoznej(prikaz);
        if (v != null && Bukkit.getPluginManager().isPluginEnabled("Vault")
                && Vnitrek.vyplat(v, cil(v, komu), Bukkit.getLogger())) return;
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), prikaz);
    }

    /** Hráč pro výplatu: zadaný, když sedí jméno přesně; jinak null (dohledá se podle jména). */
    static OfflinePlayer cil(Vyplata v, OfflinePlayer komu) {
        return komu != null && v.hrac().equals(komu.getName()) ? komu : null;
    }

    /** Oddělené, aby se třídy Vaultu načetly až po kontrole, že Vault běží. */
    private static final class Vnitrek {
        static boolean vyplat(Vyplata v, OfflinePlayer znamy, Logger log) {
            // registrace se bere při každé výplatě: proxy CiviaAnalytics nebo CMI se mohou přeregistrovat
            RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
            if (rsp == null) return false;
            OfflinePlayer p = znamy;
            if (p == null) p = Bukkit.getPlayerExact(v.hrac());
            if (p == null) p = Bukkit.getOfflinePlayerIfCached(v.hrac());
            if (p == null) return false;
            // offline účet (UUID ne verze 4) se jménem lišícím se velikostí písmen je jiný účet: nechat konzoli
            if (p.getUniqueId().version() != 4 && !v.hrac().equals(p.getName())) return false;
            EconomyResponse r = rsp.getProvider().depositPlayer(p, v.castka());
            if (r != null && r.transactionSuccess()) return true;
            log.warning("Vault odmítl výplatu " + v.castka() + " hráči " + v.hrac() + " ("
                    + (r == null ? "bez odpovědi" : r.errorMessage) + "), zkouším příkaz CMI");
            return false;
        }
    }
}
