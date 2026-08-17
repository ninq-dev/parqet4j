/**
 * OAuth 2.0 for Parqet Connect: the authorization-code flow with PKCE, and the token plumbing the client needs.
 * <p>
 * {@link dev.ninq.parqet.auth.ParqetOAuth} builds the authorization URI and talks to the token endpoint;
 * {@link dev.ninq.parqet.auth.TokenProvider} is what {@link dev.ninq.parqet.ParqetClient} asks for a bearer token before each call. An
 * application that already manages OAuth elsewhere can ignore everything here except {@link dev.ninq.parqet.auth.TokenProvider#of(String)}.
 */
package dev.ninq.parqet.auth;
