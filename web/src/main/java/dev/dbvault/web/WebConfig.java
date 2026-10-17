package dev.dbvault.web;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

@Configuration
class WebConfig implements WebMvcConfigurer {

    /** The console's languages; anything else asked for is English (ADR-042). */
    static final List<Locale> LANGUAGES = List.of(Locale.ENGLISH, Locale.forLanguageTag("vi"));

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // There is one thing to look at so far, so the root is not a dashboard.
        registry.addRedirectViewController("/", "/databases");
    }

    /**
     * {@code ?lang=vi} on any console page switches the language and is kept in a cookie;
     * without one the browser's {@code Accept-Language} decides. The name must be
     * {@code localeResolver} for the DispatcherServlet to use it.
     */
    @Bean
    LocaleResolver localeResolver() {
        CookieLocaleResolver cookie = new SupportedLocaleResolver("dbvault_lang");
        cookie.setCookieMaxAge(Duration.ofDays(365));
        cookie.setCookieHttpOnly(true);
        cookie.setCookieSameSite("Lax");
        cookie.setDefaultLocaleFunction(request -> supported(request.getLocale()));
        return cookie;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        LocaleChangeInterceptor change = new LocaleChangeInterceptor();
        change.setParamName("lang");
        change.setHttpMethods("GET");
        change.setIgnoreInvalidLocale(true);
        registry.addInterceptor(change);
    }

    /** The language itself when the console has it, otherwise English. */
    static Locale supported(Locale requested) {
        if (requested != null) {
            for (Locale language : LANGUAGES) {
                if (language.getLanguage().equals(requested.getLanguage())) {
                    return language;
                }
            }
        }
        return Locale.ENGLISH;
    }

    /** Narrows whatever a cookie or {@code ?lang=} says to a language the console has. */
    private static final class SupportedLocaleResolver extends CookieLocaleResolver {

        SupportedLocaleResolver(String cookieName) {
            super(cookieName);
        }

        @Override
        public Locale resolveLocale(HttpServletRequest request) {
            return supported(super.resolveLocale(request));
        }

        @Override
        public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
            super.setLocale(request, response, locale == null ? null : supported(locale));
        }
    }
}
