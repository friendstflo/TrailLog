package org.mountaineers.traillog

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import org.mountaineers.traillog.data.TrailLogRepository

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment

        val navController = navHostFragment.navController

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        bottomNav.setupWithNavController(navController)

        // Safety for MapFragment tab switching
        bottomNav.setOnItemSelectedListener { item ->
            val currentFragment = navHostFragment.childFragmentManager.fragments.firstOrNull()
            if (currentFragment is org.mountaineers.traillog.ui.map.MapFragment && item.itemId != R.id.mapFragment) {
                currentFragment.arguments = null
            }
            navController.navigate(item.itemId)
            true
        }

        // === KEY FIX: Start listener for both new login AND remembered session ===
        if (auth.currentUser != null) {
            Log.d("MainActivity", "User already authenticated - starting listener")
            TrailLogRepository.startFirebaseListener()
            navController.navigate(R.id.mapFragment)
        } else {
            navController.navigate(R.id.loginFragment)
        }

        // Initialize Room on background thread
        lifecycleScope.launch {
            TrailLogRepository.initialize(this@MainActivity)
        }
    }
}