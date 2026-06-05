package io.github.ofz.autolog.provider;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Default implementation of {@link SensitiveDataFilter} that matches parameter
 * names against a built-in set of common sensitive keywords.
 *
 * <p>Built-in keywords (case-insensitive contains match):
 * {@code password}, {@code passwd}, {@code pwd}, {@code secret}, {@code token},
 * {@code key}, {@code credential}, {@code apikey}, {@code apiKey},
 * {@code accessToken}, {@code accessKey}, {@code privateKey}, {@code secretKey}.
 *
 * <p>Additional keywords can be provided via the constructor (sourced from
 * {@code auto.log.sensitive-keywords}), or the entire filter can be replaced
 * by registering a custom {@link SensitiveDataFilter} bean.
 *
 * @author ofz
 */
public class DefaultSensitiveDataFilter implements SensitiveDataFilter {

    private static final Set<String> BUILT_IN_KEYWORDS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "password", "passwd", "pwd",
                    "secret",
                    "token",
                    "key",
                    "credential",
                    "apikey", "api_key",
                    "accesstoken", "access_token",
                    "accesskey", "access_key",
                    "privatekey", "private_key",
                    "secretkey", "secret_key"
            ))
    );

    private final Set<String> keywords;

    /**
     * Creates a filter with only the built-in keyword set.
     */
    public DefaultSensitiveDataFilter() {
        this.keywords = BUILT_IN_KEYWORDS;
    }

    /**
     * Creates a filter that combines built-in keywords with the given
     * additional set. Additional keywords are normalised to lower case.
     *
     * @param additionalKeywords extra keywords to add (may be null or empty)
     */
    public DefaultSensitiveDataFilter(Set<String> additionalKeywords) {
        if (additionalKeywords == null || additionalKeywords.isEmpty()) {
            this.keywords = BUILT_IN_KEYWORDS;
        } else {
            Set<String> combined = new HashSet<>(BUILT_IN_KEYWORDS);
            for (String kw : additionalKeywords) {
                if (kw != null && !kw.trim().isEmpty()) {
                    combined.add(kw.trim().toLowerCase());
                }
            }
            this.keywords = Collections.unmodifiableSet(combined);
        }
    }

    @Override
    public boolean isSensitive(String paramName, Object paramValue, Method method) {
        if (paramName == null || paramName.isEmpty()) {
            return false;
        }
        // Normalise: lower-case and strip common separators so that
        // "user_password", "userPassword", "User-Password" all match.
        String normalised = paramName.toLowerCase()
                .replace("_", "")
                .replace("-", "");
        for (String keyword : keywords) {
            if (normalised.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /** Returns an unmodifiable view of the active keyword set (for diagnostics). */
    public Set<String> getKeywords() {
        return keywords;
    }
}
