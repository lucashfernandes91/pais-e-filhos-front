package com.example.chatapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import android.view.LayoutInflater
import android.view.ViewGroup
import com.google.android.material.button.MaterialButton

class OnboardingActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var dotsContainer: LinearLayout
    private lateinit var btnNext: MaterialButton
    private lateinit var btnSkip: TextView

    data class OnboardingPage(
        val iconRes: Int,
        val title: String,
        val description: String
    )

    private val pages = listOf(
        OnboardingPage(
            R.drawable.ic_shield,
            "Comunicação segura",
            "Todas as mensagens são registro oficial e não podem ser editadas ou apagadas. Válido para fins legais."
        ),
        OnboardingPage(
            R.drawable.ic_calendar,
            "Agenda compartilhada",
            "Organize consultas, escola e convivência em um só lugar. Sem confusão, sem esquecimento."
        ),
        OnboardingPage(
            R.drawable.ic_chat,
            "Simples como WhatsApp",
            "Interface familiar e fácil de usar. Tudo em 1-2 toques. Sem complicação."
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Verificar se já completou onboarding
        val prefs = getSharedPreferences("coparent", MODE_PRIVATE)
        if (prefs.getBoolean("onboarding_complete", false)) {
            goToLogin()
            return
        }

        setContentView(R.layout.activity_onboarding)

        viewPager = findViewById(R.id.viewPager)
        dotsContainer = findViewById(R.id.dotsContainer)
        btnNext = findViewById(R.id.btnNext)
        btnSkip = findViewById(R.id.btnSkip)

        viewPager.adapter = OnboardingAdapter(pages)

        setupDots()

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateDots(position)
                btnNext.text = if (position == pages.size - 1) "Começar" else "Continuar"
            }
        })

        btnNext.setOnClickListener {
            if (viewPager.currentItem < pages.size - 1) {
                viewPager.currentItem += 1
            } else {
                completeOnboarding()
            }
        }

        btnSkip.setOnClickListener {
            completeOnboarding()
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
        getSharedPreferences("coparent", MODE_PRIVATE)
            .edit().putBoolean("onboarding_complete", true).apply()
        goToLogin()
    }

    private fun goToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    // Adapter
    inner class OnboardingAdapter(
        private val pages: List<OnboardingPage>
    ) : RecyclerView.Adapter<OnboardingAdapter.PageViewHolder>() {

        inner class PageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.ivIcon)
            val title: TextView = view.findViewById(R.id.tvTitle)
            val description: TextView = view.findViewById(R.id.tvDescription)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_onboarding_page, parent, false)
            return PageViewHolder(view)
        }

        override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
            val page = pages[position]
            holder.icon.setImageResource(page.iconRes)
            holder.title.text = page.title
            holder.description.text = page.description
        }

        override fun getItemCount() = pages.size
    }
}
