package dev.dbvault.web;

import java.util.Locale;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Artifact sizes as people read them, for the templates: {@code ${byteSizes.readable(n)}}.
 * Binary units, one decimal from KiB up, and the exact byte count below that. Offered to every
 * page as a model attribute, so controller slice tests see it too.
 */
@ControllerAdvice
public class ByteSizes {

    @ModelAttribute("byteSizes")
    ByteSizes byteSizes() {
        return this;
    }

    private static final String[] UNITS = {"KB", "MB", "GB", "TB"};

    public String readable(Long bytes) {
        if (bytes == null) {
            return "—";
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        double value = bytes;
        int unit = -1;
        while (value >= 1024 && unit < UNITS.length - 1) {
            value /= 1024;
            unit++;
        }
        return String.format(Locale.ROOT, "%.1f %s", value, UNITS[unit]);
    }
}
