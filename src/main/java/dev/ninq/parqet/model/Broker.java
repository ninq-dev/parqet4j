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
 * The brokers and banks Parqet recognises as the origin of an activity.
 * <p>
 * Parqet adds entries to this list over time, so unknown identifiers deserialize to {@link #UNKNOWN} rather than failing; serializing
 * {@code UNKNOWN} throws.
 */
public enum Broker {

    /** 1822direkt. */
    DIREKT_1822("1822direkt"),
    /** bison. */
    BISON("bison"),
    /** bitpanda. */
    BITPANDA("bitpanda"),
    /** bsdex. */
    BSDEX("bsdex"),
    /** bondora. */
    BONDORA("bondora"),
    /** baader_bank. */
    BAADER_BANK("baader_bank"),
    /** cap_trader. */
    CAP_TRADER("cap_trader"),
    /** coinbase. */
    COINBASE("coinbase"),
    /** coinbase_pro. */
    COINBASE_PRO("coinbase_pro"),
    /** comdirect. */
    COMDIRECT("comdirect"),
    /** consors_bank. */
    CONSORS_BANK("consors_bank"),
    /** cortal_consors. */
    CORTAL_CONSORS("cortal_consors"),
    /** commerzbank. */
    COMMERZBANK("commerzbank"),
    /** crypto_com. */
    CRYPTO_COM("crypto_com"),
    /** dadat. */
    DADAT("dadat"),
    /** deutsche_bank. */
    DEUTSCHE_BANK("deutsche_bank"),
    /** degiro. */
    DEGIRO("degiro"),
    /** dkb. */
    DKB("dkb"),
    /** ebase. */
    EBASE("ebase"),
    /** erste_bank. */
    ERSTE_BANK("erste_bank"),
    /** extra_etf. */
    EXTRA_ETF("extra_etf"),
    /** ffb. */
    FFB("ffb"),
    /** finanzen_zero. */
    FINANZEN_ZERO("finanzen_zero"),
    /** finvesto. */
    FINVESTO("finvesto"),
    /** flatex. */
    FLATEX("flatex"),
    /** fondsdepot_bank. */
    FONDSDEPOT_BANK("fondsdepot_bank"),
    /** generic_csv. */
    GENERIC_CSV("generic_csv"),
    /** gratisbroker. */
    GRATISBROKER("gratisbroker"),
    /** hypo_vereinsbank. */
    HYPO_VEREINSBANK("hypo_vereinsbank"),
    /** ing. */
    ING("ing"),
    /** interactive_brokers. */
    INTERACTIVE_BROKERS("interactive_brokers"),
    /** just_trade. */
    JUST_TRADE("just_trade"),
    /** kraken. */
    KRAKEN("kraken"),
    /** lgt_bank. */
    LGT_BANK("lgt_bank"),
    /** onvista. */
    ONVISTA("onvista"),
    /** oskar. */
    OSKAR("oskar"),
    /** peaks. */
    PEAKS("peaks"),
    /** portfolio_performance. */
    PORTFOLIO_PERFORMANCE("portfolio_performance"),
    /** postbank. */
    POSTBANK("postbank"),
    /** quirion. */
    QUIRION("quirion"),
    /** saxo_bank. */
    SAXO_BANK("saxo_bank"),
    /** s_broker. */
    S_BROKER("s_broker"),
    /** scalable_capital. */
    SCALABLE_CAPITAL("scalable_capital"),
    /** smartbroker. */
    SMARTBROKER("smartbroker"),
    /** spk_vb. */
    SPK_VB("spk_vb"),
    /** smavesto. */
    SMAVESTO("smavesto"),
    /** smartbroker_plus. */
    SMARTBROKER_PLUS("smartbroker_plus"),
    /** sunrise. */
    SUNRISE("sunrise"),
    /** sutorbank. */
    SUTORBANK("sutorbank"),
    /** swissquote. */
    SWISSQUOTE("swissquote"),
    /** targobank. */
    TARGOBANK("targobank"),
    /** tomorrow. */
    TOMORROW("tomorrow"),
    /** trade_republic. */
    TRADE_REPUBLIC("trade_republic"),
    /** traders_place. */
    TRADERS_PLACE("traders_place"),
    /** trading212. */
    TRADING212("trading212"),
    /** union_investment. */
    UNION_INVESTMENT("union_investment"),
    /** vanguard. */
    VANGUARD("vanguard"),
    /** v_bank. */
    V_BANK("v_bank"),
    /** volksbank. */
    VOLKSBANK("volksbank"),
    /** yuh. */
    YUH("yuh"),

    /** A broker this client does not know yet. Read-only — serializing it throws. */
    UNKNOWN(null);

    private static final Map<String, Broker> BY_ID = Stream.of(values()).filter(b -> b.id != null).collect(Collectors.toMap(b -> b.id, Function.identity()));

    private final String id;

    Broker(String id) {
        this.id = id;
    }

    /**
     * Returns whether this client knows the broker, and can therefore send it back to the API.
     *
     * @return {@code false} only for {@link #UNKNOWN}
     */
    public boolean isKnown() {
        return id != null;
    }

    /**
     * Returns the wire representation of this broker.
     *
     * @return the broker identifier as the API spells it
     * @throws IllegalStateException if called on {@link #UNKNOWN}
     */
    public String id() {
        if (id == null) {
            throw new IllegalStateException("Broker.UNKNOWN has no wire representation and cannot be sent to the API");
        }
        return id;
    }

    /**
     * Maps a wire broker identifier to a constant.
     *
     * @param id the identifier as it appears in JSON
     * @return the matching constant, or {@link #UNKNOWN} if this client does not know the identifier
     */
    @JsonCreator
    public static Broker fromId(String id) {
        return BY_ID.getOrDefault(id, UNKNOWN);
    }
}
