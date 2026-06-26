package io.split.client.thin.internal.secure

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ContentDigestTest {

    @Test
    fun `compute accepts a body string and produces deterministic digest`() {
        val body = """{"attributes":{},"bucketingKey":null,"configs":false,"key":"user1","sets":[]}"""

        val digest = ContentDigest.compute(body)

        assertNotNull(digest)
        assertEquals(digest, ContentDigest.compute(body))
    }

    @Test
    fun `different body strings produce different digests`() {
        val body1 = """{"attributes":{},"bucketingKey":null,"configs":false,"key":"user1","sets":[]}"""
        val body2 = """{"attributes":{"a":1},"bucketingKey":null,"configs":false,"key":"user1","sets":[]}"""

        assert(ContentDigest.compute(body1) != ContentDigest.compute(body2))
    }

    @Test
    fun `same body string always produces same digest`() {
        val body = """{"attributes":{"a":1},"bucketingKey":null,"configs":false,"key":"user1","sets":[]}"""

        assertEquals(ContentDigest.compute(body), ContentDigest.compute(body))
    }
}
