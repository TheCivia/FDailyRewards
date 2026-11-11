package com.fretka46.fDailyRewards.Core;

import com.fretka46.fDailyRewards.Core.ClaimState.Reason;
import com.fretka46.fDailyRewards.Storage.Database;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetAddress;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static com.fretka46.fDailyRewards.Core.Fixtures.at;
import static org.junit.jupiter.api.Assertions.*;

/** Brána proti altům (1.3.0): vypnuto = beze změny, limit účtů z jedné IP, minimální odehraný čas. */
class AntiAltTest {
    final YearMonth oct = YearMonth.of(2026, 10);
    final String salt = AntiAlt.newSalt();

    record Gate(Database db, Fixtures.MutableClock clock, RewardService svc, AtomicReference<AntiAlt.Settings> settings) {}

    static Gate gate(int maxPerIp, int minPlay) {
        Database db = Database.openMemory();
        var clock = new Fixtures.MutableClock(at(2026, 10, 10, 12, 0));
        TimeService t = Fixtures.time(clock);
        PassService pass = new PassService(db, t, u -> false, () -> 0);
        RewardCatalog cat = Fixtures.catalog(31);
        var settings = new AtomicReference<>(new AntiAlt.Settings(maxPerIp, minPlay));
        RewardService svc = new RewardService(db, t, () -> cat, pass, () -> 0, m -> {}, settings::get);
        return new Gate(db, clock, svc, settings);
    }

    String hash(String ip) throws Exception {
        return AntiAlt.hashIp(salt, InetAddress.getByName(ip));
    }

    AntiAlt.Context ctx(String ip, long minutes) throws Exception {
        return new AntiAlt.Context(hash(ip), minutes, false);
    }

    // ------------------------------------------------------------------ vypnuto

    @Test
    void offMeansUnchangedAndWritesNoIpRows() throws Exception {
        Gate g = gate(0, 0);
        for (int i = 0; i < 10; i++) {
            UUID alt = UUID.randomUUID();
            assertEquals(Reason.OK, g.svc().claim(alt, 1, ctx("10.0.0.1", 0)));
        }
        assertEquals(0, g.db().ipClaimRows());
        assertNull(g.svc().gateWouldDeny(UUID.randomUUID(), ctx("10.0.0.1", 0)));
    }

    @Test
    void offGivesSameResultsAsClaimWithoutGate() throws Exception {
        Gate a = gate(0, 0), b = gate(0, 0);
        UUID p = UUID.randomUUID();
        for (int day = 1; day <= 6; day++) {
            for (int click : new int[]{day, day + 1, 4}) {
                assertEquals(a.svc().claim(p, click), b.svc().claim(p, click, ctx("10.0.0.1", 0)), "den " + day + " klik " + click);
            }
            a.clock().advanceDays(1);
            b.clock().advanceDays(1);
        }
        assertEquals(a.db().claimedDays(p, oct), b.db().claimedDays(p, oct));
    }

    @Test
    void nullContextBypassesGate() {
        Gate g = gate(1, 60);
        assertEquals(Reason.OK, g.svc().claim(UUID.randomUUID(), 1, null));
        assertEquals(Reason.OK, g.svc().claim(UUID.randomUUID(), 1));
        assertEquals(0, g.db().ipClaimRows());
    }

    // ------------------------------------------------------------------ limit IP

    @Test
    void ipLimitCountsDistinctAccountsPerRewardDay() throws Exception {
        Gate g = gate(2, 0);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        assertEquals(Reason.OK, g.svc().claim(a, 1, ctx("10.0.0.1", 0)));
        assertEquals(Reason.OK, g.svc().claim(b, 1, ctx("10.0.0.1", 0)));
        assertEquals(Reason.IP_LIMIT, g.svc().claim(c, 1, ctx("10.0.0.1", 0)));
        assertEquals(Set.of(), g.db().claimedDays(c, oct), "zamítnutý účet nesmí mít zápis");
        assertEquals(Reason.IP_LIMIT, g.svc().gateWouldDeny(c, ctx("10.0.0.1", 0)));
        // jiná IP projde
        assertEquals(Reason.OK, g.svc().claim(c, 1, ctx("10.0.0.2", 0)));
        // další den odměn (po 04:15) se počítá znovu
        g.clock().set(at(2026, 10, 11, 4, 14));
        assertEquals(Reason.IP_LIMIT, g.svc().claim(UUID.randomUUID(), 1, ctx("10.0.0.1", 0)), "04:14 je ještě předchozí den");
        g.clock().set(at(2026, 10, 11, 4, 15));
        assertEquals(Reason.OK, g.svc().claim(UUID.randomUUID(), 1, ctx("10.0.0.1", 0)));
    }

    @Test
    void sameAccountCanClaimVipDayTooWithLimitOne() throws Exception {
        Gate g = gate(1, 0);
        UUID p = UUID.randomUUID();
        g.db().addPassMonths(p, java.util.List.of(oct));
        for (int d = 1; d <= 2; d++) g.db().tryInsertClaim(p, oct, LocalDate.of(2026, 10, d), d, Database.Source.CLAIM);
        assertEquals(Reason.OK, g.svc().claim(p, 3, ctx("10.0.0.1", 0)));   // běžný den
        assertEquals(Reason.OK, g.svc().claim(p, 4, ctx("10.0.0.1", 0)));   // VIP den s Passem týž den, stejný účet
        assertEquals(1, g.db().ipClaimRows());
    }

    @Test
    void deniedForOtherReasonDoesNotConsumeIpSlot() throws Exception {
        Gate g = gate(1, 0);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        assertEquals(Reason.VIP_ONLY, g.svc().claim(a, 4, ctx("10.0.0.1", 0)));
        assertEquals(0, g.db().ipClaimRows());
        assertEquals(Reason.OK, g.svc().claim(b, 1, ctx("10.0.0.1", 0)));
    }

    @Test
    void bypassAndUnknownIpSkipLimit() throws Exception {
        Gate g = gate(1, 0);
        assertEquals(Reason.OK, g.svc().claim(UUID.randomUUID(), 1, ctx("10.0.0.1", 0)));
        assertEquals(Reason.OK, g.svc().claim(UUID.randomUUID(), 1, new AntiAlt.Context(hash("10.0.0.1"), 0, true)));
        assertEquals(Reason.OK, g.svc().claim(UUID.randomUUID(), 1, new AntiAlt.Context(null, 0, false)));
    }

    @Test
    void settingsReadOnEveryClaim() throws Exception {
        Gate g = gate(1, 0);
        assertEquals(Reason.OK, g.svc().claim(UUID.randomUUID(), 1, ctx("10.0.0.1", 0)));
        assertEquals(Reason.IP_LIMIT, g.svc().claim(UUID.randomUUID(), 1, ctx("10.0.0.1", 0)));
        g.settings().set(AntiAlt.Settings.OFF);                       // reload s vypnutou branou
        assertEquals(Reason.OK, g.svc().claim(UUID.randomUUID(), 1, ctx("10.0.0.1", 0)));
    }

    // ------------------------------------------------------------------ playtime

    @Test
    void playtimeGate() throws Exception {
        Gate g = gate(0, 30);
        UUID p = UUID.randomUUID();
        assertEquals(Reason.PLAYTIME, g.svc().claim(p, 1, ctx("10.0.0.1", 29)));
        assertEquals(Set.of(), g.db().claimedDays(p, oct));
        assertEquals(Reason.PLAYTIME, g.svc().gateWouldDeny(p, ctx("10.0.0.1", 29)));
        assertEquals(Reason.OK, g.svc().claim(p, 1, ctx("10.0.0.1", 30)));
        assertEquals(0, g.db().ipClaimRows(), "jen playtime = IP se nezapisuje");
    }

    @Test
    void playtimeDeniedDoesNotConsumeIpSlot() throws Exception {
        Gate g = gate(1, 30);
        assertEquals(Reason.PLAYTIME, g.svc().claim(UUID.randomUUID(), 1, ctx("10.0.0.1", 5)));
        assertEquals(0, g.db().ipClaimRows());
        assertEquals(Reason.OK, g.svc().claim(UUID.randomUUID(), 1, ctx("10.0.0.1", 60)));
    }

    // ------------------------------------------------------------------ hash, sůl, DB

    @Test
    void hashIsSaltedAndNeverContainsIp() throws Exception {
        String h = hash("192.168.1.20");
        assertEquals(64, h.length());
        assertFalse(h.contains("192.168"));
        assertEquals(h, hash("192.168.1.20"));
        assertNotEquals(h, AntiAlt.hashIp(AntiAlt.newSalt(), InetAddress.getByName("192.168.1.20")));
        assertNotEquals(h, hash("192.168.1.21"));
    }

    @Test
    void ipv6SamePrefix64IsOneNetwork() throws Exception {
        assertEquals(hash("2001:db8:1:2::1"), hash("2001:db8:1:2:abcd::99"));
        assertNotEquals(hash("2001:db8:1:2::1"), hash("2001:db8:1:3::1"));
    }

    @Test
    void saltGeneratedOnceAndKept(@TempDir Path dir) {
        Path file = dir.resolve("database.db");
        String s1;
        try (Database d = Database.openFile(file, x -> {})) {
            s1 = d.ipSalt(AntiAlt::newSalt);
            assertNotNull(s1);
            assertEquals(s1, d.ipSalt(() -> fail("sůl se nesmí generovat znovu")));
        }
        try (Database d = Database.openFile(file, x -> {})) {
            assertEquals(s1, d.ipSalt(() -> fail("sůl se nesmí generovat znovu")));
        }
    }

    @Test
    void cleanupDropsOldIpHashes() throws Exception {
        Gate g = gate(5, 0);
        g.svc().claim(UUID.randomUUID(), 1, ctx("10.0.0.1", 0));
        g.db().cleanup(LocalDate.of(2026, 10, 12), oct, s -> {});
        assertEquals(1, g.db().ipClaimRows());
        g.db().cleanup(LocalDate.of(2026, 10, 13), oct, s -> {});
        assertEquals(0, g.db().ipClaimRows());
    }

    /** Existující DB verze 2 (stav serveru s 1.2.x) se po startu 1.3.0 doplní o tabulku claim_ips. */
    @Test
    void migratesV2DatabaseAddsIpTable(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("database.db");
        UUID p = UUID.randomUUID();
        try (var c = DriverManager.getConnection("jdbc:sqlite:" + file); var st = c.createStatement()) {
            st.executeUpdate("CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
            st.executeUpdate("INSERT INTO meta VALUES ('schema_version','2')");
            st.executeUpdate("CREATE TABLE rewards_claimed (id INTEGER PRIMARY KEY AUTOINCREMENT, uuid TEXT NOT NULL, date TEXT NOT NULL,"
                    + " day INTEGER NOT NULL, source TEXT NOT NULL DEFAULT 'claim', month INTEGER NOT NULL DEFAULT 0)");
            st.executeUpdate("INSERT INTO rewards_claimed (uuid,date,day,source,month) VALUES ('" + p + "','2026-10-09',1,'claim',202610)");
        }
        StringBuilder log = new StringBuilder();
        try (Database d = Database.openFile(file, log::append)) {
            assertEquals(Set.of(1), d.claimedDays(p, oct));
            assertEquals(Database.IpReservation.NEW, d.tryReserveIpClaim("h", LocalDate.of(2026, 10, 10), p, 1));
            assertEquals("3", d.meta("schema_version"));
        }
        assertTrue(log.toString().contains("v2 -> v3"), log.toString());
    }

    @Test
    void formatMinutes() {
        assertEquals("45m", AntiAlt.formatMinutes(45));
        assertEquals("1h", AntiAlt.formatMinutes(60));
        assertEquals("1h 5m", AntiAlt.formatMinutes(65));
    }
}
