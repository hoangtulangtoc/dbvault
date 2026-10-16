package dev.dbvault.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ByteSizesTest {

    private final ByteSizes sizes = new ByteSizes();

    @Test
    void readsLikeAPersonWould() {
        assertThat(sizes.readable(null)).isEqualTo("—");
        assertThat(sizes.readable(0L)).isEqualTo("0 B");
        assertThat(sizes.readable(1023L)).isEqualTo("1023 B");
        assertThat(sizes.readable(1024L)).isEqualTo("1.0 KB");
        assertThat(sizes.readable(104_448L)).isEqualTo("102.0 KB");
        assertThat(sizes.readable(61_301_298L)).isEqualTo("58.5 MB");
        assertThat(sizes.readable(5L * 1024 * 1024 * 1024 * 1024 * 3)).isEqualTo("15.0 TB");
    }
}
