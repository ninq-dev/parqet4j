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
package dev.ninq.parqet;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import dev.ninq.parqet.internal.Envelopes;
import dev.ninq.parqet.internal.ParqetJson;
import dev.ninq.parqet.model.Activity;
import dev.ninq.parqet.model.ActivityType;
import dev.ninq.parqet.model.AssetProduct;
import dev.ninq.parqet.model.AssetType;
import dev.ninq.parqet.model.Broker;
import dev.ninq.parqet.model.CommodityIdentifier;
import dev.ninq.parqet.model.CommodityUnit;
import dev.ninq.parqet.model.ConnectInfo;
import dev.ninq.parqet.model.Currency;
import dev.ninq.parqet.model.Holding;
import dev.ninq.parqet.model.NewActivity;
import dev.ninq.parqet.model.NewHolding;
import dev.ninq.parqet.model.PerformanceRequest;
import dev.ninq.parqet.model.Portfolio;
import dev.ninq.parqet.model.QuoteUpdate;
import dev.ninq.parqet.model.RelativeInterval;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Holds the client against the vendored OpenAPI document.
 * <p>
 * These are the tests that fail when Parqet changes the API: a new endpoint, a new property, or a new enum value all surface here rather
 * than as a runtime surprise in someone's integration. Refresh {@code src/test/resources/openapi/parqet-connect-0.1.0.json} from
 * <a href="https://developer.parqet.com/api-spec/current.json">the published spec</a> and let this class point at what needs updating.
 */
class SpecCoverageTest {

    private static final JsonNode SPEC = loadSpec();

    /** Every operation the spec declares, mapped to the client method that reaches it. */
    private static final Map<String, Reachable> OPERATIONS = Map.ofEntries(
            Map.entry("user_info", new Reachable(ParqetClient.class, "user")),
            Map.entry("portfolios_retrieve", new Reachable(Portfolios.class, "list")),
            Map.entry("portfolios_create", new Reachable(Portfolios.class, "create")),
            Map.entry("portfolios_holdings_retrieve", new Reachable(PortfolioResource.class, "holdings")),
            Map.entry("activities_retrieve", new Reachable(PortfolioResource.class, "activities")),
            Map.entry("activities_create", new Reachable(PortfolioResource.class, "createActivities")),
            Map.entry("portfolios_holdings_create_cash", new Reachable(PortfolioResource.class, "createHolding")),
            Map.entry("portfolios_holdings_create_commodity", new Reachable(PortfolioResource.class, "createHolding")),
            Map.entry("portfolios_holdings_create_custom", new Reachable(PortfolioResource.class, "createHolding")),
            Map.entry("portfolios_holdings_create_real_estate", new Reachable(PortfolioResource.class, "createHolding")),
            Map.entry("portfolios_holdings_create_insurance", new Reachable(PortfolioResource.class, "createHolding")),
            Map.entry("portfolios_holdings_create_p2p", new Reachable(PortfolioResource.class, "createHolding")),
            Map.entry("portfolios_holdings_create_user_managed_quotes", new Reachable(PortfolioResource.class, "pushQuotes")),
            Map.entry("performance", new Reachable(ParqetClient.class, "performance")));

    private record Reachable(Class<?> type, String method) {
    }

    @Test
    void coversEveryOperationTheSpecDeclares() {
        var declared = new LinkedHashSet<String>();
        SPEC.get("paths").forEach(path -> path.forEach(operation -> declared.add(operation.get("operationId").asText())));

        assertThat(declared)
                .as("the spec declares an operation this client does not implement, or no longer declares one it does")
                .containsExactlyInAnyOrderElementsOf(OPERATIONS.keySet());

        OPERATIONS.forEach((operationId, reachable) -> assertThat(Arrays.stream(reachable.type().getMethods()))
                .as("no public %s.%s() for operation %s", reachable.type().getSimpleName(), reachable.method(), operationId)
                .anyMatch(m -> m.getName().equals(reachable.method())));
    }

    @Test
    void bindsEveryPropertyOfTheReadModels() {
        assertBound("ConnectInfoDto_Output", ConnectInfo.class);
        assertBound("PortfolioListResponseDto_Output/properties/items", Portfolio.class);
        assertBound("HoldingsRetrieveResponseDto_Output/properties/items", Holding.class);
    }

    @Test
    void bindsEveryPropertyOfEveryReturnedActivityShape() {
        var variants = SPEC.at("/components/schemas/GetActivitiesDto_Output/properties/activities/items/oneOf");
        assertThat(variants).isNotEmpty();

        var bound = boundNames(Activity.class);
        variants.forEach(variant -> assertThat(bound)
                .as("Activity does not bind every property of the %s shape", variant.path("title").asText("?"))
                .containsAll(propertyNames(variant)));
    }

    @Test
    void bindsEveryPropertyOfEveryWritableActivityShape() {
        var variants = SPEC.at("/components/schemas/CreateActivityBodyDto/properties/activities/items/oneOf");
        var byDiscriminator = Map.<String, Class<?>> ofEntries(
                Map.entry("isin", NewActivity.Security.class),
                Map.entry("crypto_symbol", NewActivity.Crypto.class),
                Map.entry("commodity", NewActivity.Commodity.class),
                Map.entry("cash", NewActivity.Cash.class),
                Map.entry("custom_asset", NewActivity.CustomAsset.class),
                Map.entry("insurance", NewActivity.Insurance.class),
                Map.entry("p2p", NewActivity.P2p.class),
                Map.entry("real_estate", NewActivity.RealEstate.class));

        assertThat(variants).hasSize(byDiscriminator.size());
        variants.forEach(variant -> {
            var discriminator = variant.at("/properties/assetIdentifierType/const").asText();
            var model = byDiscriminator.get(discriminator);
            assertThat(model).as("no NewActivity variant for assetIdentifierType %s", discriminator).isNotNull();
            assertThat(boundNames(model))
                    .as("%s does not bind every property of the %s request shape", model.getSimpleName(), discriminator)
                    .containsAll(propertyNames(variant));
        });
    }

    @Test
    void bindsEveryPropertyOfTheWriteModels() {
        assertBound("PortfolioCreationBodyDto", Envelopes.PortfolioCreation.class);
        assertBound("CashHoldingCreationBodyDto", NewHolding.Cash.class);
        assertBound("CommodityHoldingCreationBodyDto", NewHolding.Commodity.class);
        assertBound("CustomHoldingCreationBodyDto", NewHolding.Custom.class);
        assertBound("RealEstateHoldingCreationBodyDto", NewHolding.RealEstate.class);
        assertBound("InsuranceHoldingCreationBodyDto", NewHolding.Insurance.class);
        assertBound("P2PHoldingCreationBodyDto", NewHolding.P2p.class);
        assertBound("PortfolioPerformanceBodyDto", PerformanceRequest.class);
        // `holdingId` is the deprecated spelling the spec still lists; `identifier` supersedes it.
        assertBound("CustomHoldingCreateUserManagedQuotesBodyDto", QuoteUpdate.class, Set.of("holdingId"));
    }

    @Test
    void keepsTheEnumsInStepWithTheSpec() {
        assertEnum(Currency.class, "EUR", Currency::code, currency -> currency != Currency.UNKNOWN);
        assertEnum(Broker.class, "trade_republic", Broker::id, broker -> broker != Broker.UNKNOWN);
        assertEnum(ActivityType.class, "fees_taxes", ActivityType::id, ignored -> true);
        assertEnum(AssetType.class, "real_estate", AssetType::id, ignored -> true);
        assertEnum(AssetProduct.class, "material_asset", AssetProduct::id, ignored -> true);
        assertEnum(CommodityIdentifier.class, "palladium", CommodityIdentifier::id, ignored -> true);
        assertEnum(CommodityUnit.class, "oz.tr.", CommodityUnit::id, ignored -> true);
        assertEnum(RelativeInterval.class, "mtd", RelativeInterval::id, ignored -> true);
    }

    private static void assertBound(String pointer, Class<?> model) {
        assertBound(pointer, model, Set.of());
    }

    private static void assertBound(String pointer, Class<?> model, Set<String> exempt) {
        var schema = SPEC.at("/components/schemas/" + pointer);
        assertThat(schema.isMissingNode()).as("the spec no longer declares %s", pointer).isFalse();

        var expected = new LinkedHashSet<>(propertyNames(schema));
        expected.removeAll(exempt);
        assertThat(boundNames(model)).as("%s does not bind every property of %s", model.getSimpleName(), pointer).containsAll(expected);
    }

    /** The JSON names a record binds, taking {@code @JsonProperty} renames into account. */
    private static Set<String> boundNames(Class<?> model) {
        return Arrays.stream(model.getRecordComponents())
                .map(component -> renamedTo(model, component.getName()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * {@code @JsonProperty} does not target {@code RECORD_COMPONENT}, so it lands on the backing field rather than on the component. Look there
     * before falling back to the component's own name.
     */
    private static String renamedTo(Class<?> model, String component) {
        try {
            var renamed = model.getDeclaredField(component).getAnnotation(JsonProperty.class);
            return renamed == null || renamed.value().isEmpty() ? component : renamed.value();
        } catch (NoSuchFieldException e) {
            return component;
        }
    }

    private static List<String> propertyNames(JsonNode schema) {
        var names = new ArrayList<String>();
        schema.path("properties").fieldNames().forEachRemaining(names::add);
        // The discriminator is written by @JsonTypeInfo, not by a record component.
        names.remove("assetIdentifierType");
        return names;
    }

    private static <E extends Enum<E>> void assertEnum(
            Class<E> type, String sentinel, Function<E, String> wireValue, java.util.function.Predicate<E> mapped) {
        var declared = findEnumContaining(sentinel);
        assertThat(declared).as("the spec no longer declares an enum containing %s", sentinel).isNotEmpty();

        var known = Stream.of(type.getEnumConstants()).filter(mapped).map(wireValue).collect(Collectors.toSet());
        assertThat(known).as("%s is out of step with the spec", type.getSimpleName()).containsExactlyInAnyOrderElementsOf(declared);
    }

    /** Finds the spec enum that contains a value unique to it, so a rename in the spec is caught too. */
    private static Set<String> findEnumContaining(String sentinel) {
        var found = new LinkedHashSet<String>();
        walk(SPEC, node -> {
            var values = node.path("enum");
            if (!values.isArray()) {
                return;
            }
            var texts = new LinkedHashSet<String>();
            values.forEach(value -> texts.add(value.asText()));
            if (texts.contains(sentinel) && found.isEmpty()) {
                found.addAll(texts);
            }
        });
        return found;
    }

    private static void walk(JsonNode node, java.util.function.Consumer<JsonNode> visitor) {
        if (node.isObject()) {
            visitor.accept(node);
        }
        node.forEach(child -> walk(child, visitor));
    }

    private static JsonNode loadSpec() {
        try (var in = SpecCoverageTest.class.getResourceAsStream("/openapi/parqet-connect-0.1.0.json")) {
            return ParqetJson.mapper().readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException("The vendored OpenAPI document is missing", e);
        }
    }
}
