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

        // Navegação manual — garante que sempre volta para Home
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

            // Atualizar item selecionado sem disparar listener
            val menuItem = bottomNav.menu.findItem(destination.id)
            if (menuItem != null) {
                bottomNav.setOnItemSelectedListener(null)
                menuItem.isChecked = true
                // Reattach listener
                bottomNav.setOnItemSelectedListener { item ->
                    val opts = NavOptions.Builder()
                        .setPopUpTo(R.id.homeFragment, false)
                        .setLaunchSingleTop(true)
                        .build()
                    try {
                        navController.navigate(item.itemId, null, opts)
                        true
                    } catch (e: Exception) {
                        false
                    }
                }
            }
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
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        updateBottomNavBadges(bottomNav)
    }

    private fun updateBottomNavBadges(bottomNav: BottomNavigationView) {
        val token = PrefsHelper.getAuthToken(this)
        if (token.isEmpty()) return

        val conversationId = PrefsHelper.getConversationId(this)
        val currentUser = PrefsHelper.getUsername(this)

        lifecycleScope.launch {
            // ─── Chat badge: unread messages ───
            try {
                // Contar mensagens do outro pai (assumir todas como não lidas já que read_by não vem no getMessages)
                val messages = RetrofitClient.api.getMessages("Bearer $token", conversationId)
                val unreadCount = messages.count { msg -> msg.sender != currentUser }
                
                // TODO: Idealmente usar /messages/detail/ para cada msg para ter read_by completo
                // Por enquanto: contar quantas mensagens o outro pai enviou = todas são "potencialmente" não lidas
                // Se necessário validar lidas, usar endpoint específico no futuro
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

            // ─── Agenda badge: events today ───
            try {
                val events = RetrofitClient.api.getEvents("Bearer $token", conversationId)
                val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
                val todayCount = events.count { event ->
                    try {
                        // event_date é DateTime (2024-04-30T10:00:00Z), extrair apenas data
                        event.event_date.substring(0, 10) == todayStr
                    } catch (e: Exception) {
                        false
                    }
                }
                val agendaBadge = bottomNav.getOrCreateBadge(R.id.agendaFragment)
                if (todayCount > 0) {
                    agendaBadge.isVisible = true
                    agendaBadge.number = todayCount
                    agendaBadge.backgroundColor = ContextCompat.getColor(this@MainActivity, R.color.primary_blue)
                    Log.d(TAG, "Agenda badge: $todayCount events today")
                } else {
                    bottomNav.removeBadge(R.id.agendaFragment)
                    Log.d(TAG, "Agenda badge: removed (no events today)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Agenda badge error: ${e.message}")
                bottomNav.removeBadge(R.id.agendaFragment)
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
