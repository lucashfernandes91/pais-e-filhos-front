package com.example.chatapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val requestNotificationPermissionCode = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Garantir que RetrofitClient está inicializado com AuthInterceptor
        RetrofitClient.init(this)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        // Navegação manual — garante que sempre volta para Home.
        // Listener único: marcar isChecked programaticamente não o dispara,
        // então a sincronização abaixo não precisa desanexar nada.
        bottomNav.setOnItemSelectedListener { item ->
            val navOptions = NavOptions.Builder()
                .setPopUpTo(R.id.homeFragment, false)
                .setLaunchSingleTop(true)
                .build()

            try {
                navController.navigate(item.itemId, null, navOptions)
                true
            } catch (e: Exception) {
                false
            }
        }

        // Sincronizar bottom nav com destino atual
        navController.addOnDestinationChangedListener { _, destination, _ ->
            val topLevelDestinations = setOf(
                R.id.homeFragment,
                R.id.chatFragment,
                R.id.agendaFragment,
                R.id.timelineFragment,
                R.id.profileFragment
            )

            // Mostrar/esconder bottom nav
            bottomNav.visibility = if (destination.id in topLevelDestinations) {
                View.VISIBLE
            } else {
                View.GONE
            }

            bottomNav.menu.findItem(destination.id)?.isChecked = true
        }

        // Register Firebase token
        registerFirebaseToken()

        // Load badges
        updateBottomNavBadges(bottomNav)

        // Request notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    requestNotificationPermissionCode
                )
            }
        }
    }

    private fun registerFirebaseToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "Fetching FCM token failed", task.exception)
                return@addOnCompleteListener
            }

            val token = task.result
            Log.d(TAG, "FCM Token obtained: $token")
        }
    }

    override fun onResume() {
        super.onResume()

        // Sessão caiu (refresh expirado → AuthInterceptor limpou): volta ao login.
        if (PrefsHelper.getAuthToken(this).isEmpty()) {
            startActivity(
                android.content.Intent(this, LoginActivity::class.java)
                    .addFlags(
                        android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                            android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                    )
            )
            finish()
            return
        }

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        updateBottomNavBadges(bottomNav)
        resumePendingInvite()
        showWelcomeIfPending()
    }

    /** B8: pós-cadastro, aponta os 2 passos que fazem o app funcionar. */
    private fun showWelcomeIfPending() {
        if (!PrefsHelper.isWelcomePending(this)) return
        PrefsHelper.setWelcomePending(this, false)

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.welcome_title)
            .setMessage(R.string.welcome_message)
            .setPositiveButton(R.string.welcome_go_to_profile) { _, _ ->
                val navHost = supportFragmentManager
                    .findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
                try {
                    navHost?.navController?.navigate(R.id.profileFragment)
                } catch (_: Exception) {
                }
            }
            .setNegativeButton(R.string.welcome_later, null)
            .show()
    }

    /** Deep link de convite recebido antes do login: retoma o aceite. */
    private fun resumePendingInvite() {
        val pendingCode = PrefsHelper.getPendingInviteCode(this) ?: return
        PrefsHelper.clearPendingInviteCode(this)
        startActivity(
            android.content.Intent(this, InviteAcceptActivity::class.java)
                .putExtra(InviteAcceptActivity.EXTRA_CODE, pendingCode)
        )
    }

    private fun updateBottomNavBadges(bottomNav: BottomNavigationView) {
        val token = PrefsHelper.getAuthToken(this)
        if (token.isEmpty()) return

        val conversationId = PrefsHelper.getConversationId(this)
        val currentUser = PrefsHelper.getUsername(this)

        lifecycleScope.launch {
            // ─── Chat badge: unread messages ───
            try {
                val messages = RetrofitClient.api.getMessages("Bearer $token", conversationId)
                val unreadCount = messages.count { msg ->
                    msg.sender != currentUser &&
                    msg.read_by?.any { it.reader_name == currentUser } != true
                }

                val chatBadge = bottomNav.getOrCreateBadge(R.id.chatFragment)
                if (unreadCount > 0) {
                    chatBadge.isVisible = true
                    chatBadge.number = unreadCount
                    chatBadge.backgroundColor = ContextCompat.getColor(this@MainActivity, R.color.error)
                    Log.d(TAG, "Chat badge: $unreadCount unread messages")
                } else {
                    bottomNav.removeBadge(R.id.chatFragment)
                    Log.d(TAG, "Chat badge: removed (no unread)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Chat badge error: ${e.message}")
                bottomNav.removeBadge(R.id.chatFragment)
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
