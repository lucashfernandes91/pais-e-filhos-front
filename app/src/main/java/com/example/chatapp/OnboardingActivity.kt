package com.example.chatapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.activity.OnBackPressedCallback
import com.google.android.material.button.MaterialButton
import androidx.annotation.IdRes
import androidx.annotation.StringRes

class OnboardingActivity : AppCompatActivity() {

    private lateinit var title: TextView
    private lateinit var description: TextView
    private lateinit var dotsContainer: LinearLayout
    private lateinit var btnNext: MaterialButton
    private lateinit var btnSkip: TextView
    private lateinit var btnBack: ImageButton
    private var currentPage = 0

    data class OnboardingPage(
        @param:IdRes val previewId: Int,
        @param:StringRes val titleRes: Int,
        @param:StringRes val descriptionRes: Int
    )

    private val pages = listOf(
        OnboardingPage(
            R.id.previewAgenda,
            R.string.onboarding_organization_title,
            R.string.onboarding_organization_description
        ),
        OnboardingPage(
            R.id.previewConversation,
            R.string.onboarding_security_title,
            R.string.onboarding_security_description
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (BuildConfig.MOCK_LOGIN_ENABLED) {
            PrefsHelper.setOnboardingComplete(this, true)
            goToLogin()
            return
        }

        if (PrefsHelper.isOnboardingComplete(this)) {
            // Sessão salva: direto para o app, sem flash da tela de login.
            // Se o token estiver vencido, o AuthInterceptor renova; falhando,
            // o guard do MainActivity devolve ao login.
            if (PrefsHelper.getAuthToken(this).isNotEmpty()) {
                goToMain()
            } else {
                goToLogin()
            }
            return
        }

        setContentView(R.layout.activity_onboarding)

        title = findViewById(R.id.tvTitle)
        description = findViewById(R.id.tvDescription)
        dotsContainer = findViewById(R.id.dotsContainer)
        btnNext = findViewById(R.id.btnNext)
        btnSkip = findViewById(R.id.btnSkip)
        btnBack = findViewById(R.id.btnBack)

        prioritizeCopyOnCompactScreens()
        setupDots()
        updatePage(savedInstanceState?.getInt("onboarding_page") ?: 0)

        btnNext.setOnClickListener {
            goForwardOrComplete()
        }

        btnSkip.setOnClickListener {
            goToRegistration()
        }

        findViewById<View>(R.id.btnLogin).setOnClickListener {
            completeOnboarding()
        }

        btnBack.setOnClickListener {
            updatePage((currentPage - 1).coerceAtLeast(0))
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (currentPage > 0) updatePage(currentPage - 1) else finish()
            }
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("onboarding_page", currentPage)
        super.onSaveInstanceState(outState)
    }

    private fun prioritizeCopyOnCompactScreens() {
        val config = resources.configuration
        if (config.screenHeightDp >= 680 && config.fontScale <= 1.2f) return

        val content = findViewById<LinearLayout>(R.id.pageContent)
        val preview = findViewById<View>(R.id.previewContainer)
        content.removeView(preview)
        (preview.layoutParams as LinearLayout.LayoutParams).apply {
            topMargin = resources.getDimensionPixelSize(R.dimen.spacing_l)
            bottomMargin = 0
        }
        content.addView(preview)
    }

    private fun goForwardOrComplete() {
        if (currentPage < pages.lastIndex) {
            updatePage(currentPage + 1)
        } else {
            goToRegistration()
        }
    }

    private fun updatePage(position: Int) {
        currentPage = position.coerceIn(0, pages.lastIndex)
        val page = pages[currentPage]
        pages.forEach {
            findViewById<View>(it.previewId).visibility =
                if (it == page) View.VISIBLE else View.GONE
        }
        title.setText(page.titleRes)
        description.setText(page.descriptionRes)
        updateDots(currentPage)
        btnNext.setText(
            if (currentPage == pages.lastIndex) {
                R.string.onboarding_create_account
            } else {
                R.string.ui_continuar
            }
        )
        dotsContainer.contentDescription =
            getString(R.string.onboarding_page_position, currentPage + 1, pages.size)
        btnSkip.visibility = if (currentPage == 0) View.VISIBLE else View.INVISIBLE
        btnBack.visibility = if (currentPage == 0) View.INVISIBLE else View.VISIBLE
        findViewById<ScrollView>(R.id.onboardingScroll).post {
            findViewById<ScrollView>(R.id.onboardingScroll).scrollTo(0, 0)
        }
    }

    private fun setupDots() {
        dotsContainer.removeAllViews()
        val dp = { v: Int -> dpToPx(v) }
        for (i in pages.indices) {
            val dot = View(this).apply {
                val size = dp(8)
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    if (i < pages.lastIndex) marginEnd = dp(8)
                }
                background = ContextCompat.getDrawable(
                    this@OnboardingActivity,
                    if (i == 0) R.drawable.bg_dot_active else R.drawable.bg_dot_inactive
                )
            }
            dotsContainer.addView(dot)
        }
    }

    private fun updateDots(activeIndex: Int) {
        for (i in 0 until dotsContainer.childCount) {
            dotsContainer.getChildAt(i).background = ContextCompat.getDrawable(
                this,
                if (i == activeIndex) R.drawable.bg_dot_active else R.drawable.bg_dot_inactive
            )
        }
    }

    private fun completeOnboarding() {
        PrefsHelper.setOnboardingComplete(this, true)
        goToLogin()
    }

    private fun goToRegistration() {
        PrefsHelper.setOnboardingComplete(this, true)
        startActivity(Intent(this, RegisterActivity::class.java))
        finish()
    }

    private fun goToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

}
