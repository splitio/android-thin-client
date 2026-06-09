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
     * Auth response with push enabled (v3 format).
     *
     * Token expiry is set far in the future (year 2099) to avoid JWT-refresh flows
     * during normal E2E test execution.
     */
    const val AUTH_PUSH_ENABLED: String =
        """{"token":"$STREAMING_JWT","config":{"streaming":{"enabled":true,"delay":0}}}"""

    /**
     * Auth response with push disabled — SDK falls back to polling (v3 format).
     *
     * Uses the same long-lived JWT as [AUTH_PUSH_ENABLED] so polling tests do not
     * exercise credential expiry unless they explicitly set that up.
     */
    const val AUTH_PUSH_DISABLED: String =
        """{"token":"$STREAMING_JWT","config":{"streaming":{"enabled":false}}}"""

    // -------------------------------------------------------------------------
    // Evaluations responses
    // -------------------------------------------------------------------------

    /**
     * Initial evaluations payload: flag_a=on, flag_b=off.
     */
    const val EVALUATIONS_RESPONSE_1: String = """{"till":1000,"since":1000,"evaluations":[""" +
        """{"flag":"flag_a","treatment":"on","sets":[],"config":null},""" +
        """{"flag":"flag_b","treatment":"off","sets":[],"config":null}]}"""

    /**
     * Updated evaluations payload: flag_a=off, flag_b=on.
     */
    const val EVALUATIONS_RESPONSE_2: String = """{"till":2000,"since":2000,"evaluations":[""" +
        """{"flag":"flag_a","treatment":"off","sets":[],"config":null},""" +
        """{"flag":"flag_b","treatment":"on","sets":[],"config":null}]}"""

    /**
     * Second-poll update for user_b: flag_b flips from "on" to "off".
     *
     * Used in the multi-client event-isolation test to trigger an [SdkUpdateMetadata]
     * event on client2 while leaving client1 (user_a) unchanged.
     */
    const val EVALUATIONS_RESPONSE_2_UPDATED: String = """{"till":3000,"since":3000,"evaluations":[""" +
        """{"flag":"flag_a","treatment":"off","sets":[],"config":null},""" +
        """{"flag":"flag_b","treatment":"off","sets":[],"config":null}]}"""

    /**
     * Evaluations payload without config fields — for use with configsEnabled=false.
     */
    const val EVALUATIONS_WITHOUT_CONFIG: String = """{"till":1000,"since":1000,"evaluations":[""" +
        """{"flag":"my_feature","treatment":"on","sets":[]}]}"""

    /**
     * Evaluations payload with config field — for use with configsEnabled=true.
     */
    const val EVALUATIONS_WITH_CONFIG: String = """{"till":2000,"since":2000,"evaluations":[""" +
        """{"flag":"my_feature","treatment":"on","sets":[],"config":"{\"color\":\"blue\"}"}]}"""

    // -------------------------------------------------------------------------
    // SSE / streaming events
    // -------------------------------------------------------------------------

    /**
     * SSE `data:` payload that triggers an evaluations re-fetch.
     *
     * Inner type is `EVALUATIONS_UPDATE` (per `ThinNotificationType`).
     * The outer envelope follows the Ably shape parsed by `ThinNotificationParser`:
     * `channel`, `data` (JSON-encoded inner payload), `timestamp`.
     */
    const val SSE_EVALUATION_UPDATE: String =
        """{"channel":"$STREAMING_CHANNEL",""" +
        """"data":"{\"type\":\"EVALUATIONS_UPDATE\",\"changeNumber\":2000}",""" +
        """"timestamp":1000000}"""

    /**
     * A bare Ably `event: error` SSE frame signalling an expired token (code 40142 / HTTP 401).
     *
     * This is wrapped in the envelope (no `channel`/`data`/`timestamp`); it is the raw
     * Ably error frame. The thin client
     * detects it by the `event: error` line, invalidates the token, and reconnects.
     */
    const val SSE_ERROR_TOKEN_EXPIRED: String =
        "event: error\n" +
        """data: {"code":40142,"statusCode":401,"message":"Token expired","href":"https://x/error/40142"}""" +
        "\n\n"

    // -------------------------------------------------------------------------
    // SSE control notifications (pause / resume)
    // -------------------------------------------------------------------------

    /**
     * Ably control channel name. The thin client routes control notifications by their inner
     * `type`/`controlType`, not by channel, so any channel name works here.
     */
    const val CONTROL_CHANNEL: String = "control_pri"

    /**
     * SSE `data:` payload carrying a `STREAMING_PAUSED` control notification. The SDK keeps the
     * socket open and falls back to polling. [timestamp] must increase across notifications for
     * the SDK's stale-control guard to accept it.
     */
    fun sseControlPaused(timestamp: Long): String =
        """{"channel":"$CONTROL_CHANNEL",""" +
        """"data":"{\"type\":\"CONTROL\",\"controlType\":\"STREAMING_PAUSED\"}",""" +
        """"timestamp":$timestamp}"""

    /**
     * SSE `data:` payload carrying a `STREAMING_RESUMED` control notification. The SDK stops
     * polling and resumes processing pushes over the live socket.
     */
    fun sseControlResumed(timestamp: Long): String =
        """{"channel":"$CONTROL_CHANNEL",""" +
        """"data":"{\"type\":\"CONTROL\",\"controlType\":\"STREAMING_RESUMED\"}",""" +
        """"timestamp":$timestamp}"""

    /**
     * Second SSE EVALUATION_UPDATE with a distinct changeNumber, used to verify push processing
     * resumes over the same socket after a control resume.
     */
    const val SSE_EVALUATION_UPDATE_2: String =
        """{"channel":"$STREAMING_CHANNEL",""" +
        """"data":"{\"type\":\"EVALUATIONS_UPDATE\",\"changeNumber\":3000}",""" +
        """"timestamp":2000000}"""

    /**
     * Stale evaluations response — till=1000 matches the initial state, not the SSE changeNumber.
     * Used to simulate a misbehaving CDN that serves cached data.
     */
    const val EVALUATIONS_STALE: String = """{"till":1000,"since":1000,"evaluations":[""" +
        """{"flag":"flag_a","treatment":"on","sets":[],"config":null}]}"""

    // -------------------------------------------------------------------------
    // Delayed-fetch SSE fixture (SyncDelayCalculator / hashing params)
    // -------------------------------------------------------------------------

    /** Interval passed in the SSE payload for per-key delay calculation. */
    const val DELAYED_FETCH_INTERVAL_MS: Long = 5_000L

    /** Seed passed in the SSE payload for MurmurHash3 delay calculation. */
    const val DELAYED_FETCH_SEED: Int = 42

    /**
     * SSE payload that includes hashing params (`i`, `s`, `h`).
     *
     * The SDK's [DefaultSyncDelayCalculator] uses these fields to compute a
     * per-key delay before re-fetching evaluations after the notification arrives.
     *
     * `h=1` → MURMUR3_32 algorithm.
     */
    const val SSE_EVALUATION_UPDATE_WITH_DELAY: String =
        """{"channel":"$STREAMING_CHANNEL",""" +
        """"data":"{\"type\":\"EVALUATIONS_UPDATE\",\"changeNumber\":2000,""" +
        """\"i\":$DELAYED_FETCH_INTERVAL_MS,\"s\":$DELAYED_FETCH_SEED,\"h\":1}",""" +
        """"timestamp":1000000}"""
}
