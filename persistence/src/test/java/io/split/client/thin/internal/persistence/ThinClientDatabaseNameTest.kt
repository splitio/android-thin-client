package io.split.client.thin.internal.persistence

import org.junit.Test
import kotlin.test.assertEquals

class ThinClientDatabaseNameTest {

    @Test
    fun `buildDatabaseName with long key returns first4 plus last4 plus extension`() {
        val name = ThinClientDatabase.buildDatabaseName(null, "abcd1234efgh")
        assertEquals("io.harness.thin.v3.abcdefgh.db", name)
    }

    @Test
    fun `buildDatabaseName with exactly 4 char key returns key repeated plus extension`() {
        val name = ThinClientDatabase.buildDatabaseName(null, "abcd")
        assertEquals("io.harness.thin.v3.abcdabcd.db", name)
    }

    @Test
    fun `buildDatabaseName with short key less than 4 chars returns fallback`() {
        val name = ThinClientDatabase.buildDatabaseName(null, "abc")
        assertEquals("io.harness.thin.v3.split_thin.db", name)
    }

    @Test
    fun `buildDatabaseName with null key returns fallback`() {
        val name = ThinClientDatabase.buildDatabaseName(null, null)
        assertEquals("io.harness.thin.v3.split_thin.db", name)
    }

    @Test
    fun `buildDatabaseName with prefix prepends it`() {
        val name = ThinClientDatabase.buildDatabaseName("myapp", "abcd1234efgh")
        assertEquals("io.harness.thin.v3.myapp.abcdefgh.db", name)
    }

    @Test
    fun `buildDatabaseName with prefix and short key returns fallback with prefix`() {
        val name = ThinClientDatabase.buildDatabaseName("myapp", "ab")
        assertEquals("io.harness.thin.v3.myapp.split_thin.db", name)
    }

    @Test
    fun `buildDatabaseName with null prefix behaves same as empty prefix`() {
        val withNull = ThinClientDatabase.buildDatabaseName(null, "abcd1234efgh")
        val withEmpty = ThinClientDatabase.buildDatabaseName("", "abcd1234efgh")
        assertEquals(withNull, withEmpty)
    }

    @Test
    fun `buildDatabaseName different keys produce different names`() {
        val name1 = ThinClientDatabase.buildDatabaseName(null, "aaaa1111bbbb")
        val name2 = ThinClientDatabase.buildDatabaseName(null, "cccc2222dddd")
        assert(name1 != name2)
    }

    @Test
    fun `buildDatabaseName same key produces same name`() {
        val name1 = ThinClientDatabase.buildDatabaseName(null, "aaaa1111bbbb")
        val name2 = ThinClientDatabase.buildDatabaseName(null, "aaaa1111bbbb")
        assertEquals(name1, name2)
    }
}
