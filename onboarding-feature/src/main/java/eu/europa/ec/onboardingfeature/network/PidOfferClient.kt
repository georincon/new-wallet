package eu.europa.ec.onboardingfeature.network

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** La Registraduría rechazó o no pudo crear la oferta de la PID (con el motivo para mostrar al usuario). */
class PidOfferException(message: String) : Exception(message)

/**
 * Pide al issuer de la Registraduría la oferta de la PID (mso_mdoc + dc+sd-jwt) para una sesión de onboarding
 * APPROVED. El issuer verifica la sesión con el backend de onboarding, homologa los datos contra su base y
 * devuelve un `offerUri` de OpenID4VCI con un `issuer_state` de un solo uso (la autenticación posterior en
 * Keycloak es automática, sin contraseña).
 */
class PidOfferClient(private val issuerBaseUrl: String) {

    fun requestOffer(sessionId: String): String {
        val conn = (URL("$issuerBaseUrl/onboarding/offers").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15000
            readTimeout = 60000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }
        try {
            conn.outputStream.use { it.write(JSONObject().put("sessionId", sessionId).toString().toByteArray()) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw PidOfferException(describe(code, text))
            return JSONObject(text).optString("offerUri").ifBlank {
                throw PidOfferException("La Registraduría no devolvió la oferta de credencial.")
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun describe(code: Int, body: String): String {
        val json = runCatching { JSONObject(body) }.getOrNull()
        val reasons = json?.optJSONArray("reasons")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
        return when (code) {
            404 -> "Tu cédula no aparece en la base de la Registraduría."
            403 -> "Los datos no coinciden con la Registraduría" + reasons.takeIf { it.isNotEmpty() }
                ?.joinToString(prefix = ": ", separator = ", ").orEmpty() + "."
            else -> "La Registraduría respondió $code."
        }
    }
}
