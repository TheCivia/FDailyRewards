package com.fretka46.fDailyRewards.Utils;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class VaultPenizeTest {

    @Test
    void rozpozna_vyplatu() {
        assertEquals(new VaultPenize.Vyplata("Tikka_1", 1000), VaultPenize.rozpoznej("cmi money give Tikka_1 1000"));
        assertEquals(new VaultPenize.Vyplata("Tikka_1", 250.5), VaultPenize.rozpoznej("/cmi money give Tikka_1 250.5 -s"));
        assertEquals(new VaultPenize.Vyplata("abc", 5), VaultPenize.rozpoznej("CMI Money Give abc 5"));
    }

    @Test
    void ostatni_nechá_konzoli() {
        assertNull(VaultPenize.rozpoznej("cmi money take Tikka_1 1000"));
        assertNull(VaultPenize.rozpoznej("cmi money give Tikka_1 0"));
        assertNull(VaultPenize.rozpoznej("cmi money give Tikka_1 {amount}"));
        assertNull(VaultPenize.rozpoznej("cmi money give Tikka_1 1000 -s navic"));
        assertNull(VaultPenize.rozpoznej("money give Tikka_1 1000"));
        assertNull(VaultPenize.rozpoznej("cmi money give {player} 1000"));
        assertNull(VaultPenize.rozpoznej("lp user Tikka_1 permission set x"));
    }

    /** Peněžní odměny v configu odměn serveru (configy-nove) po dosazení hráče jdou přes Vault. */
    @Test
    void odmeny_v_configu() throws IOException {
        Path dir = Path.of("../../TheCivia-navrhy/configy-nove/FDailyRewards");
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.isDirectory(dir), "configy-nove nejsou vedle repa");
        Pattern p = Pattern.compile("\"(cmi money give [^\"]*)\"");
        int n = 0;
        try (Stream<Path> s = Files.walk(dir)) {
            for (Path f : (Iterable<Path>) s.filter(x -> x.toString().endsWith(".yml"))::iterator) {
                Matcher m = p.matcher(Files.readString(f, StandardCharsets.UTF_8));
                while (m.find()) {
                    n++;
                    String cmd = m.group(1).replace("%player_name%", "Tikka_1");
                    assertNotNull(VaultPenize.rozpoznej(cmd), f + ": " + cmd);
                }
            }
        }
        assertTrue(n > 0);
    }
}
