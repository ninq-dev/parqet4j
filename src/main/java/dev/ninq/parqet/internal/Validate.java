/*
 * Copyright 2026 the parqet4j authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.ninq.parqet.internal;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Argument checks shared by the model records, mirroring the constraints the OpenAPI spec declares.
 * <p>
 * Validating in the canonical constructors means an illegal request fails locally, before any wire I/O, with a message that names the
 * offending field.
 */
public final class Validate {

    /** {@code ^[A-Z]{2}[A-Z0-9]{9}\d{1}$} — two letters, nine alphanumerics, one check digit. */
    private static final Pattern ISIN = Pattern.compile("^[A-Z]{2}[A-Z0-9]{9}\\d$");

    /** {@code ^[A-Za-z0-9\-_]+$}, at most 255 characters. */
    private static final Pattern EXTERNAL_ID = Pattern.compile("^[A-Za-z0-9\\-_]+$");

    /** {@code ^\w+::\w+$} — a portfolio id and a sub-account id joined by a double colon. */
    private static final Pattern SUB_ACCOUNT = Pattern.compile("^\\w+::\\w+$");

    private static final int MAX_NAME_LENGTH = 80;
    private static final int MAX_EXTERNAL_ID_LENGTH = 255;

    private Validate() {
    }

    /**
     * Requires a non-{@code null}, non-blank string.
     *
     * @param value the value to check
     * @param field the field name to use in the failure message
     * @return {@code value}
     * @throws NullPointerException if {@code value} is {@code null}
     * @throws IllegalArgumentException if {@code value} is blank
     */
    public static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    /**
     * Requires a display name of 1 to 80 characters, the range the API accepts for portfolios and holdings.
     *
     * @param name the name to check
     * @return {@code name}
     * @throws NullPointerException if {@code name} is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank or longer than 80 characters
     */
    public static String requireName(String name) {
        requireText(name, "name");
        if (name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("name must be at most " + MAX_NAME_LENGTH + " characters, was " + name.length());
        }
        return name;
    }

    /**
     * Checks an optional external identifier against {@code ^[A-Za-z0-9\-_]+$} and the 255-character limit.
     *
     * @param externalId the identifier to check, may be {@code null}
     * @return {@code externalId}
     * @throws IllegalArgumentException if a non-{@code null} identifier violates the pattern or length
     */
    public static String checkExternalId(String externalId) {
        if (externalId == null) {
            return null;
        }
        if (externalId.length() > MAX_EXTERNAL_ID_LENGTH) {
            throw new IllegalArgumentException(
                    "externalId must be at most " + MAX_EXTERNAL_ID_LENGTH + " characters, was " + externalId.length());
        }
        if (!EXTERNAL_ID.matcher(externalId).matches()) {
            throw new IllegalArgumentException("externalId must match ^[A-Za-z0-9\\-_]+$, was: " + externalId);
        }
        return externalId;
    }

    /**
     * Requires a syntactically valid ISIN.
     *
     * @param isin the ISIN to check
     * @return {@code isin}
     * @throws NullPointerException if {@code isin} is {@code null}
     * @throws IllegalArgumentException if {@code isin} does not match {@code ^[A-Z]{2}[A-Z0-9]{9}\d$}
     */
    public static String requireIsin(String isin) {
        Objects.requireNonNull(isin, "isin must not be null");
        if (!ISIN.matcher(isin).matches()) {
            throw new IllegalArgumentException("isin must match ^[A-Z]{2}[A-Z0-9]{9}\\d$, was: " + isin);
        }
        return isin;
    }

    /**
     * Requires a sub-account identifier of the form {@code portfolioId::subAccountId}.
     *
     * @param subAccount the identifier to check
     * @return {@code subAccount}
     * @throws NullPointerException if {@code subAccount} is {@code null}
     * @throws IllegalArgumentException if {@code subAccount} does not match {@code ^\w+::\w+$}
     */
    public static String requireSubAccountId(String subAccount) {
        Objects.requireNonNull(subAccount, "subAccount must not be null");
        if (!SUB_ACCOUNT.matcher(subAccount).matches()) {
            throw new IllegalArgumentException("subAccount must match ^\\w+::\\w+$, was: " + subAccount);
        }
        return subAccount;
    }

    /**
     * Requires a non-{@code null} amount.
     *
     * @param value the amount to check
     * @param field the field name to use in the failure message
     * @return {@code value}
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public static BigDecimal requireAmount(BigDecimal value, String field) {
        return Objects.requireNonNull(value, field + " must not be null");
    }

    /**
     * Requires an amount strictly greater than zero.
     *
     * @param value the amount to check
     * @param field the field name to use in the failure message
     * @return {@code value}
     * @throws NullPointerException if {@code value} is {@code null}
     * @throws IllegalArgumentException if {@code value} is zero or negative
     */
    public static BigDecimal requirePositive(BigDecimal value, String field) {
        requireAmount(value, field);
        if (value.signum() <= 0) {
            throw new IllegalArgumentException(field + " must be greater than zero, was " + value.toPlainString());
        }
        return value;
    }

    /**
     * Requires an integer within an inclusive range.
     *
     * @param value the value to check
     * @param min the smallest allowed value
     * @param max the largest allowed value
     * @param field the field name to use in the failure message
     * @return {@code value}
     * @throws IllegalArgumentException if {@code value} is outside the range
     */
    public static int requireInRange(int value, int min, int max, String field) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(field + " must be between " + min + " and " + max + ", was " + value);
        }
        return value;
    }

    /**
     * Returns an unmodifiable copy of a list, mapping {@code null} to the empty list.
     *
     * @param <T> the element type
     * @param values the list to copy, may be {@code null}
     * @return an unmodifiable copy
     * @throws NullPointerException if any element is {@code null}
     */
    public static <T> List<T> copyOf(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    /**
     * Returns an unmodifiable copy of a non-empty list.
     *
     * @param <T> the element type
     * @param values the list to copy
     * @param field the field name to use in the failure message
     * @return an unmodifiable copy
     * @throws NullPointerException if {@code values} or any element is {@code null}
     * @throws IllegalArgumentException if {@code values} is empty
     */
    public static <T> List<T> requireNonEmpty(List<T> values, String field) {
        Objects.requireNonNull(values, field + " must not be null");
        if (values.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be empty");
        }
        return List.copyOf(values);
    }
}
