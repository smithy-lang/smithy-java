/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.io.uri;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Utility methods for extracting components from URI strings.
 */
public final class UriUtils {

    private static final Pattern REGEX_STRING_OF_SLASHES = Pattern.compile("/+");

    private UriUtils() {}

    /**
     * Extracts the query string from a URI.
     *
     * @param uri URI to extract the query string from.
     * @return the query string without the leading '?', or null if no query string is present.
     */
    public static String getQuery(String uri) {
        Objects.requireNonNull(uri);
        int questionMark = uri.indexOf('?');
        return questionMark < 0 ? null : uri.substring(questionMark + 1);
    }

    /**
     * Extracts the path from a URI, collapsing consecutive slashes.
     *
     * <p>Equivalent to calling {@code getPath(uri, false)}.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code getPath("/foo/bar")}  -> {@code "foo/bar"}</li>
     *   <li>{@code getPath("/foo/bar/")} -> {@code "foo/bar"}</li>
     *   <li>{@code getPath("///a///b")}  -> {@code "a/b"}</li>
     * </ul>
     *
     * @param uri URI to extract the path from.
     * @return the extracted path with consecutive slashes collapsed.
     */
    public static String getPath(String uri) {
        return getPath(uri, false);
    }

    /**
     * Extracts the path from a URI, stripping leading and trailing slashes and optionally collapsing
     * consecutive slashes into a single slash.
     *
     * <p>The query string, if present, is removed before extracting the path.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code getPath("/foo/bar?q=1", false)}    -> {@code "foo/bar"}</li>
     *   <li>{@code getPath("///foo///bar///", false)} -> {@code "foo/bar"}</li>
     *   <li>{@code getPath("///foo///bar///", true)}  -> {@code "foo///bar"}</li>
     * </ul>
     *
     * @param uri URI to extract the path from.
     * @param allowEmptyPathSegments if true, consecutive slashes are preserved; if false, they are collapsed.
     * @return the extracted path.
     */
    public static String getPath(String uri, boolean allowEmptyPathSegments) {
        uri = getRawPath(uri);
        if (allowEmptyPathSegments) {
            return uri;
        }
        return REGEX_STRING_OF_SLASHES.matcher(uri).replaceAll("/");
    }

    /**
     * Extracts the raw path from a URI, stripping leading and trailing slashes but preserving consecutive slashes.
     *
     * <p>The query string, if present, is removed before extracting the path.
     *
     * @param uri URI to extract the path from.
     * @return the raw path.
     */
    public static String getRawPath(String uri) {
        Objects.requireNonNull(uri);

        int i = 0;
        // Remove leading slashes
        while (i < uri.length() - 1 && uri.charAt(i) == '/') {
            i++;
        }
        int j = uri.indexOf('?');
        if (j < 0) {
            j = uri.length();
        }
        // Remove trailing slashes
        while (j > i && uri.charAt(j - 1) == '/') {
            j--;
        }
        return uri.substring(i, j);
    }
}
