/**
 * The unchecked exception hierarchy, rooted at {@link dev.ninq.parqet.error.ParqetException}.
 * <p>
 * Three branches separate the causes: {@link dev.ninq.parqet.error.ParqetApiException} for a non-2xx answer,
 * {@link dev.ninq.parqet.error.ParqetTransportException} when no answer arrived, and {@link dev.ninq.parqet.error.ParqetProtocolException}
 * when one arrived but could not be understood.
 */
package dev.ninq.parqet.error;
