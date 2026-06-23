package com.example.chatapp.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.chatapp.Child
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
    private val apiDateKeyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)

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
            val dayMonthFormat = SimpleDateFormat("dd 'de' MMMM", Locale.forLanguageTag("pt-BR"))
            dayMonthFormat.dateFormatSymbols = dayMonthFormat.dateFormatSymbols.apply {
                months = months.map { month ->
                    month.replaceFirstChar { it.titlecase(Locale.forLanguageTag("pt-BR")) }
                }.toTypedArray()
            }
            val dateKey = eventDateKey(eventDate)
            val time = eventTimeText(eventDate)

            val date = if (dateKey != null) {
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateKey)
            } else {
                parseEventDate(eventDate)
            }

            if (date != null && eventDate.contains("T")) {
                "${dayMonthFormat.format(date)} · ${time ?: "Dia inteiro"}"
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
        val dateFormat = SimpleDateFormat("EEEE, d 'de' MMMM", Locale.forLanguageTag("pt-BR"))
        view.findViewById<TextView>(R.id.tvDate)?.text = dateFormat.format(Date())

        // Saudação dinâmica
        view.findViewById<TextView>(R.id.tvGreeting)?.text =
            getString(R.string.home_greeting, getGreetingByTime(), displayName)
        renderMessageTarget(view, PrefsHelper.getOtherParentName(requireContext()))

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
            val destination = if (PrefsHelper.getOtherParentName(requireContext()).isBlank()) {
                R.id.profileFragment
            } else {
                R.id.chatFragment
            }
            try { findNavController().navigate(destination) } catch (_: Exception) {}
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

    override fun onResume() {
        super.onResume()
        val view = view ?: return
        val token = PrefsHelper.getAuthToken(requireContext())
        if (token.isNotEmpty()) loadAllData(view, token)
    }

    private fun loadAllData(view: View, token: String) {
        loadUpcomingEvents(view, token)
        loadUnreadNotificationsCount(view, token)
        loadMessageTarget(view, token)
    }

    private fun loadUpcomingEvents(view: View, token: String) {
        val container = view.findViewById<LinearLayout>(R.id.eventsContainer) ?: return
        val ctx = context ?: return

        lifecycleScope.launch {
            try {
                val conversationId = PrefsHelper.getConversationId(ctx)
                val children = RetrofitClient.api.getChildren("Bearer $token", conversationId)
                val events = RetrofitClient.api.getEvents("Bearer $token", conversationId)
                val now = Date()
                if (!isAdded || this@HomeFragment.view !== view) return@launch

                // --- Card de Guarda dinâmico ---
                updateCustodyCard(view, ctx, events, children, now)

                // --- Próximos eventos (hoje + futuros) ---
                val todayKey = calendarDateKey(Calendar.getInstance())

                val upcoming = events
                    .filter { event -> eventIsTodayOrFuture(event, todayKey) }
                    .sortedWith(compareBy<Event> { eventSortDateKey(it, todayKey) }
                        .thenBy { eventSortTimestamp(it) })
                    .take(3)

                container.removeAllViews()

                if (upcoming.isEmpty()) {
                    container.addView(createEmptyEventsCard(ctx))
                    return@launch
                }

                for (event in upcoming) {
                    val dateStr = formatEventDate(event.event_date)
                    container.addView(createEventRow(ctx, event.title, dateStr, event.event_type))
                }
            } catch (_: Exception) {
                if (!isAdded || this@HomeFragment.view !== view) return@launch
                showCustodyErrorState(view)
                container.removeAllViews()
                container.addView(createEventsErrorCard(ctx, view, token))
            } finally {
                if (!isAdded || this@HomeFragment.view !== view) return@launch
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

    private fun loadMessageTarget(view: View, token: String) {
        val ctx = context ?: return
        lifecycleScope.launch {
            try {
                val conversationId = PrefsHelper.getConversationId(ctx)
                val conversation = RetrofitClient.api.getConversations("Bearer $token")
                    .firstOrNull { it.id == conversationId }
                    ?: return@launch
                val otherParent = conversation.participants.firstOrNull { !it.is_me }?.username.orEmpty()
                if (!isAdded || this@HomeFragment.view !== view) return@launch
                PrefsHelper.saveOtherParentName(ctx, otherParent)
                renderMessageTarget(view, otherParent)
            } catch (_: Exception) {
                // Keep the cached target while offline.
            }
        }
    }

    private fun renderMessageTarget(view: View, name: String) {
        val target = view.findViewById<TextView>(R.id.tvMessageTarget) ?: return
        target.text = if (name.isBlank()) {
            getString(R.string.home_invite_parent)
        } else {
            getString(R.string.home_message_to_parent, name.replaceFirstChar { it.uppercase() })
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

    private fun updateCustodyCard(view: View, ctx: Context, events: List<Event>, children: List<Child>, now: Date) {
        val tvCustodyHeaderLabel = view.findViewById<TextView>(R.id.tvCustodyHeaderLabel)
        val custodyLegalBadge = view.findViewById<View>(R.id.custodyLegalBadge)
        val tvCustodyStatus = view.findViewById<TextView>(R.id.tvCustodyStatus)
        val tvNextSwapLabel = view.findViewById<TextView>(R.id.tvNextSwapLabel)
        val tvNextSwapDate = view.findViewById<TextView>(R.id.tvNextSwapDate)

        if (children.isEmpty()) {
            tvCustodyHeaderLabel?.setText(R.string.home_children_label)
            custodyLegalBadge?.visibility = View.GONE
            tvCustodyStatus?.setText(R.string.children_empty)
            tvNextSwapLabel?.setText(R.string.home_next_step_label)
            tvNextSwapDate?.setText(R.string.home_no_children_action)
            return
        }

        tvCustodyHeaderLabel?.setText(R.string.ui_guarda_atual)
        tvNextSwapLabel?.setText(R.string.ui_proxima_troca)

        val custodyEvents = events.filter { it.event_type.uppercase() == "CUSTODY" }

        if (custodyEvents.isEmpty()) {
            custodyLegalBadge?.visibility = View.GONE
            tvCustodyStatus?.setText(R.string.home_no_custody_configured)
            tvNextSwapLabel?.setText(R.string.home_next_step_label)
            tvNextSwapDate?.setText(R.string.home_add_custody_events)
            return
        }

        custodyLegalBadge?.visibility = View.VISIBLE

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

            if (end != null && !now.before(start) && now.before(end)) {
                currentCustody = event
                break
            }
        }

        val username = PrefsHelper.getUsername(ctx)
        val otherParentName = PrefsHelper.getOtherParentName(ctx)
            .replaceFirstChar { it.uppercase() }.ifEmpty { ctx.getString(R.string.home_other_parent) }

        if (currentCustody != null) {
            val isWithMe = currentCustody.created_by_name == username
            tvCustodyStatus?.text = if (isWithMe) {
                ctx.getString(R.string.home_with_you)
            } else {
                ctx.getString(R.string.home_with_parent, otherParentName)
            }
        } else {
            tvCustodyStatus?.setText(R.string.home_no_active_custody)
        }

        // Próxima troca: próximo evento de custódia futuro
        val nextCustody = custodyEvents
            .mapNotNull { event -> parseEventDate(event.event_date)?.let { date -> event to date } }
            .filter { it.second.after(now) }
            .minByOrNull { it.second }

        if (nextCustody != null) {
            val dateFormat = SimpleDateFormat("EEEE, dd 'de' MMMM", Locale.forLanguageTag("pt-BR"))
            tvNextSwapDate?.text = dateFormat.format(nextCustody.second)
                .replaceFirstChar { it.uppercase() }
        } else {
            tvNextSwapDate?.setText(R.string.home_no_scheduled_exchange)
        }
    }

    private fun showCustodyErrorState(view: View) {
        view.findViewById<TextView>(R.id.tvCustodyHeaderLabel)?.setText(R.string.ui_guarda_atual)
        view.findViewById<View>(R.id.custodyLegalBadge)?.visibility = View.GONE
        view.findViewById<TextView>(R.id.tvCustodyStatus)?.setText(R.string.home_custody_error_title)
        view.findViewById<TextView>(R.id.tvNextSwapLabel)?.setText(R.string.home_next_step_label)
        view.findViewById<TextView>(R.id.tvNextSwapDate)?.setText(R.string.home_custody_error_action)
    }

    private fun parseEventDate(dateStr: String): Date? {
        val formats = listOf(
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSX", Locale.getDefault()),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX", Locale.getDefault()),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX", Locale.getDefault()),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mmX", Locale.getDefault()),
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

    private fun eventDateKey(value: String?): String? {
        if (value.isNullOrBlank()) return null
        val rawKey = Regex("""^\d{4}-\d{2}-\d{2}""").find(value)?.value
        if (rawKey != null) return rawKey
        return parseEventDate(value)?.let { apiDateKeyFormat.format(it) }
    }

    private fun eventTimeText(value: String): String? {
        val timeStart = value.indexOf('T') + 1
        if (timeStart <= 0 || value.length < timeStart + 5) return null
        val time = value.substring(timeStart, timeStart + 5)
        return if (time.matches(Regex("""\d{2}:\d{2}"""))) time else null
    }

    private fun eventIsTodayOrFuture(event: Event, todayKey: String): Boolean {
        val startKey = eventDateKey(event.event_date)
        val endKey = eventEndDateKey(event, startKey)
        if (endKey != null) return endKey >= todayKey

        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
        val start = parseEventDate(event.event_date) ?: return false
        val end = event.event_date_end
            ?.takeIf { it.isNotBlank() }
            ?.let { parseEventDate(it) }
            ?: start
        val effectiveEnd = if (end.before(start)) start else end
        return !effectiveEnd.before(todayStart)
    }

    private fun eventEndDateKey(event: Event, startKey: String?): String? {
        val endKey = eventDateKey(event.event_date_end) ?: startKey
        return when {
            endKey == null -> null
            startKey != null && endKey < startKey -> startKey
            else -> endKey
        }
    }

    private fun calendarDateKey(calendar: Calendar): String =
        apiDateKeyFormat.format(calendar.time)

    private fun eventSortDateKey(event: Event, todayKey: String): String =
        eventDateKey(event.event_date)
            ?.takeIf { it >= todayKey }
            ?: todayKey

    private fun eventSortTimestamp(event: Event): Long {
        val start = parseEventDate(event.event_date) ?: return Long.MAX_VALUE
        return start.time
    }

    private fun createEmptyEventsCard(ctx: Context): View {
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
            text = ctx.getString(R.string.ui_nenhum_evento_agendado)
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(ctx, R.color.gray_700))
            setPadding(0, 0, 0, dp(4))
            gravity = android.view.Gravity.CENTER
        }
        val tvSub = TextView(ctx).apply {
            text = ctx.getString(R.string.home_empty_events_subtitle)
            textSize = 13f
            setTextColor(ContextCompat.getColor(ctx, R.color.gray_400))
            gravity = android.view.Gravity.CENTER
        }
        innerLayout.addView(tvTitle)
        innerLayout.addView(tvSub)
        card.addView(innerLayout)
        return card
    }

    private fun createEventsErrorCard(ctx: Context, view: View, token: String): View {
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

        val content = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }
        val title = TextView(ctx).apply {
            setText(R.string.state_error_title)
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(ctx, R.color.gray_700))
            gravity = android.view.Gravity.CENTER
        }
        val message = TextView(ctx).apply {
            setText(R.string.home_events_error_message)
            textSize = 13f
            setTextColor(ContextCompat.getColor(ctx, R.color.gray_500))
            gravity = android.view.Gravity.CENTER
            setPadding(0, dp(8), 0, dp(12))
        }
        val retry = com.google.android.material.button.MaterialButton(
            ctx,
            null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            setText(R.string.action_try_again)
            setOnClickListener { loadUpcomingEvents(view, token) }
        }
        content.addView(title)
        content.addView(message)
        content.addView(retry)
        card.addView(content)
        return card
    }

    private fun createEventRow(ctx: Context, title: String, subtitle: String, eventType: String = ""): View {
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
