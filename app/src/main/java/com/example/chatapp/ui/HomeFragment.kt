package com.example.chatapp.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.chatapp.Event
import com.example.chatapp.PrefsHelper
import com.example.chatapp.R
import com.example.chatapp.RetrofitClient
import com.example.chatapp.SkeletonAnimator
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class HomeFragment : Fragment() {

    private var swipeRefresh: SwipeRefreshLayout? = null

    private fun getGreetingByTime(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 0..11 -> "Bom dia"
            in 12..17 -> "Boa tarde"
            else -> "Boa noite"
        }
    }

    private fun formatEventDate(eventDate: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val inputFormatAlt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val dayMonthFormat = SimpleDateFormat("dd 'de' MMM.", Locale("pt", "BR"))
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

            val date = try { inputFormat.parse(eventDate) } catch (e: Exception) { inputFormatAlt.parse(eventDate) }

            if (date != null && eventDate.contains("T")) {
                "${dayMonthFormat.format(date)} · ${timeFormat.format(date)}"
            } else if (date != null) {
                dayMonthFormat.format(date)
            } else "Dia inteiro"
        } catch (e: Exception) { "Dia inteiro" }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_home, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val username = PrefsHelper.getUsername(requireContext())
        val token = PrefsHelper.getAuthToken(requireContext())
        val displayName = username.replaceFirstChar { it.uppercase() }

        // Data dinâmica
        val dateFormat = SimpleDateFormat("EEEE, d 'de' MMMM", Locale("pt", "BR"))
        view.findViewById<TextView>(R.id.tvDate)?.text = dateFormat.format(Date())

        // Saudação dinâmica
        view.findViewById<TextView>(R.id.tvGreeting)?.text = "${getGreetingByTime()}, $displayName"

        // Pull-to-refresh
        swipeRefresh = view.findViewById(R.id.swipeRefreshHome)
        swipeRefresh?.setColorSchemeResources(R.color.primary_blue)
        swipeRefresh?.setOnRefreshListener {
            if (token.isNotEmpty()) loadAllData(view, token)
            else swipeRefresh?.isRefreshing = false
        }

        // Navegação
        view.findViewById<View>(R.id.btnNotifications)?.setOnClickListener {
            try { findNavController().navigate(R.id.action_home_to_notifications) }
            catch (_: Exception) {}
        }
        view.findViewById<View>(R.id.btnNewMessage)?.setOnClickListener {
            try { findNavController().navigate(R.id.chatFragment) } catch (_: Exception) {}
        }
        view.findViewById<View>(R.id.btnNewEvent)?.setOnClickListener {
            try { findNavController().navigate(R.id.agendaFragment) } catch (_: Exception) {}
        }
        view.findViewById<View>(R.id.btnViewAgenda)?.setOnClickListener {
            try { findNavController().navigate(R.id.agendaFragment) } catch (_: Exception) {}
        }

        // Carregar dados
        if (token.isNotEmpty()) {
            val skeleton = view.findViewById<View>(R.id.skeletonHome)
            skeleton?.let { SkeletonAnimator.startShimmer(it) }
            loadAllData(view, token)
        }
    }

    private fun loadAllData(view: View, token: String) {
        loadUpcomingEvents(view, token)
        loadUnreadNotificationsCount(view, token)
        loadMessagesPreview(view, token)
    }

    private fun loadUpcomingEvents(view: View, token: String) {
        val container = view.findViewById<LinearLayout>(R.id.eventsContainer) ?: return

        lifecycleScope.launch {
            try {
                val conversationId = PrefsHelper.getConversationId(requireContext())
                val events = RetrofitClient.api.getEvents("Bearer $token", conversationId)
                val now = Date()

                // --- Card de Guarda dinâmico ---
                updateCustodyCard(view, events, now)

                // --- Próximos eventos (hoje + futuros) ---
                val todayCal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val todayStart = todayCal.time

                val upcoming = events
                    .filter {
                        val eventDate = parseEventDate(it.event_date)
                        eventDate != null && !eventDate.before(todayStart)
                    }
                    .sortedBy { it.event_date }
                    .take(3)

                container.removeAllViews()

                if (upcoming.isEmpty()) {
                    container.addView(createEmptyEventsCard())
                    return@launch
                }

                for (event in upcoming) {
                    val dateStr = formatEventDate(event.event_date)
                    container.addView(createEventRow(event.title, dateStr, event.event_type))
                }
            } catch (_: Exception) {
            } finally {
                swipeRefresh?.isRefreshing = false
                // Hide skeleton
                val skeleton = view.findViewById<View>(R.id.skeletonHome)
                skeleton?.let {
                    SkeletonAnimator.stopShimmer(it)
                    it.visibility = View.GONE
                }
            }
        }
    }

    // ── Messages preview card ─────────────────────

    private fun loadMessagesPreview(view: View, token: String) {
        val cardMessages = view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardMessages) ?: return
        val tvTitle = view.findViewById<TextView>(R.id.tvMessagesTitle)
        val tvPreview = view.findViewById<TextView>(R.id.tvMessagesPreview)
        val tvUnreadBadge = view.findViewById<TextView>(R.id.tvUnreadBadge)

        lifecycleScope.launch {
            try {
                val conversationId = PrefsHelper.getConversationId(requireContext())
                val messages = RetrofitClient.api.getMessages("Bearer $token", conversationId)
                val currentUser = PrefsHelper.getUsername(requireContext())

                if (messages.isEmpty()) {
                    cardMessages.visibility = View.GONE
                    return@launch
                }

                cardMessages.visibility = View.VISIBLE

                // Last message preview
                val lastMsg = messages.last()
                val senderName = if (lastMsg.sender == currentUser) "Voc\u00ea" else lastMsg.sender.replaceFirstChar { it.uppercase() }
                val previewText = if (lastMsg.content.isNotBlank()) {
                    "$senderName: ${lastMsg.content}"
                } else {
                    "$senderName: [Anexo]"
                }
                tvPreview?.text = previewText

                // Count unread (messages from other that I haven't read)
                val unreadCount = messages.count { msg ->
                    msg.sender != currentUser &&
                    msg.read_by?.any { it.reader_name == currentUser } != true
                }

                if (unreadCount > 0) {
                    tvTitle?.text = "Mensagens ($unreadCount novas)"
                    tvUnreadBadge?.text = unreadCount.toString()
                    tvUnreadBadge?.visibility = View.VISIBLE
                } else {
                    tvTitle?.text = "Mensagens"
                    tvUnreadBadge?.visibility = View.GONE
                }

                // Click to go to chat
                cardMessages.setOnClickListener {
                    try { findNavController().navigate(R.id.chatFragment) } catch (_: Exception) {}
                }
            } catch (_: Exception) {
                cardMessages.visibility = View.GONE
            }
        }
    }

    // ── Item 21: Badge de notificações não lidas ─────────

    private fun loadUnreadNotificationsCount(view: View, token: String) {
        val badge = view.findViewById<TextView>(R.id.tvNotificationBadge) ?: return

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.api.getUnreadNotificationsCount("Bearer $token")
                val count = (response["unread_count"] as? Number)?.toInt() ?: 0

                if (count > 0) {
                    badge.text = if (count > 99) "99+" else count.toString()
                    badge.visibility = View.VISIBLE
                } else {
                    badge.visibility = View.GONE
                }
            } catch (_: Exception) {
                badge.visibility = View.GONE
            }
        }
    }

    // ── Item 7: Guarda dinâmica ─────────────────────────────

    private fun updateCustodyCard(view: View, events: List<Event>, now: Date) {
        val tvCustodyStatus = view.findViewById<TextView>(R.id.tvCustodyStatus)
        val tvNextSwapDate = view.findViewById<TextView>(R.id.tvNextSwapDate)

        val custodyEvents = events.filter { it.event_type.uppercase() == "CUSTODY" }

        if (custodyEvents.isEmpty()) {
            tvCustodyStatus?.text = "Sem guarda configurada"
            tvNextSwapDate?.text = "Adicione eventos de convivência"
            return
        }

        // Encontrar evento de custódia ativo (agora está entre start e end)
        var currentCustody: Event? = null
        for (event in custodyEvents) {
            val start = parseEventDate(event.event_date) ?: continue
            val end = if (!event.event_date_end.isNullOrEmpty()) {
                parseEventDate(event.event_date_end)
            } else {
                // Se não tem fim, assume 24h
                Calendar.getInstance().apply { time = start; add(Calendar.DAY_OF_MONTH, 1) }.time
            }

            if (end != null && now.after(start) && now.before(end)) {
                currentCustody = event
                break
            }
        }

        val username = PrefsHelper.getUsername(requireContext())
        val otherParentName = PrefsHelper.getOtherParentName(requireContext())
            .replaceFirstChar { it.uppercase() }.ifEmpty { "Outro pai" }

        if (currentCustody != null) {
            val isWithMe = currentCustody.created_by_name == username
            tvCustodyStatus?.text = if (isWithMe) "Com Você" else "Com $otherParentName"
        } else {
            tvCustodyStatus?.text = "Sem guarda ativa"
        }

        // Próxima troca: próximo evento de custódia futuro
        val nextCustody = custodyEvents
            .mapNotNull { event -> parseEventDate(event.event_date)?.let { date -> event to date } }
            .filter { it.second.after(now) }
            .minByOrNull { it.second }

        if (nextCustody != null) {
            val dateFormat = SimpleDateFormat("EEEE, dd 'de' MMM.", Locale("pt", "BR"))
            tvNextSwapDate?.text = dateFormat.format(nextCustody.second)
                .replaceFirstChar { it.uppercase() }
        } else {
            tvNextSwapDate?.text = "Sem troca agendada"
        }
    }

    private fun parseEventDate(dateStr: String): Date? {
        val formats = listOf(
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS", Locale.getDefault()),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.getDefault()),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault()),
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        )
        for (fmt in formats) {
            try {
                val result = fmt.parse(dateStr)
                if (result != null) return result
            } catch (_: Exception) {}
        }
        return null
    }

    // ── Event Row (mesmo padrão visual) ─────────────────────

    private fun createEmptyEventsCard(): View {
        val ctx = requireContext()
        val dp = { value: Int -> (value * ctx.resources.displayMetrics.density).toInt() }

        val card = com.google.android.material.card.MaterialCardView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            radius = dp(16).toFloat()
            cardElevation = 0f
            strokeColor = ContextCompat.getColor(ctx, R.color.gray_200)
            strokeWidth = dp(1)
            setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.white))
        }

        val innerLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }

        val tvTitle = TextView(ctx).apply {
            text = "Nenhum evento agendado"
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(ctx, R.color.gray_700))
            setPadding(0, 0, 0, dp(4))
            gravity = android.view.Gravity.CENTER
        }
        val tvSub = TextView(ctx).apply {
            text = "Agenda livre. Que dia tranquilo."
            textSize = 13f
            setTextColor(ContextCompat.getColor(ctx, R.color.gray_400))
            gravity = android.view.Gravity.CENTER
        }
        innerLayout.addView(tvTitle)
        innerLayout.addView(tvSub)
        card.addView(innerLayout)
        return card
    }

    private fun createEventRow(title: String, subtitle: String, eventType: String = ""): View {
        val ctx = requireContext()
        val dp = { value: Int -> (value * ctx.resources.displayMetrics.density).toInt() }

        val card = com.google.android.material.card.MaterialCardView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
            radius = dp(16).toFloat()
            cardElevation = 0f
            strokeColor = ContextCompat.getColor(ctx, R.color.gray_200)
            strokeWidth = dp(1)
            setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.white))
        }

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val iconSize = dp(40)
        val iconFrame = android.widget.FrameLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply { marginEnd = dp(14) }
            background = ContextCompat.getDrawable(ctx, R.drawable.bg_icon_circle_blue)
        }

        val iconRes = when (eventType.uppercase()) {
            "SCHOOL" -> R.drawable.ic_school
            "MEDICAL" -> R.drawable.ic_health
            "CUSTODY" -> R.drawable.ic_custody
            else -> R.drawable.ic_other
        }

        val icon = android.widget.ImageView(ctx).apply {
            val size = dp(20)
            layoutParams = android.widget.FrameLayout.LayoutParams(size, size).apply {
                gravity = android.view.Gravity.CENTER
            }
            setImageResource(iconRes)
            setColorFilter(ContextCompat.getColor(ctx, R.color.primary_blue))
        }
        iconFrame.addView(icon)
        row.addView(iconFrame)

        val textContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        textContainer.addView(TextView(ctx).apply {
            text = title
            textSize = 15f
            setTextColor(ContextCompat.getColor(ctx, R.color.gray_900))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        textContainer.addView(TextView(ctx).apply {
            text = subtitle
            textSize = 13f
            setTextColor(ContextCompat.getColor(ctx, R.color.gray_500))
        })

        row.addView(textContainer)
        card.addView(row)
        return card
    }
}
