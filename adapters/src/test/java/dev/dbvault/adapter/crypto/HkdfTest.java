package dev.dbvault.adapter.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HexFormat;

import org.junit.jupiter.api.Test;

/** The RFC 5869 test vectors for HMAC-SHA256, and the guards dbvault adds. */
class HkdfTest {

    private static final HexFormat HEX = HexFormat.of();

    @Test
    void matchesRfc5869TestCase1() {
        byte[] okm = Hkdf.derive(
                HEX.parseHex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b"),
                HEX.parseHex("000102030405060708090a0b0c"),
                HEX.parseHex("f0f1f2f3f4f5f6f7f8f9"),
                42);
        assertThat(HEX.formatHex(okm)).isEqualTo(
                "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865");
    }

    @Test
    void anEmptySaltAndNoSaltAreTheSame() {
        byte[] okm = Hkdf.derive(
                HEX.parseHex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b"), new byte[0], "x".getBytes(), 42);
        byte[] again = Hkdf.derive(
                HEX.parseHex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b"), null, "x".getBytes(), 42);
        assertThat(okm).isEqualTo(again);
    }

    @Test
    void differentInfoGivesIndependentKeys() {
        byte[] master = new byte[32];
        assertThat(Hkdf.derive(master, null, "target:a".getBytes(), 32))
                .isNotEqualTo(Hkdf.derive(master, null, "target:b".getBytes(), 32));
    }

    @Test
    void refusesAnEmptyInfoOrAnImpossibleLength() {
        assertThatThrownBy(() -> Hkdf.derive(new byte[32], null, new byte[0], 32))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Hkdf.derive(new byte[32], null, "x".getBytes(), 255 * 32 + 1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
