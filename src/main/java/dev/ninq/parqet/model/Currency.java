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
package dev.ninq.parqet.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The currencies the Parqet Connect API accepts and reports.
 * <p>
 * Mostly ISO 4217, plus {@link #GBX} (pence sterling), which is not. Unknown codes deserialize to {@link #UNKNOWN} so that a currency added
 * on the server does not break existing clients; serializing {@code UNKNOWN} throws.
 */
public enum Currency {

    /** AED. */
    AED("AED"),
    /** ARS. */
    ARS("ARS"),
    /** AUD. */
    AUD("AUD"),
    /** BHD. */
    BHD("BHD"),
    /** BRL. */
    BRL("BRL"),
    /** BWP. */
    BWP("BWP"),
    /** CAD. */
    CAD("CAD"),
    /** CHF. */
    CHF("CHF"),
    /** CLP. */
    CLP("CLP"),
    /** CNY. */
    CNY("CNY"),
    /** CZK. */
    CZK("CZK"),
    /** DKK. */
    DKK("DKK"),
    /** EUR. */
    EUR("EUR"),
    /** GBP. */
    GBP("GBP"),
    /** GBX. */
    GBX("GBX"),
    /** GEL. */
    GEL("GEL"),
    /** HKD. */
    HKD("HKD"),
    /** HUF. */
    HUF("HUF"),
    /** IDR. */
    IDR("IDR"),
    /** ILS. */
    ILS("ILS"),
    /** INR. */
    INR("INR"),
    /** ISK. */
    ISK("ISK"),
    /** JPY. */
    JPY("JPY"),
    /** KRW. */
    KRW("KRW"),
    /** KZT. */
    KZT("KZT"),
    /** MAD. */
    MAD("MAD"),
    /** MXN. */
    MXN("MXN"),
    /** MYR. */
    MYR("MYR"),
    /** NOK. */
    NOK("NOK"),
    /** NZD. */
    NZD("NZD"),
    /** PEN. */
    PEN("PEN"),
    /** PHP. */
    PHP("PHP"),
    /** PLN. */
    PLN("PLN"),
    /** QAR. */
    QAR("QAR"),
    /** RON. */
    RON("RON"),
    /** RSD. */
    RSD("RSD"),
    /** RUB. */
    RUB("RUB"),
    /** SAR. */
    SAR("SAR"),
    /** SEK. */
    SEK("SEK"),
    /** SGD. */
    SGD("SGD"),
    /** THB. */
    THB("THB"),
    /** TRY. */
    TRY("TRY"),
    /** TWD. */
    TWD("TWD"),
    /** USD. */
    USD("USD"),
    /** VND. */
    VND("VND"),
    /** ZAR. */
    ZAR("ZAR"),
    /** ZMW. */
    ZMW("ZMW"),

    /** A currency this client does not know yet. Read-only — serializing it throws. */
    UNKNOWN(null);

    private static final Map<String, Currency> BY_CODE = Stream.of(values()).filter(c -> c.code != null)
            .collect(Collectors.toMap(c -> c.code, Function.identity()));

    private final String code;

    Currency(String code) {
        this.code = code;
    }

    /**
     * Returns whether this client knows the currency, and can therefore send it back to the API.
     *
     * @return {@code false} only for {@link #UNKNOWN}
     */
    public boolean isKnown() {
        return code != null;
    }

    /**
     * Returns the wire representation of this currency.
     *
     * @return the currency code as the API spells it
     * @throws IllegalStateException if called on {@link #UNKNOWN}
     */
    public String code() {
        if (code == null) {
            throw new IllegalStateException("Currency.UNKNOWN has no wire representation and cannot be sent to the API");
        }
        return code;
    }

    /**
     * Maps a wire currency code to a constant.
     *
     * @param code the code as it appears in JSON
     * @return the matching constant, or {@link #UNKNOWN} if this client does not know the code
     */
    @JsonCreator
    public static Currency fromCode(String code) {
        return BY_CODE.getOrDefault(code, UNKNOWN);
    }
}
