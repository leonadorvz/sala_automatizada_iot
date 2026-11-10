package cl.inacap.salaautomatizada

import android.util.Log
import java.security.MessageDigest

/**
 * Gestor de Seguridad OT basado en la norma ISO 27400 para sistemas IoT industriales.
 * Proporciona cifrado/hashing SHA-256 para verificación de PIN de operadores,
 * validación de comandos manuales críticos y registros de auditoría de seguridad.
 */
object Iso27400SecurityManager {
    private const val TAG = "Iso27400SecurityManager"

    // PIN de operador predeterminado: "1234" (Hash SHA-256: 03ac674216f3e15c761ee1a5e255f067953623c8b388b4459e13f978d7c846f4)
    private const val DEFAULT_PIN_HASH = "03ac674216f3e15c761ee1a5e255f067953623c8b388b4459e13f978d7c846f4"

    /**
     * Calcula el hash SHA-256 de una contraseña o PIN en texto plano.
     */
    fun hashPin(pin: String): String {
        return try {
            val bytes = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray(Charsets.UTF_8))
            bytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculando hash SHA-256", e)
            ""
        }
    }

    /**
     * Valida si el PIN ingresado por el operador es correcto (ISO 27400 Control de Acceso OT).
     */
    fun validateOperatorPin(enteredPin: String): Boolean {
        val hashedInput = hashPin(enteredPin)
        val isValid = hashedInput.equals(DEFAULT_PIN_HASH, ignoreCase = true)
        auditLog("VERIFICACION_PIN_OPERADOR", if (isValid) "AUTORIZADO" else "DENEGADO")
        return isValid
    }

    /**
     * Registra eventos de seguridad y comandos manuales según directrices ISO 27400.
     */
    fun auditLog(action: String, status: String) {
        val timestamp = System.currentTimeMillis()
        Log.w(TAG, "[ISO-27400 AUDIT] TS=$timestamp | Acción: $action | Estado: $status")
    }
}
