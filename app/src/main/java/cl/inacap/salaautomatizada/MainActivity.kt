package cl.inacap.salaautomatizada

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import cl.inacap.salaautomatizada.databinding.ActivityMainBinding
import cl.inacap.salaautomatizada.databinding.DialogOperatorPinBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private var isUpdatingSwitchProgrammatically = false
    private val temperatureThreshold = 30.0 // Umbral crítico configurable ISO 27400
    private var userRole: String = "profesor" // Por defecto rol restrictivo (Principio de Menor Privilegio)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val currentUser = auth.currentUser
        if (currentUser == null) {
            navigateToLogin()
            return
        }

        // Consultar el rol del usuario en Firestore (Colección "usuarios", ID = UID)
        checkUserRoleAndPermissions(currentUser.uid, currentUser.email)

        binding.btnLogout.setOnClickListener {
            auth.signOut()
            Iso27400SecurityManager.auditLog("LOGOUT", "Sesión cerrada por ${currentUser.email}")
            navigateToLogin()
        }

        // Listener seguro para el ventilador protegido por ISO 27400 y control de roles INACAP
        binding.switchFan.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingSwitchProgrammatically) return@setOnCheckedChangeListener

            // Validar si el rol es "admin"
            if (userRole != "admin") {
                isUpdatingSwitchProgrammatically = true
                binding.switchFan.isChecked = !isChecked
                isUpdatingSwitchProgrammatically = false

                Toast.makeText(this, "Acceso denegado: El rol '$userRole' solo tiene permisos de visualización.", Toast.LENGTH_LONG).show()
                Iso27400SecurityManager.auditLog("CONTROL_DENEGADO", "Usuario con rol '$userRole' intentó controlar ventilador")
                return@setOnCheckedChangeListener
            }

            // Revertir temporalmente hasta verificar PIN de operador admin
            isUpdatingSwitchProgrammatically = true
            binding.switchFan.isChecked = !isChecked
            isUpdatingSwitchProgrammatically = false

            showOperatorPinDialog(isChecked)
        }

        // Sincronización en tiempo real con Firestore (Arduino R4 WiFi telemetry)
        setupFirestoreListener()
    }

    private fun checkUserRoleAndPermissions(uid: String, email: String?) {
        db.collection("usuarios").document(uid)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    userRole = document.getString("rol")?.lowercase() ?: "profesor"
                } else {
                    // Si no existe el documento en "usuarios", crear uno por defecto como "profesor"
                    val defaultUserData = mapOf(
                        "email" to (email ?: ""),
                        "rol" to "profesor"
                    )
                    db.collection("usuarios").document(uid).set(defaultUserData)
                    userRole = "profesor"
                }
                applyRolePermissions()
            }
            .addOnFailureListener { e ->
                // Ante fallo de red, por seguridad aplicar rol restringido "profesor"
                userRole = "profesor"
                applyRolePermissions()
                Toast.makeText(this, "Error obteniendo rol: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun applyRolePermissions() {
        val isAdmin = (userRole == "admin")
        binding.switchFan.isEnabled = isAdmin

        val roleDisplay = if (isAdmin) "Administrador (Control Total)" else "Profesor (Solo Lectura)"
        binding.tvUserEmail.text = "${auth.currentUser?.email}\nRol: $roleDisplay"

        Iso27400SecurityManager.auditLog("CONTROL_ACCESO_ROL", "Usuario asignado con rol: $userRole | Controles habilitados: $isAdmin")
    }

    private fun setupFirestoreListener() {
        // Documento de telemetría de la sala: colección "sala_automatizada", documento "telemeter"
        db.collection("sala_automatizada").document("telemeter")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Iso27400SecurityManager.auditLog("FIRESTORE_SYNC", "Error escuchando telemetría: ${e.message}")
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val temp = snapshot.getDouble("temperatura") ?: 24.0
                    val hum = snapshot.getDouble("humedad") ?: 50.0
                    val people = snapshot.getLong("personas")?.toInt() ?: 0
                    val light = snapshot.getString("luz") ?: "Óptimo"
                    val fanState = snapshot.getBoolean("ventilador") ?: false

                    updateTelemetryUI(temp, hum, people, light, fanState)
                } else {
                    // Inicializar documento por defecto si no existe en Firestore
                    val initialData = mapOf(
                        "temperatura" to 24.5,
                        "humedad" to 55.0,
                        "personas" to 2,
                        "luz" to "Óptimo",
                        "ventilador" to false
                    )
                    db.collection("sala_automatizada").document("telemeter").set(initialData)
                }
            }
    }

    private fun updateTelemetryUI(temp: Double, hum: Double, people: Int, light: String, fanState: Boolean) {
        binding.tvTemperature.text = String.format(Locale.getDefault(), "%.1f °C", temp)
        binding.tvHumidity.text = String.format(Locale.getDefault(), "%.0f %%", hum)
        binding.tvPeopleCount.text = people.toString()
        binding.tvLightStatus.text = light

        isUpdatingSwitchProgrammatically = true
        binding.switchFan.isChecked = fanState
        isUpdatingSwitchProgrammatically = false

        // Validación de temperatura frente a umbral crítico (ISO 27400)
        if (temp > temperatureThreshold) {
            binding.cardAlert.visibility = View.VISIBLE
            binding.cardTemp.setCardBackgroundColor(Color.parseColor("#B71C1C")) // Rojo crítico
        } else {
            binding.cardAlert.visibility = View.GONE
            binding.cardTemp.setCardBackgroundColor(Color.parseColor("#1E1E1E")) // Normal
        }
    }

    private fun showOperatorPinDialog(targetFanState: Boolean) {
        val dialogBinding = DialogOperatorPinBinding.inflate(LayoutInflater.from(this))

        AlertDialog.Builder(this)
            .setTitle("Seguridad Industrial OT")
            .setView(dialogBinding.root)
            .setCancelable(false)
            .setPositiveButton("Autorizar") { _, _ ->
                val pin = dialogBinding.etOperatorPin.text.toString().trim()
                if (Iso27400SecurityManager.validateOperatorPin(pin)) {
                    Toast.makeText(this, "Comando autorizado por operador", Toast.LENGTH_SHORT).show()
                    updateFanStateInFirestore(targetFanState)
                } else {
                    Toast.makeText(this, "PIN incorrecto. Comando denegado.", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun updateFanStateInFirestore(newState: Boolean) {
        db.collection("sala_automatizada").document("telemeter")
            .update("ventilador", newState)
            .addOnSuccessListener {
                Iso27400SecurityManager.auditLog("CONTROL_VENTILADOR", "Estado cambiado a: $newState")
                Toast.makeText(this, "Ventilador actualizado en Arduino/Cloud", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Error actualizando actuador: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish()
    }
}
