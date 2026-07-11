package org.mountaineers.traillog

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.mountaineers.traillog.data.TrailLogRepository
import org.mountaineers.traillog.sync.SyncScheduler
import org.mountaineers.traillog.util.NetworkMonitor

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private var authStateListener: FirebaseAuth.AuthStateListener? = null
    private lateinit var syncBanner: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        TrailLogRepository.initialize(this)
        NetworkMonitor.start(this)

        auth = FirebaseAuth.getInstance()
        syncBanner = findViewById(R.id.sync_status_banner)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        bottomNav.setupWithNavController(navController)

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

        syncBanner.setOnClickListener {
            if (NetworkMonitor.isOnline.value) {
                TrailLogRepository.requestForceSync()
                Toast.makeText(this, R.string.banner_sync_requested, Toast.LENGTH_SHORT).show()
            }
        }

        // Hide banner on login; update text from pending count + connectivity
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    TrailLogRepository.pendingSyncCount,
                    TrailLogRepository.isSyncing,
                    NetworkMonitor.isOnline
                ) { pending, syncing, online ->
                    Triple(pending, syncing, online)
                }.collect { (pending, syncing, online) ->
                    val onLogin = navController.currentDestination?.id == R.id.loginFragment
                    updateSyncBanner(pending, syncing, online, hide = onLogin)
                }
            }
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            val pending = TrailLogRepository.pendingSyncCount.value
            val syncing = TrailLogRepository.isSyncing.value
            val online = NetworkMonitor.isOnline.value
            updateSyncBanner(pending, syncing, online, hide = destination.id == R.id.loginFragment)
        }
    }

    private fun updateSyncBanner(pending: Int, syncing: Boolean, online: Boolean, hide: Boolean) {
        if (hide || (online && pending == 0 && !syncing)) {
            syncBanner.visibility = View.GONE
            return
        }

        syncBanner.visibility = View.VISIBLE
        when {
            !online && pending > 0 -> {
                syncBanner.setBackgroundColor(ContextCompat.getColor(this, R.color.sync_banner_offline))
                syncBanner.text = getString(R.string.banner_offline_pending, pending)
            }
            !online -> {
                syncBanner.setBackgroundColor(ContextCompat.getColor(this, R.color.sync_banner_offline))
                syncBanner.text = getString(R.string.banner_offline)
            }
            syncing -> {
                syncBanner.setBackgroundColor(ContextCompat.getColor(this, R.color.sync_banner_syncing))
                syncBanner.text = getString(R.string.banner_syncing)
            }
            pending > 0 -> {
                syncBanner.setBackgroundColor(ContextCompat.getColor(this, R.color.sync_banner_pending))
                syncBanner.text = getString(R.string.banner_pending_tap, pending)
            }
            else -> syncBanner.visibility = View.GONE
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
