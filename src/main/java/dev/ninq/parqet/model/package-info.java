/**
 * The typed models: one record per shape the Parqet Connect API reads or writes.
 * <p>
 * Read models — {@link dev.ninq.parqet.model.Portfolio}, {@link dev.ninq.parqet.model.Holding}, {@link dev.ninq.parqet.model.Activity},
 * {@link dev.ninq.parqet.model.PortfolioPerformance} — are produced by the client. Write models —
 * {@link dev.ninq.parqet.model.NewActivity}, {@link dev.ninq.parqet.model.NewHolding}, {@link dev.ninq.parqet.model.QuoteUpdate},
 * {@link dev.ninq.parqet.model.PerformanceRequest} — are built by the caller, and validate their arguments on construction so a malformed
 * request fails before any I/O.
 * <p>
 * Where the API discriminates between shapes, this package uses a sealed interface so a {@code switch} over the variants is exhaustive.
 * {@link dev.ninq.parqet.model.Currency} and {@link dev.ninq.parqet.model.Broker} are deliberately open: a value this client does not know
 * reads back as {@code UNKNOWN} instead of failing, and is refused on the way out.
 */
package dev.ninq.parqet.model;
