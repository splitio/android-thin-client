package io.split.client.thin.internal.persistence

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class GeneralPropertiesDaoTest {

    private lateinit var database: ThinClientDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ThinClientDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun `getByKey returns null when key not stored`() {
        assertNull(database.generalPropertiesDao().getByKey("someKey"))
    }

    @Test
    fun `insert and getByKey round trip`() {
        database.generalPropertiesDao().insert(GeneralPropertiesEntity(key = "myKey", value = "myValue"))

        val result = database.generalPropertiesDao().getByKey("myKey")
        assertEquals("myValue", result?.value)
    }

    @Test
    fun `insert replaces existing value for same key`() {
        database.generalPropertiesDao().insert(GeneralPropertiesEntity(key = "myKey", value = "first"))
        database.generalPropertiesDao().insert(GeneralPropertiesEntity(key = "myKey", value = "second"))

        val result = database.generalPropertiesDao().getByKey("myKey")
        assertEquals("second", result?.value)
    }

    @Test
    fun `deleteByKey removes entry`() {
        database.generalPropertiesDao().insert(GeneralPropertiesEntity(key = "myKey", value = "myValue"))
        database.generalPropertiesDao().deleteByKey("myKey")

        assertNull(database.generalPropertiesDao().getByKey("myKey"))
    }
}
