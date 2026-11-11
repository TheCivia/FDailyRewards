package com.fretka46.fDailyRewards.Core;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Volitelná brána proti farmení denních odměn přes alty (offline mode = účty zdarma). Bez Bukkitu.
 *
 * <p>Dvě nezávislé kontroly, obě vypnuté hodnotou 0 (výchozí v kódu, protože nové klíče se do configu na serveru
 * samy nedoplní):
 * <ul>
 *   <li>{@code maxAccountsPerIp}: kolik <b>různých účtů</b> smí z jedné IP vyzvednout běžnou denní odměnu v jednom
 *       dni odměn (reset 04:15). Jeden účet může v jednom dni vyzvednout víc dní (VIP den s Passem), ten se počítá
 *       jednou.</li>
 *   <li>{@code minPlaytimeMinutes}: minimální odehraný čas účtu (statistika serveru), než si může odměnu vyzvednout.</li>
 * </ul>
 * IP se nikdy neukládá: do DB jde jen HMAC-SHA256 se solí z tabulky {@code meta}. IPv6 se hashuje jen podle
 * prefixu /64 (jedna domácnost má celý blok adres).
 */
public final class AntiAlt {
    private AntiAlt() {}

    public static final String SALT_META_KEY = "ip_hash_salt";
    public static final int IPV6_PREFIX_BYTES = 8; // /64

    public record Settings(int maxAccountsPerIp, int minPlaytimeMinutes) {
        public static final Settings OFF = new Settings(0, 0);

        public boolean ipLimitOn() {
            return maxAccountsPerIp > 0;
        }

        public boolean playtimeOn() {
            return minPlaytimeMinutes > 0;
        }

        public boolean anyOn() {
            return ipLimitOn() || playtimeOn();
        }
    }

    /**
     * Údaje o hráči v okamžiku vyzvednutí (sestaví je volající, který má přístup k serveru).
     *
     * @param ipHash hash IP z {@link #hashIp}, null = adresa není známá (limit IP se pak neuplatní)
     * @param playedMinutes odehrané minuty účtu
     * @param bypass hráč má právo obejít bránu
     */
    public record Context(String ipHash, long playedMinutes, boolean bypass) {}

    /** Nová náhodná sůl (32 bajtů, Base64). */
    public static String newSalt() {
        byte[] b = new byte[32];
        new SecureRandom().nextBytes(b);
        return Base64.getEncoder().encodeToString(b);
    }

    /** Bajty adresy, které se hashují: IPv4 celá, IPv6 jen prvních 64 bitů. */
    static byte[] significantBytes(InetAddress address) {
        byte[] raw = address.getAddress();
        return address instanceof Inet6Address ? Arrays.copyOf(raw, IPV6_PREFIX_BYTES) : raw;
    }

    /** HMAC-SHA256 adresy se solí, hex. Null pro null adresu. */
    public static String hashIp(String saltBase64, InetAddress address) {
        if (address == null) return null;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(Base64.getDecoder().decode(saltBase64), "HmacSHA256"));
            byte[] fam = {(byte) (address instanceof Inet6Address ? 6 : 4)};
            mac.update(fam);
            return HexFormat.of().formatHex(mac.doFinal(significantBytes(address)));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Hash IP selhal: " + e.getMessage(), e);
        }
    }

    /** Minuty jako {@code 45m}, {@code 1h 5m}, {@code 2h} (časy bez mezery mezi číslem a jednotkou). */
    public static String formatMinutes(long minutes) {
        long m = Math.max(0, minutes);
        if (m < 60) return m + "m";
        long h = m / 60, r = m % 60;
        return r == 0 ? h + "h" : h + "h " + r + "m";
    }
}
