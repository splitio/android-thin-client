package io.split.client.thin.e2e

/**
 * Inline JSON constants for E2E test fixtures.
 *
 * All payloads are minimal but structurally valid responses that the thin client
 * can parse without errors.
 */
object E2EFixtures {

    // -------------------------------------------------------------------------
    // JWT used in AUTH_PUSH_ENABLED — declared first so it can be referenced below
    // -------------------------------------------------------------------------

    /**
     * A pre-built JWT whose decoded payload grants `subscribe` on channel
     * [STREAMING_CHANNEL].
     *
     * Header: `{"alg":"HS256","typ":"JWT"}`
     * Payload:
     * ```json
     * {
     *   "x-ably-capability": "{\"evaluations_test\":[\"subscribe\"]}",
     *   "exp": 4102444800
     * }
     * ```
     *
     * The signature is a dummy value — the thin client does not verify JWT signatures.
     */
    const val STREAMING_JWT: String =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9" +
        ".eyJ4LWFibHktY2FwYWJpbGl0eSI6IntcImV2YWx1YXRpb25zX3Rlc3RcIjpbXCJzdWJzY3JpYmVcIl19IiwiZXhwIjo0MTAyNDQ0ODAwfQ" +
        ".dummysignature"

    // -------------------------------------------------------------------------
    // Ably channel name
    // -------------------------------------------------------------------------

    /**
     * Ably channel name used in [STREAMING_JWT] capability and in SSE events.
     *
     * The thin client extracts channel names from the JWT `x-ably-capability` field.
     * Using a fixed name here lets [MockSplitServer] serve the matching SSE event
     * without parsing the JWT at test time.
     */
    const val STREAMING_CHANNEL: String = "evaluations_test"

    // -------------------------------------------------------------------------
    // Auth responses
    // -------------------------------------------------------------------------

    /**
     * Auth response with push enabled.
     *
     * Token expiry is set far in the future (year 2099) to avoid JWT-refresh flows
     * during normal E2E test execution.
     */
    const val AUTH_PUSH_ENABLED: String =
        """{"pushEnabled":true,"connDelay":0,"token":"$STREAMING_JWT"}"""

    /**
     * Auth response with push disabled — SDK falls back to polling.
     */
    const val AUTH_PUSH_DISABLED: String = """{"pushEnabled":false}"""

    // -------------------------------------------------------------------------
    // Evaluations responses
    // -------------------------------------------------------------------------

    /**
     * Initial evaluations payload: flag_a=on, flag_b=off.
     */
    const val EVALUATIONS_RESPONSE_1: String = """{"till":1000,"since":-1,"evaluations":[""" +
        """{"featureName":"flag_a","treatment":"on","sets":[],"config":null},""" +
        """{"featureName":"flag_b","treatment":"off","sets":[],"config":null}]}"""

    /**
     * Updated evaluations payload: flag_a=off, flag_b=on.
     */
    const val EVALUATIONS_RESPONSE_2: String = """{"till":2000,"since":1000,"evaluations":[""" +
        """{"featureName":"flag_a","treatment":"off","sets":[],"config":null},""" +
        """{"featureName":"flag_b","treatment":"on","sets":[],"config":null}]}"""

    /**
     * Second-poll update for user_b: flag_b flips from "on" to "off".
     *
     * Used in the multi-client event-isolation test to trigger an [SdkUpdateMetadata]
     * event on client2 while leaving client1 (user_a) unchanged.
     */
    const val EVALUATIONS_RESPONSE_2_UPDATED: String = """{"till":3000,"since":2000,"evaluations":[""" +
        """{"featureName":"flag_a","treatment":"off","sets":[],"config":null},""" +
        """{"featureName":"flag_b","treatment":"off","sets":[],"config":null}]}"""

    // -------------------------------------------------------------------------
    // SSE / streaming events
    // -------------------------------------------------------------------------

    /**
     * SSE `data:` payload that triggers an evaluations re-fetch.
     *
     * Inner type is `EVALUATION_UPDATE` (per `ThinNotificationType`).
     * The outer envelope follows the Ably shape parsed by `ThinNotificationParser`:
     * `channel`, `data` (JSON-encoded inner payload), `timestamp`.
     */
    const val SSE_EVALUATION_UPDATE: String =
        """{"channel":"$STREAMING_CHANNEL",""" +
        """"data":"{\"type\":\"EVALUATION_UPDATE\",\"changeNumber\":2000}",""" +
        """"timestamp":1000000}"""
}
