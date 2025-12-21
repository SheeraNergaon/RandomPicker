package com.sheeranergaon.randompicker

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract
import com.firebase.ui.auth.data.model.FirebaseAuthUIAuthenticationResult
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : AppCompatActivity() {

    // Launcher for Google/Social Login (reuses your existing setup!)
    private val signInLauncher = registerForActivityResult(
        FirebaseAuthUIActivityResultContract()
    ) { res ->
        onSignInResult(res)
    }

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login) // 1. Load your new Design

        // Handle window insets (for edge-to-edge)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        auth = FirebaseAuth.getInstance()

        // 2. Check if already logged in
        if (auth.currentUser != null) {
            goToMainActivity()
            return
        }

        // 3. Find Views from your new XML
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnGoogle = findViewById<TextView>(R.id.btnGoogle)

        // 4. Handle "Log In" Button Click (Custom Email/Pass)
        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Sign in with standard Firebase Auth
            auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener {
                    goToMainActivity()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Login Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }

        // 5. Handle "Google" Button Click
        // We reuse the AuthUI launcher here because it's the easiest way to handle Google Sign-In
        btnGoogle.setOnClickListener {
            startSocialLogin()
        }
    }

    // This launches the FirebaseUI flow, but just for Social providers if you want
    private fun startSocialLogin() {
        val providers = arrayListOf(
            AuthUI.IdpConfig.GoogleBuilder().build()
            // You can add Facebook here too if you enabled it in console
        )

        val signInIntent = AuthUI.getInstance()
            .createSignInIntentBuilder()
            .setAvailableProviders(providers)
            .build()

        signInLauncher.launch(signInIntent)
    }

    private fun onSignInResult(result: FirebaseAuthUIAuthenticationResult) {
        if (result.resultCode == RESULT_OK) {
            goToMainActivity()
        } else {
            Toast.makeText(this, "Social Login Cancelled or Failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun goToMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}