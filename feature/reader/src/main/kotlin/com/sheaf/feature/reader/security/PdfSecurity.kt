package com.sheaf.feature.reader.security

/** Password protect / unlock a PDF via a security-capable engine. */
interface PdfSecurity {
    /** Encrypt [uri] with [password] (AES-128); returns output file path or null. */
    suspend fun encrypt(uri: String, password: String): String?

    /** Remove protection from [uri] using [password]; returns output file path, or null if wrong/failed. */
    suspend fun decrypt(uri: String, password: String): String?
}
