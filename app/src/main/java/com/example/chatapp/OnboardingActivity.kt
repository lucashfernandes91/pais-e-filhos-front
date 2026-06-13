package com.example.chatapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

class OnboardingActivity : AppCompatActivity() {

    private lateinit var icon: ImageView
    private lateinit var title: TextView
    private lateinit var description: TextView
    private lateinit var dotsContainer: LinearLayout
    private lateinit var btnNext: MaterialButton
    private lateinit var btnSkip: TextView
    private lateinit var btnBack: ImageButton
    private lateinit var bottomActions: LinearLayout
    private var currentPage = 0

    data class OnboardingPage(
        @param:DrawableRes val iconRes: Int,
        @param:StringRes val titleRes: Int,
        @param:StringRes val descriptionRes: Int,
        @param:StringRes val secondaryActionRes: Int
    )

    private val pages = listOf(
        OnboardingPage(
            R.drawable.ic_onboarding_organization,
            R.string.onboarding_organization_title,
            R.string.onboarding_organization_description,
            R.string.ui_pular_introducao
        ),
        OnboardingPage(
            R.drawable.ic_onboarding_security,
            R.string.onboarding_security_title,
            R.string.onboarding_security_description,
            R.string.onboarding_have_account
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (PrefsHelper.isOnboardingComplete(this)) {
            goToLogin()
            return
        }

        setContentView(R.layout.activity_onboarding)

        icon = findViewById(R.id.ivIcon)
        title = findViewById(R.id.tvTitle)
        description = findViewById(R.id.tvDescription)
        dotsContainer = findViewById(R.id.dotsContainer)
        btnNext = findViewById(R.id.btnNext)
        btnSkip = findViewById(R.id.btnSkip)
        btnBack = findViewById(R.id.btnBack)
        bottomActions = findViewById(R.id.bottomActions)

        bottomActions.bringToFront()
        btnNext.bringToFront()
        btnSkip.bringToFront()

        setupDots()
        setupSystemInsets()
        updatePage(0)

        btnNext.setOnClickListener {
            goForwardOrComplete()
        }

        btnSkip.setOnClickListener {
            completeOnboarding()
        }

        btnBack.setOnClickListener {
            updatePage((currentPage - 1).coerceAtLeast(0))
        }
    }

    private fun goForwardOrComplete() {
        if (currentPage < pages.lastIndex) {
            updatePage(currentPage + 1)
        } else {
            completeOnboarding()
        }
    }

    private fun updatePage(position: Int) {
        currentPage = position.coerceIn(0, pages.lastIndex)
        val page = pages[currentPage]
        icon.setImageResource(page.iconRes)
        title.setText(page.titleRes)
        description.setText(page.descriptionRes)
        updateDots(currentPage)
        btnNext.setText(
            if (currentPage == pages.lastIndex) {
                R.string.onboarding_start
            } else {
                R.string.ui_continuar
            }
        )
        btnSkip.setText(page.secondaryActionRes)
        btnBack.visibility = if (currentPage == 0) View.GONE else View.VISIBLE
    }

    private fun setupSystemInsets() {
        val originalBottomPadding = bottomActions.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(bottomActions) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                view.paddingLeft,
                view.paddingTop,
                view.paddingRight,
                originalBottomPadding + systemBars.bottom
            )
            insets
        }
    }

    private fun setupDots() {
        dotsContainer.removeAllViews()
        val dp = { v: Int -> (v * resources.displayMetrics.density).toInt() }
        for (i in pages.indices) {
            val dot = View(this).apply {
                val size = dp(8)
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = dp(8)
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

    private fun goToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

}
