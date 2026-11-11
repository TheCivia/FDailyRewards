package com.fretka46.fDailyRewards.Commands;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Hledání cíle Tebex/AT příkazů: offline účet jen při přesné shodě jména. */
class AdminNameTest {
    static UUID offline(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void offlineAccountNeedsExactName() {
        UUID petr = offline("Petr");
        assertEquals(3, petr.version());
        assertTrue(Admin.nameMatches(petr, "Petr", "Petr"));
        assertFalse(Admin.nameMatches(petr, "Petr", "petr"), "nákup pro petr nesmí dostat účet Petr");
        assertFalse(Admin.nameMatches(petr, null, "Petr"));
    }

    @Test
    void mojangAccountIgnoresCase() {
        UUID mojang = UUID.randomUUID();
        assertEquals(4, mojang.version());
        assertTrue(Admin.nameMatches(mojang, "Petr", "petr"));
    }
}
