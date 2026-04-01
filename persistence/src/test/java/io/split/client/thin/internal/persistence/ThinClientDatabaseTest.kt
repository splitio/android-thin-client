package io.split.client.thin.internal.persistence

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
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val db1 = ThinClientDatabase.build(context, "prefix1")
        val db2 = ThinClientDatabase.build(context, "prefix2")

        assertNotSame(db1, db2)
    }

    @Test
    fun `build with same prefix returns same instance`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val db1 = ThinClientDatabase.build(context, "same")
        val db2 = ThinClientDatabase.build(context, "same")

        kotlin.test.assertSame(db1, db2)
    }

    @Test
    fun `build with null and empty prefix returns same instance`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val db1 = ThinClientDatabase.build(context, null)
        val db2 = ThinClientDatabase.build(context, "")

        kotlin.test.assertSame(db1, db2)
    }
}
