package io.split.client.thin

import org.junit.Assert.assertNotNull
import org.junit.Test

class SplitFactoryBuilderTest {

    @Test
    fun `build returns a factory with a manager`() {
        val factory = SplitFactoryBuilder.build(
            sdkKey = SdkKey("sdk-key"),
            defaultTarget = Target(Key("user-1")),
        )

        assertNotNull(factory)
        assertNotNull(factory.getManager())
    }
}
