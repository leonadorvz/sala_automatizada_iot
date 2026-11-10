package cl.inacap.salaautomatizada

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import cl.inacap.salaautomatizada.databinding.ActivityLoginBinding
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        // Verificar si el usuario ya está autenticado (Sesión persistente OT)
        if (auth.currentUser != null) {
            navigateToMain()
        }

        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Por favor ingrese correo y contraseña", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        Iso27400SecurityManager.auditLog("LOGIN_FIREBASE", "EXITOSO: $email")
                        Toast.makeText(this, "Autenticación Exitosa", Toast.LENGTH_SHORT).show()
                        navigateToMain()
                    } else {
                        Iso27400SecurityManager.auditLog("LOGIN_FIREBASE", "FALLIDO: ${task.exception?.message}")
                        Toast.makeText(this, "Error de autenticación: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
        }

        binding.btnRegister.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Ingrese correo y contraseña para registrarse", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password.length < 6) {
                Toast.makeText(this, "La contraseña debe tener al menos 6 caracteres", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        Iso27400SecurityManager.auditLog("REGISTRO_FIREBASE", "EXITOSO: $email")
                        Toast.makeText(this, "Registro exitoso. Bienvenido", Toast.LENGTH_SHORT).show()
                        navigateToMain()
                    } else {
                        Iso27400SecurityManager.auditLog("REGISTRO_FIREBASE", "FALLIDO: ${task.exception?.message}")
                        Toast.makeText(this, "Error en registro: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
        }
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}
