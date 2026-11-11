package com.fretka46.fDailyRewards.Utils;

import com.fretka46.fDailyRewards.Core.AntiAlt;
import com.fretka46.fDailyRewards.Core.ClaimState.Reason;
import com.fretka46.fDailyRewards.FDailyRewards;
import org.bukkit.Statistic;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.net.InetAddress;
import java.net.InetSocketAddress;

/**
 * Serverová strana brány proti altům: nastavení z configu, údaje o hráči (IP, odehraný čas) a zpráva o zamítnutí.
 * Logika je v {@link AntiAlt} a {@code RewardService.claim(uuid, day, ctx)}.
 */
public final class AntiAltGate {
    private AntiAltGate() {}

    public static final String BYPASS_PERMISSION = "fdailyrewards.bypass.antialt";

    // Výchozí texty jsou v kódu: nové klíče se do configu na serveru samy nedoplní.
    static final String DEFAULT_IP_LIMIT = "<hover:show_text:'<white>倃</white> <#F4EBDD><bold>Denní odměny</bold><newline><newline>"
            + "<#F4EBDD>Limit účtů z jedné sítě za den: <#ffaa00>{max}</#ffaa00>.<newline>"
            + "<#F4EBDD>Zkus to znovu po resetu dne.'><!shadow><white>候</white></!shadow> <#F4EBDD>Z tvé sítě už si dnes odměnu vyzvedl "
            + "povolený počet účtů (limit <#ffaa00>{max}</#ffaa00>).</hover>";
    static final String DEFAULT_PLAYTIME = "<hover:show_text:'<white>倃</white> <#F4EBDD><bold>Denní odměny</bold><newline><newline>"
            + "<#F4EBDD>Nové účty si denní odměnu<newline><#F4EBDD>vyzvednou po <#ffaa00>{minutes}</#ffaa00> hraní.'>"
            + "<!shadow><white>候</white></!shadow> <#F4EBDD>Denní odměnu si vyzvedneš po <#ffaa00>{minutes}</#ffaa00> hraní, "
            + "zbývá <#ffaa00>{left}</#ffaa00>.</hover>";

    public static AntiAlt.Settings settings(FileConfiguration cfg) {
        return new AntiAlt.Settings(
                Math.max(0, cfg.getInt("anti_alt.max_accounts_per_ip", 0)),
                Math.max(0, cfg.getInt("anti_alt.min_playtime_minutes", 0)));
    }

    /** Údaje o hráči pro bránu; null, když je brána vypnutá (pak se nic nečte ani nezapisuje). */
    public static AntiAlt.Context context(Player player) {
        FDailyRewards plugin = FDailyRewards.get();
        AntiAlt.Settings s = plugin.rewards().antiAlt();
        if (!s.anyOn()) return null;
        boolean bypass = player.hasPermission(BYPASS_PERMISSION);
        String hash = null;
        if (s.ipLimitOn() && !bypass) {
            InetSocketAddress sock = player.getAddress();
            InetAddress addr = sock == null ? null : sock.getAddress();
            if (addr == null) Log.warning("Brána proti altům: hráč " + player.getName() + " nemá známou IP, limit IP se neuplatní.");
            else hash = AntiAlt.hashIp(plugin.ipSalt(), addr);
        }
        long minutes = s.playtimeOn() ? player.getStatistic(Statistic.PLAY_ONE_MINUTE) / 1200L : 0L;
        return new AntiAlt.Context(hash, minutes, bypass);
    }

    /** Zpráva + zvuk zamítnutí od brány. */
    public static void deny(Player player, Reason reason, AntiAlt.Context ctx) {
        var cfg = FDailyRewards.get().getConfig();
        AntiAlt.Settings s = FDailyRewards.get().rewards().antiAlt();
        String msg;
        if (reason == Reason.IP_LIMIT) {
            msg = or(cfg.getString("anti_alt_ip_limit"), DEFAULT_IP_LIMIT).replace("{max}", String.valueOf(s.maxAccountsPerIp()));
        } else {
            long left = s.minPlaytimeMinutes() - (ctx == null ? 0 : ctx.playedMinutes());
            msg = or(cfg.getString("anti_alt_playtime"), DEFAULT_PLAYTIME)
                    .replace("{minutes}", AntiAlt.formatMinutes(s.minPlaytimeMinutes()))
                    .replace("{left}", AntiAlt.formatMinutes(Math.max(1, left)));
        }
        Messages.sendMessage(player, msg);
        RewardActions.playSound(player, "sound_denied");
    }

    private static String or(String v, String def) {
        return v == null || v.isBlank() ? def : v;
    }
}
