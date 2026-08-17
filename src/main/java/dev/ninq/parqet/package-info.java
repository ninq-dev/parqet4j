/**
 * The client itself: {@link dev.ninq.parqet.ParqetClient} and the resource handles it hands out.
 * <p>
 * {@link dev.ninq.parqet.ParqetClient} is the entry point. {@link dev.ninq.parqet.Portfolios} covers the portfolio collection,
 * {@link dev.ninq.parqet.PortfolioResource} everything below one portfolio, and {@link dev.ninq.parqet.ActivityQuery} and
 * {@link dev.ninq.parqet.RetryPolicy} tune what is fetched and what happens when a call fails.
 */
package dev.ninq.parqet;
