package com.releasewatch.app.data.playconsole

import android.util.Base64
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec

// Builds and RS256-signs a Google service-account JWT by hand (no google-api-client dependency)
// so it can be exchanged for an OAuth access token via the standard JWT-bearer grant.
object JwtSigner {

    fun createSignedJwt(
        clientEmail: String,
        privateKeyPem: String,
        scope: String,
        audience: String
    ): String {
        val nowSeconds = System.currentTimeMillis() / 1000
        val header = """{"alg":"RS256","typ":"JWT"}"""
        val claims = """{"iss":"$clientEmail","scope":"$scope","aud":"$audience","iat":$nowSeconds,"exp":${nowSeconds + 3600}}"""

        val signingInput = "${base64Url(header.toByteArray())}.${base64Url(claims.toByteArray())}"
        val signature = sign(signingInput.toByteArray(), privateKeyPem)
        return "$signingInput.${base64Url(signature)}"
    }

    private fun sign(data: ByteArray, privateKeyPem: String): ByteArray {
        val keyBytes = Base64.decode(cleanPem(privateKeyPem), Base64.DEFAULT)
        val privateKey = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyBytes))
        return Signature.getInstance("SHA256withRSA").apply {
            initSign(privateKey)
            update(data)
        }.sign()
    }

    private fun cleanPem(pem: String): String = pem
        .replace("-----BEGIN PRIVATE KEY-----", "")
        .replace("-----END PRIVATE KEY-----", "")
        .replace("\\n", "\n")
        .replace("\n", "")
        .replace("\r", "")
        .trim()

    private fun base64Url(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
}
