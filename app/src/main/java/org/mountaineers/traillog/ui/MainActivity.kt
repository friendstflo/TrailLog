package org.mountaineers.traillog

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import org.mountaineers.traillog.data.TrailLogRepository
import org.mountaineers.traillog.sync.SyncScheduler

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private var authStateListener: FirebaseAuth.AuthStateListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Room / Firestore persistence already set up in TrailLogApp; safe to re-init
        TrailLogRepository.initialize(this)

        auth = FirebaseAuth.getInstance()

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        bottomNav.setupWithNavController(navController)

        // Safety for MapFragment tab switching (clear one-shot map target args)
        bottomNav.setOnItemSelectedListener { item ->
            val currentFragment = navHostFragment.childFragmentManager.fragments.firstOrNull()
            if (currentFragment is org.mountaineers.traillog.ui.map.MapFragment &&
                item.itemId != R.id.mapFragment
            ) {
                currentFragment.arguments = null
            }
            navController.navigate(item.itemId)
            true
        }

        // AuthStateListener: remembered login + new login + logout
        authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                Log.d("MainActivity", "Auth state: signed in uid=${user.uid}")
                TrailLogRepository.startFirebaseListener()
                SyncScheduler.schedulePeriodic(this@MainActivity)
                SyncScheduler.enqueueImmediate(this@MainActivity)
                if (navController.currentDestination?.id == R.id.loginFragment) {
                    navController.navigate(R.id.action_login_to_map)
                }
            } else {
                Log.d("MainActivity", "Auth state: signed out")
                TrailLogRepository.stopFirebaseListener()
                if (navController.currentDestination?.id != R.id.loginFragment) {
                    navController.navigate(R.id.loginFragment)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        authStateListener?.let { auth.addAuthStateListener(it) }
    }

    override fun onStop() {
        super.onStop()
        authStateListener?.let { auth.removeAuthStateListener(it) }
    }
}
