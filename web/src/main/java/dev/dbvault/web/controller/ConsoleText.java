package dev.dbvault.web.controller;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;

/**
 * A flash message in the language of the request that set it (ADR-042). The next page shows it
 * to the same browser, which keeps its language in the {@code dbvault_lang} cookie.
 */
final class ConsoleText {

    private ConsoleText() {
    }

    static String of(MessageSource messages, String code, Object... args) {
        return messages.getMessage(code, args, LocaleContextHolder.getLocale());
    }
}
