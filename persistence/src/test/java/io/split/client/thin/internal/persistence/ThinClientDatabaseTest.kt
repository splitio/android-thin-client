package io.split.client.thin.internal.persistence

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertNotSame

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ThinClientDatabaseTest {

    @Test
    fun `build with different prefixes returns different instances`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val db1 = ThinClientDatabase.build(context, "prefix1", null)
        val db2 = ThinClientDatabase.build(context, "prefix2", null)

        assertNotSame(db1, db2)
    }

    @Test
    fun `build with same prefix returns same instance`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val db1 = ThinClientDatabase.build(context, "same", null)
        val db2 = ThinClientDatabase.build(context, "same", null)

        kotlin.test.assertSame(db1, db2)
    }

    @Test
    fun `build with null and empty prefix returns same instance`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val db1 = ThinClientDatabase.build(context, null, null)
        val db2 = ThinClientDatabase.build(context, "", null)

        kotlin.test.assertSame(db1, db2)
    }

    @Test
    fun `build with different sdk keys returns different instances`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val db1 = ThinClientDatabase.build(context, null, "aaaa1111bbbb")
        val db2 = ThinClientDatabase.build(context, null, "cccc2222dddd")

        assertNotSame(db1, db2)
    }

    @Test
    fun `build with same sdk key returns same instance`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val db1 = ThinClientDatabase.build(context, null, "aaaa1111bbbb")
        val db2 = ThinClientDatabase.build(context, null, "aaaa1111bbbb")

        kotlin.test.assertSame(db1, db2)
    }
}
