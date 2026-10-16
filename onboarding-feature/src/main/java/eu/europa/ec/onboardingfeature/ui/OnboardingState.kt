package eu.europa.ec.onboardingfeature.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import eu.europa.ec.onboardingfeature.data.OnboardingData
import eu.europa.ec.onboardingfeature.data.OnboardingStatus
import eu.europa.ec.onboardingfeature.data.PersonalInfo
import eu.europa.ec.onboardingfeature.data.PersonalInfoStore
import eu.europa.ec.onboardingfeature.data.OnboardingStore
import eu.europa.ec.onboardingfeature.navigation.Step
import eu.europa.ec.onboardingfeature.network.BackendClient
import eu.europa.ec.onboardingfeature.network.BackendResult
import eu.europa.ec.onboardingfeature.network.DocumentUnreadableException
import eu.europa.ec.onboardingfeature.network.PidOfferClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class OnboardingState(
    private val store: OnboardingStore,
    private val status: OnboardingStatus,
    private val personalInfoStore: PersonalInfoStore,
) {

    /**
     * Reanudación híbrida: se conserva lo ya verificado (celular, correo, datos declarados) pero las
     * capturas biométricas/documentales nunca se reutilizan: se descartan al abrir el flujo.
     */
    var data by mutableStateOf(store.load())
        private set

    /**
     * Descarta capturas de una sesión anterior. Se llama UNA sola vez al entrar al flujo (no en el
     * constructor: si el estado se recrea a mitad del flujo no debe borrar las fotos recién tomadas).
     */
    fun discardStaleCaptures() {
        data = discardCaptures(data)
    }

    val hasAllCaptures: Boolean
        get() = listOf(data.frontImagePath, data.backImagePath, data.selfieImagePath)
            .all { it != null && File(it).exists() }

    private fun discardCaptures(d: OnboardingData): OnboardingData {
        listOf(d.frontImagePath, d.backImagePath, d.selfieImagePath)
            .forEach { p -> p?.let { runCatching { File(it).delete() } } }
        return d.copy(frontImagePath = null, backImagePath = null, selfieImagePath = null)
            .also { store.save(it) }
    }

    /** Primer paso pendiente según lo ya verificado. */
    fun resumeStep(): Step = when {
        !data.phoneVerified -> Step.Phone
        data.cedula.isBlank() || data.nombres.isBlank() || data.apellidos.isBlank() || data.birthDate.isBlank() -> Step.Cedula
        !data.emailVerified -> Step.Email
        !data.consentAccepted -> Step.Summary
        else -> Step.DocumentFront
    }

    /** Validación negativa: conserva contacto verificado, borra capturas y resultado. */
    fun prepareRetry() {
        data = discardCaptures(data).copy(consentAccepted = false).also { store.save(it) }
        resetBackend()
    }

    /** Validación aprobada: se limpian los datos temporales y se marca el onboarding como completado. */
    suspend fun markCompleted() {
        // Se conservan (cifrados) los datos validados de la cédula para "Info Personal" y la mayoría de edad.
        backendResult?.let { PersonalInfo.fromBackend(it) }?.let { personalInfoStore.save(it) }
        listOf(data.frontImagePath, data.backImagePath, data.selfieImagePath)
            .forEach { p -> p?.let { runCatching { File(it).delete() } } }
        store.clear()
        status.markCompleted()
    }

    var backendResult by mutableStateOf<BackendResult?>(null)
        private set

    var backendError by mutableStateOf<String?>(null)
        private set

    var backendRunning by mutableStateOf(false)
        private set

    /** El último intento falló porque la cédula no se pudo leer: hay que volver a tomar las fotos. */
    var retakeNeeded by mutableStateOf(false)
        private set

    private fun commit(updated: OnboardingData) {
        data = updated
        store.save(updated)
    }

    private fun newCode(): String = (100000..999999).random().toString()

    fun updatePhone(value: String) = commit(data.copy(phone = value.filter { it.isDigit() }.take(10)))

    /** Emula el envio del codigo por SMS: se genera en la app y se muestra en pantalla. */
    fun sendPhoneOtp() {
        commit(data.copy(phoneOtpGenerated = newCode(), phoneVerified = false))
    }

    fun verifyPhoneOtp(input: String): Boolean {
        val ok = input.isNotBlank() && input == data.phoneOtpGenerated
        if (ok) commit(data.copy(phoneVerified = true))
        return ok
    }

    /** Emula el envio del codigo por correo. */
    fun sendEmailOtp() {
        commit(data.copy(emailOtpGenerated = newCode(), emailVerified = false))
    }

    fun verifyEmailOtp(input: String): Boolean {
        val ok = input.isNotBlank() && input == data.emailOtpGenerated
        if (ok) commit(data.copy(emailVerified = true))
        return ok
    }

    fun updateCedula(value: String) = commit(data.copy(cedula = value.filter { it.isDigit() }.take(10)))

    fun updateNombres(value: String) = commit(data.copy(nombres = value))

    fun updateApellidos(value: String) = commit(data.copy(apellidos = value))

    fun updateBirthDate(value: String) = commit(data.copy(birthDate = value))

    fun updateEmail(value: String) = commit(data.copy(email = value.trim()))

    fun updateConsent(value: Boolean) = commit(data.copy(consentAccepted = value))

    fun updateFrontImage(path: String?) = commit(data.copy(frontImagePath = path))

    fun updateBackImage(path: String?) = commit(data.copy(backImagePath = path))

    fun updateSelfie(path: String?) = commit(data.copy(selfieImagePath = path))

    /**
     * Ejecuta el flujo completo contra el backend (red local): crea la sesion,
     * verifica contacto/consentimiento, sube las imagenes (frente, reverso, selfie)
     * y consulta el resultado (OCR + biometria).
     */
    suspend fun runBackend() {
        val d = data
        val front = d.frontImagePath?.let { File(it) }?.takeIf { it.exists() }
        val back = d.backImagePath?.let { File(it) }?.takeIf { it.exists() }
        val selfie = d.selfieImagePath?.let { File(it) }?.takeIf { it.exists() }
        if (front == null || back == null || selfie == null) {
            backendError = "Faltan imágenes capturadas (cédula frente/reverso y selfie)."
            return
        }
        backendRunning = true
        backendError = null
        backendResult = null
        try {
            val result = withContext(Dispatchers.IO) {
                BackendClient().runOnboarding(d, front, back, selfie)
            }
            backendResult = result
            // En cuanto la validación sale APPROVED, los datos y el reporte se guardan (cifrados) y el onboarding
            // queda completado: así no se pierden aunque se cierre la app o falle la emisión de la credencial.
            if (result.decision == "APPROVED" && !completedMarked) {
                markCompleted()
                completedMarked = true
            }
        } catch (e: DocumentUnreadableException) {
            backendError = e.message
            retakeNeeded = true
            // Las fotos que no sirvieron se eliminan: se vuelven a tomar desde cero.
            data = discardCaptures(data)
        } catch (e: Exception) {
            backendError = e.message ?: "No se pudo conectar con el backend."
        } finally {
            backendRunning = false
        }
    }

    /** Borra todo lo capturado (datos e imagenes) para empezar una validacion nueva. */
    fun resetAll() {
        listOf(data.frontImagePath, data.backImagePath, data.selfieImagePath)
            .forEach { p -> p?.let { runCatching { File(it).delete() } } }
        store.clear()
        data = OnboardingData()
        resetBackend()
    }

    /** Decisión no aprobada por datos que no coinciden con la cédula (vs. otras causas: rostro, calidad). */
    fun hasDataMismatch(): Boolean {
        val m = backendResult?.dataMatch.orEmpty()
        return m["documentNumberMatch"] == "false" || m["birthDateMatch"] == "false" ||
            (m["firstNamesSimilarity"]?.toDoubleOrNull() ?: 1.0) < 0.75 ||
            (m["lastNamesSimilarity"]?.toDoubleOrNull() ?: 1.0) < 0.75
    }

    var issuing by mutableStateOf(false)
        private set

    var issuingError by mutableStateOf<String?>(null)
        private set

    private var completedMarked = false

    /**
     * Tras APPROVED: guarda los datos validados (una sola vez) y pide al issuer de la Registraduría la oferta
     * de la PID. Devuelve el `offerUri` o null si falló (el motivo queda en [issuingError]).
     */
    suspend fun completeAndRequestPidOffer(issuerBaseUrl: String): String? {
        val sessionId = backendResult?.sessionId ?: return null.also { issuingError = "No hay una validación aprobada." }
        issuing = true
        issuingError = null
        return try {
            if (!completedMarked) {
                markCompleted()
                completedMarked = true
            }
            withContext(Dispatchers.IO) { PidOfferClient(issuerBaseUrl).requestOffer(sessionId) }
        } catch (e: Exception) {
            issuingError = e.message ?: "No se pudo crear tu credencial."
            null
        } finally {
            issuing = false
        }
    }

    /** Descarta solo las capturas (fotos) y el resultado; conserva los datos verificados. */
    fun prepareRetakePhotos() {
        data = discardCaptures(data)
        resetBackend()
    }

    fun resetBackend() {
        retakeNeeded = false
        backendRunning = false
        backendError = null
        backendResult = null
    }
}
