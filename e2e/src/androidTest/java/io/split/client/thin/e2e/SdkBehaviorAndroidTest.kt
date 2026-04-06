package io.split.client.thin.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith

/**
 * Phase 2 behavioral E2E tests for the Android Thin Client SDK.
 *
 * Tests use [MockSplitServer] to intercept network traffic and [E2EFixtures] for
 * pre-built JSON payloads.
 *
 * Tests are added incrementally in subsequent branches (_4 through _11).
 */
@RunWith(AndroidJUnit4::class)
class SdkBehaviorAndroidTest
