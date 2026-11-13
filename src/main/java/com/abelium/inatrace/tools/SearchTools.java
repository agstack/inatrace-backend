package com.abelium.inatrace.tools;

import org.torpedoquery.jakarta.jpa.Function;
import org.torpedoquery.jakarta.jpa.TorpedoFunction;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Search helpers that keep the current MySQL behaviour (case- and
 * accent-insensitive search) on both supported databases.
 *
 * <p>The column side is normalized by the {@code search_normalize} SQL function
 * registered in each dialect: {@code lower(x)} on MySQL (whose collation already
 * ignores accents) and {@code lower(unaccent(x))} on PostgreSQL. The search term
 * is normalized in Java with the same rules before it reaches the query.
 */
public final class SearchTools {

    private SearchTools() {
    }

    /** Wraps a string field in the vendor-specific {@code search_normalize} function. */
    public static Function<String> normalized(String field) {
        return TorpedoFunction.function("search_normalize", String.class, field);
    }

    /** Lower-cases and strips diacritics from a search term. */
    public static String normalize(String term) {
        if (term == null) {
            return null;
        }
        return Normalizer.normalize(term, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
    }
}
