package org.mountaineers.traillog.ui.auth

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.firebase.auth.FirebaseAuth
import org.mountaineers.traillog.R
import org.mountaineers.traillog.data.TrailLogRepository

class LoginFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var cbRememberMe: CheckBox
    private lateinit var btnLogin: Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_login, container, false)

        auth = FirebaseAuth.getInstance()

        etEmail = view.findViewById(R.id.et_email)
        etPassword = view.findViewById(R.id.et_password)
        cbRememberMe = view.findViewById(R.id.cb_remember_me)
        btnLogin = view.findViewById(R.id.btn_login)

        val prefs = requireContext().getSharedPreferences("traillog_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("remember_me", false)) {
            etEmail.setText(prefs.getString("saved_email", ""))
            cbRememberMe.isChecked = true
        }

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(requireContext(), "Email and password are required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnLogin.isEnabled = false
            Log.d("LoginFragment", "Attempting login with email: $email")

            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    btnLogin.isEnabled = true

                    if (task.isSuccessful) {
                        val user = auth.currentUser
                        Log.d("LoginFragment", "✅ Login SUCCESS for user: ${user?.uid}")

                        // Save preferences
                        prefs.edit()
                            .putBoolean("remember_me", cbRememberMe.isChecked)
                            .putString("saved_email", if (cbRememberMe.isChecked) email else "")
                            .apply()

                        // Start listener
                        TrailLogRepository.startFirebaseListener()

                        // Navigate
                        findNavController().navigate(R.id.action_login_to_map)

                        Toast.makeText(requireContext(), "Welcome back!", Toast.LENGTH_SHORT).show()
                    } else {
                        val error = task.exception
                        Log.e("LoginFragment", "❌ Login FAILED", error)
                        Toast.makeText(
                            requireContext(),
                            "Login failed: ${error?.message ?: "Unknown error"}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
        }

        return view
    }
}