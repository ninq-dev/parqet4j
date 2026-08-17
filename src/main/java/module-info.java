/**
 * A lightweight Java client for the Parqet Connect API — typed models over portfolios, holdings and activities, plus the OAuth 2.0
 * authorization-code flow with PKCE that the API authenticates with.
 * <p>
 * Start at {@link dev.ninq.parqet.ParqetClient}.
 */
module dev.ninq.parqet {
    // Transitive: ParqetClient.Builder.httpClient(HttpClient) exposes java.net.http in the API, and
    // the model records carry Jackson annotations that a consumer may need to read.
    requires transitive java.net.http;
    requires transitive com.fasterxml.jackson.annotation;
    requires com.fasterxml.jackson.databind;
    requires org.slf4j;

    exports dev.ninq.parqet;
    exports dev.ninq.parqet.auth;
    exports dev.ninq.parqet.model;
    exports dev.ninq.parqet.error;

    // Jackson binds the models reflectively. Records expose their components through public
    // accessors, but the nested wire records used to flatten the API's `inInterval` envelopes are
    // not public, so the packages have to be open rather than merely exported.
    opens dev.ninq.parqet.model to com.fasterxml.jackson.databind;
    opens dev.ninq.parqet.internal to com.fasterxml.jackson.databind;
    opens dev.ninq.parqet.auth to com.fasterxml.jackson.databind;
}
