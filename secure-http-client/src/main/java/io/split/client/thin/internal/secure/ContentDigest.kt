package io.split.client.thin.internal.secure

import android.util.Base64
import java.security.MessageDigest

internal object ContentDigest {
    private const val SHA_512 = "SHA-512"
    private const val DIGEST_BYTE_COUNT = 8

    fun compute(body: String): String {
        val digest = MessageDigest.getInstance(SHA_512)
            .digest(body.toByteArray(Charsets.UTF_8))
            .copyOf(DIGEST_BYTE_COUNT)
        return Base64.encodeToString(digest, Base64.NO_PADDING or Base64.NO_WRAP)
    }
}
