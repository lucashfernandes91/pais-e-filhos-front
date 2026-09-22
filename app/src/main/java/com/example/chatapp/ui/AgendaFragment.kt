package com.example.chatapp.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.chatapp.ApiService
import com.example.chatapp.AppEventType
import com.example.chatapp.CreateEventRequest
import com.example.chatapp.Event
import com.example.chatapp.EventsAdapter
import com.example.chatapp.PrefsHelper
import com.example.chatapp.dpToPx
import com.example.chatapp.R
import com.example.chatapp.RetrofitClient
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

class AgendaFragment : Fragment() {

    private var btnAddEvent: MaterialButton? = null
    private var eventsRecyclerView: RecyclerView? = null
    private var swipeRefresh: SwipeRefreshLayout? = null
    private var emptyState: View? = null
    private var errorState: View? = null
    private var progressLoading: View? = null
    private var btnRetry: MaterialButton? = null
    private var apiService: ApiService? = null
    private var conversationId: Int = PrefsHelper.NO_CONVERSATION_ID
    private var token: String = ""
    private var currentUsername: String = ""

    // All events (for calendar markers)
    private var allEvents = mutableListOf<Event>()
    // Filtered events (for the list — depends on displayed month)
    private var events = mutableListOf<Event>()
    private var eventsAdapter: EventsAdapter? = null

    // Calendar
    private var calendarGrid: LinearLayout? = null
    private var tvCalendarMonth: TextView? = null
    private var tvCurrentMonth: TextView? = null
    private var tvLegendOtherParent: TextView? = null
    private var displayedCalendar = Calendar.getInstance()

    // Calendar data maps — day -> custody owner
    private enum class CustodyOwner { MOTHER, FATHER, NONE }

    private var custodyDays = mutableMapOf<Int, CustodyOwner>()
    private var genericEventDays = mutableSetOf<Int>()

    private val ptBrLocale = Locale.forLanguageTag("pt-BR")
    private val apiDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", ptBrLocale)
    private val displayDateFormat = SimpleDateFormat("dd/MM/yyyy", ptBrLocale)
    private val displayTimeFormat = SimpleDateFormat("HH:mm", ptBrLocale)

    private val dateFormats = listOf(
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS", Locale.getDefault()),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault()),
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_agenda, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        try {
            apiService = RetrofitClient.api
            token = PrefsHelper.getAuthToken(requireContext())
            currentUsername = PrefsHelper.getUsername(requireContext())
            conversationId = arguments?.getInt("conversationId")
                ?: PrefsHelper.getConversationId(requireContext())

            btnAddEvent = view.findViewById(R.id.btnAddEvent)
            eventsRecyclerView = view.findViewById(R.id.eventsRecyclerView)
            swipeRefresh = view.findViewById(R.id.swipeRefresh)
            emptyState = view.findViewById(R.id.emptyStateAgenda)
            errorState = view.findViewById(R.id.errorStateAgenda)
            progressLoading = view.findViewById(R.id.progressLoadingAgenda)
            btnRetry = view.findViewById(R.id.btnRetryAgenda)
            calendarGrid = view.findViewById(R.id.calendarGrid)
            tvCalendarMonth = view.findViewById(R.id.tvCalendarMonth)
            tvCurrentMonth = view.findViewById(R.id.tvCurrentMonth)
            tvLegendOtherParent = view.findViewById(R.id.tvLegendMother)

            setupRecyclerView()
            setupCalendarNavigation(view)
            setupSwipeRefresh()
            loadOtherParentLegend()

            btnAddEvent?.setOnClickListener { showAddEventDialogFixed() }
            btnRetry?.setOnClickListener { loadEvents() }

            loadEvents()
        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                getString(R.string.agenda_init_error, e.message.orEmpty()),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun setupRecyclerView() {
        eventsAdapter = EventsAdapter(
            events,
            onDeleteClick = { event -> confirmDeleteEvent(event) },
            onEditClick = { event -> showDayEventsSheetForEvent(event) }
        )
        eventsRecyclerView?.layoutManager = LinearLayoutManager(requireContext())
        eventsRecyclerView?.adapter = eventsAdapter
    }

    private fun loadOtherParentLegend() {
        updateOtherParentLegend(PrefsHelper.getOtherParentName(requireContext()))

        if (token.isEmpty() || apiService == null) return
        lifecycleScope.launch {
            try {
                val conversations = apiService!!.getConversations("Bearer $token")
                val conversation = conversations.firstOrNull { it.id == conversationId }
                    ?: conversations.firstOrNull()
                    ?: return@launch
                val otherParentName = conversation.participants
                    .firstOrNull { !it.is_me }
                    ?.username
                    .orEmpty()

                if (otherParentName.isNotBlank()) {
                    PrefsHelper.saveOtherParentName(requireContext(), otherParentName)
                    updateOtherParentLegend(otherParentName)
                }
            } catch (_: Exception) {
                // Mantém o nome em cache quando estiver offline ou a conversa não carregar.
            }
        }
    }

    private fun updateOtherParentLegend(name: String) {
        tvLegendOtherParent?.text = name.trim()
            .takeIf { it.isNotBlank() }
            ?.replaceFirstChar { it.uppercase() }
            ?: getString(R.string.agenda_no_other_parent)
    }

    private fun setupSwipeRefresh() {
        swipeRefresh?.setColorSchemeResources(R.color.primary_blue)
        swipeRefresh?.setOnRefreshListener { loadEvents() }
    }

    private fun setupCalendarNavigation(view: View) {
        view.findViewById<ImageButton>(R.id.btnPrevMonth)?.setOnClickListener {
            displayedCalendar.add(Calendar.MONTH, -1)
            updateCalendarData()
            renderCalendar()
            filterEventsForDisplayedMonth()
        }
        view.findViewById<ImageButton>(R.id.btnNextMonth)?.setOnClickListener {
            displayedCalendar.add(Calendar.MONTH, 1)
            updateCalendarData()
            renderCalendar()
            filterEventsForDisplayedMonth()
        }
        renderCalendar()
    }

    // ── Event List Filtering ────────────────────────────────

    private fun filterEventsForDisplayedMonth() {
        val today = Calendar.getInstance()
        val isCurrentMonth = today.get(Calendar.YEAR) == displayedCalendar.get(Calendar.YEAR) &&
                today.get(Calendar.MONTH) == displayedCalendar.get(Calendar.MONTH)
        val oldSize = events.size

        events.clear()

        if (isCurrentMonth) {
            val todayStart = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.time

            events.addAll(
                allEvents.filter {
                    val d = parseEventDate(it.event_date)
                    d != null && !d.before(todayStart)
                }.sortedBy { it.event_date }
            )
        } else {
            val displayedYear = displayedCalendar.get(Calendar.YEAR)
            val displayedMonth = displayedCalendar.get(Calendar.MONTH)

            events.addAll(
                allEvents.filter { event ->
                    val d = parseEventDate(event.event_date) ?: return@filter false
                    val evtCal = Calendar.getInstance().apply { time = d }

                    if (AppEventType.fromRaw(event.event_type).isCustody && !event.event_date_end.isNullOrEmpty()) {
                        val endDate = parseEventDate(event.event_date_end) ?: d
                        val startCal = Calendar.getInstance().apply { time = d }
                        val endCal = Calendar.getInstance().apply { time = endDate }

                        val monthStart = Calendar.getInstance().apply {
                            set(displayedYear, displayedMonth, 1, 0, 0, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        val monthEnd = Calendar.getInstance().apply {
                            set(displayedYear, displayedMonth, getActualMaximum(Calendar.DAY_OF_MONTH), 23, 59, 59)
                        }

                        startCal.before(monthEnd) && endCal.after(monthStart)
                    } else {
                        evtCal.get(Calendar.YEAR) == displayedYear &&
                                evtCal.get(Calendar.MONTH) == displayedMonth
                    }
                }.sortedBy { it.event_date }
            )
        }

        eventsAdapter?.let { adapter ->
            val newSize = events.size
            when {
                oldSize == 0 && newSize > 0 -> adapter.notifyItemRangeInserted(0, newSize)
                newSize == 0 && oldSize > 0 -> adapter.notifyItemRangeRemoved(0, oldSize)
                oldSize == newSize && newSize > 0 -> adapter.notifyItemRangeChanged(0, newSize)
                oldSize < newSize -> {
                    if (oldSize > 0) adapter.notifyItemRangeChanged(0, oldSize)
                    adapter.notifyItemRangeInserted(oldSize, newSize - oldSize)
                }
                oldSize > newSize -> {
                    if (newSize > 0) adapter.notifyItemRangeChanged(0, newSize)
                    adapter.notifyItemRangeRemoved(newSize, oldSize - newSize)
                }
            }
        }
        updateEmptyState()
    }

    // ── Calendar Data Processing ────────────────────────────

    private fun updateCalendarData() {
        custodyDays.clear()
        genericEventDays.clear()

        val currentYear = displayedCalendar.get(Calendar.YEAR)
        val currentMonth = displayedCalendar.get(Calendar.MONTH)

        for (event in allEvents) {
            if (AppEventType.fromRaw(event.event_type).isCustody) {
                processCustodyEvent(event, currentYear, currentMonth)
            } else {
                processGenericEvent(event, currentYear, currentMonth)
            }
        }
    }

    private fun processCustodyEvent(event: Event, year: Int, month: Int) {
        val startDate = parseEventDate(event.event_date) ?: return
        val endDate = if (!event.event_date_end.isNullOrEmpty()) {
            parseEventDate(event.event_date_end)
        } else {
            null
        }

        val owner = resolveCustodyOwner(event)

        val startCal = Calendar.getInstance().apply { time = startDate }
        val endCal = if (endDate != null) {
            Calendar.getInstance().apply { time = endDate }
        } else {
            Calendar.getInstance().apply { time = startDate }
        }

        val iterCal = startCal.clone() as Calendar
        while (!iterCal.after(endCal)) {
            if (iterCal.get(Calendar.YEAR) == year && iterCal.get(Calendar.MONTH) == month) {
                val day = iterCal.get(Calendar.DAY_OF_MONTH)
                custodyDays[day] = owner
            }
            iterCal.add(Calendar.DAY_OF_MONTH, 1)
        }
    }

    private fun resolveCustodyOwner(event: Event): CustodyOwner {
        val createdBy = event.created_by_name.trim()
        if (createdBy.isBlank()) {
            return CustodyOwner.NONE
        }

        // Backend ainda não expõe o owner de custódia como campo de domínio dedicado.
        return if (createdBy == currentUsername) CustodyOwner.FATHER else CustodyOwner.MOTHER
    }

    private fun processGenericEvent(event: Event, year: Int, month: Int) {
        val date = parseEventDate(event.event_date) ?: return
        val eventCal = Calendar.getInstance().apply { time = date }

        if (eventCal.get(Calendar.YEAR) == year && eventCal.get(Calendar.MONTH) == month) {
            genericEventDays.add(eventCal.get(Calendar.DAY_OF_MONTH))
        }
    }

    // ── Calendar Rendering ──────────────────────────────────

    private fun renderCalendar() {
        val grid = calendarGrid ?: return
        grid.removeAllViews()

        val monthFormat = SimpleDateFormat("MMMM 'de' yyyy", ptBrLocale)
        val monthText = monthFormat.format(displayedCalendar.time)
            .replaceFirstChar { it.uppercase() }
        tvCalendarMonth?.text = monthText
        tvCurrentMonth?.text = monthText

        val cal = displayedCalendar.clone() as Calendar
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

        val today = Calendar.getInstance()
        val isCurrentMonth = today.get(Calendar.YEAR) == displayedCalendar.get(Calendar.YEAR) &&
                today.get(Calendar.MONTH) == displayedCalendar.get(Calendar.MONTH)
        val todayDay = today.get(Calendar.DAY_OF_MONTH)

        val ctx = requireContext()
        val density = ctx.resources.displayMetrics.density
        val dp = { value: Int -> (value * density).toInt() }

        var dayCounter = 1
        val totalCells = firstDayOfWeek - 1 + daysInMonth
        val totalRows = (totalCells + 6) / 7

        // Tamanhos dos círculos — menores para que o dot laranja fique abaixo deles
        val todayCircleSizePx = (22f * 1.05f * density).roundToInt()
        val custodyBgWidthDp = 32
        val custodyBgHeightDp = 32
        val dotSizeDp    = 5
        val dotBottomMarginDp = 5  // margem do dot em relação à borda inferior da célula

        for (row in 0 until totalRows) {
            val rowLayout = LinearLayout(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(48)
                )
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }

            for (col in 0 until 7) {
                val cellIndex = row * 7 + col

                // Célula: ocupa 1/7 da largura, altura total da row (48dp)
                val frame = FrameLayout(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f)
                }

                if (cellIndex >= firstDayOfWeek - 1 && dayCounter <= daysInMonth) {
                    val day = dayCounter
                    val isToday = isCurrentMonth && day == todayDay
                    val custodyOwner = custodyDays[day] ?: CustodyOwner.NONE
                    val hasGenericEvent = genericEventDays.contains(day)
                    val hasCustody = custodyOwner != CustodyOwner.NONE

                    if (hasGenericEvent || hasCustody) {
                        frame.isClickable = true
                        frame.isFocusable = true
                        frame.setOnClickListener {
                            val clickedCal = displayedCalendar.clone() as Calendar
                            clickedCal.set(Calendar.DAY_OF_MONTH, day)
                            showDayEventsSheet(day, clickedCal)
                        }
                    }

                    // ── Fundo de custódia (retângulo arredondado) ──────────
                    // Fica na metade superior da célula para não colidir com o dot
                    if (custodyOwner != CustodyOwner.NONE) {
                        val bgColor = when (custodyOwner) {
                            CustodyOwner.MOTHER -> R.color.custody_mother_bg
                            CustodyOwner.FATHER -> R.color.custody_father_bg
                            else -> 0
                        }
                        if (bgColor != 0) {
                            val bgView = View(ctx).apply {
                                layoutParams = FrameLayout.LayoutParams(
                                    dp(custodyBgWidthDp),
                                    dp(custodyBgHeightDp)
                                ).apply {
                                    // Centralizado horizontalmente, alinhado ao topo da célula
                                    // com uma margem para não ficar colado na borda
                                    gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
                                    topMargin = dp(4)

                                }
                                background = GradientDrawable().apply {
                                    shape = GradientDrawable.RECTANGLE
                                    cornerRadius = dp(12).toFloat()
                                    setColor(ContextCompat.getColor(ctx, bgColor))
                                }
                            }
                            frame.addView(bgView)
                        }
                    }

                    // ── Círculo azul "hoje" ────────────────────────────────
                    // Mesmo posicionamento: topo + margem, para manter simetria com custódia
                    if (isToday) {
                        val circle = View(ctx).apply {
                            layoutParams = FrameLayout.LayoutParams(
                                todayCircleSizePx,
                                todayCircleSizePx
                            ).apply {
                                gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
                                topMargin = dp(9)
                            }
                            background = ContextCompat.getDrawable(ctx, R.drawable.bg_today_circle)
                        }
                        frame.addView(circle)
                    }

                    // ── Número do dia ──────────────────────────────────────
                    // Alinhado ao topo junto com o círculo, centralizado horizontalmente
                    val textColor = when {
                        isToday -> R.color.on_primary
                        else -> R.color.gray_700
                    }

                    val tv = TextView(ctx).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            todayCircleSizePx,
                            todayCircleSizePx
                        ).apply {
                            gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
                            topMargin = dp(9)
                        }
                        text = day.toString()
                        gravity = Gravity.CENTER
                        textSize = 13f
                        setTextColor(ContextCompat.getColor(ctx, textColor))
                        if (isToday) {
                            setTypeface(typeface, android.graphics.Typeface.BOLD)
                        }
                    }
                    frame.addView(tv)

                    // ── Ponto laranja de evento ────────────────────────────
                    // Fixado na borda inferior da célula — sempre abaixo do círculo
                    if (hasGenericEvent) {
                        val dot = View(ctx).apply {
                            layoutParams = FrameLayout.LayoutParams(
                                dp(dotSizeDp),
                                dp(dotSizeDp)
                            ).apply {
                                gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
                                bottomMargin = dp(dotBottomMarginDp)
                            }
                            background = GradientDrawable().apply {
                                shape = GradientDrawable.OVAL
                                setColor(ContextCompat.getColor(ctx, R.color.event_dot))
                            }
                        }
                        frame.addView(dot)
                    }

                    dayCounter++
                }

                rowLayout.addView(frame)
            }

            grid.addView(rowLayout)
        }
    }

    // ── Day Events Bottom Sheet ────────────────────────────

    private fun showDayEventsSheet(day: Int, dayCal: Calendar) {
        val ctx = requireContext()
        val dialog = BottomSheetDialog(ctx, R.style.BottomSheetDialogTheme)
        val dialogRoot = requireActivity().findViewById<ViewGroup>(android.R.id.content)
        val sheetView = LayoutInflater.from(ctx).inflate(R.layout.bottom_sheet_day_events, dialogRoot, false)
        dialog.setContentView(sheetView)

        val dayNameFmt = SimpleDateFormat("EEEE", ptBrLocale)
        val dateFmt = SimpleDateFormat("d 'de' MMMM", ptBrLocale)
        sheetView.findViewById<TextView>(R.id.tvDaySheetDayName).text =
            dayNameFmt.format(dayCal.time).replaceFirstChar { it.uppercase() }
        sheetView.findViewById<TextView>(R.id.tvDaySheetDate).text =
            dateFmt.format(dayCal.time).replaceFirstChar { it.uppercase() }

        val currentYear = dayCal.get(Calendar.YEAR)
        val currentMonth = dayCal.get(Calendar.MONTH)

        val dayEvents = allEvents.filter { event ->
            val start = parseEventDate(event.event_date) ?: return@filter false
            val isCustody = AppEventType.fromRaw(event.event_type).isCustody

            if (isCustody) {
                val end = if (!event.event_date_end.isNullOrEmpty())
                    parseEventDate(event.event_date_end) else start
                val eventStart = startOfDay(start)
                val eventEnd = endOfDay(end ?: start)
                val selectedDayStart = startOfDay(dayCal.time)
                val selectedDayEnd = endOfDay(dayCal.time)
                !selectedDayEnd.before(eventStart) && !selectedDayStart.after(eventEnd)
            } else {
                val evtCal = Calendar.getInstance().apply { time = start }
                evtCal.get(Calendar.DAY_OF_MONTH) == day &&
                evtCal.get(Calendar.MONTH) == currentMonth &&
                evtCal.get(Calendar.YEAR) == currentYear
            }
        }

        val count = dayEvents.size
        sheetView.findViewById<TextView>(R.id.tvDaySheetCount).text =
            if (count == 1) "1 evento" else "$count eventos"

        val container = sheetView.findViewById<LinearLayout>(R.id.llDayEventsContainer)
        val dp = { value: Int -> ctx.dpToPx(value) }

        for (event in dayEvents) {
            val iconRes = eventIconRes(event)
            val typeLabel = eventTypeLabel(event)

            val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
            val startDate = parseEventDate(event.event_date)
            val endDate = if (!event.event_date_end.isNullOrEmpty()) parseEventDate(event.event_date_end) else null
            val timeStr = when {
                AppEventType.fromRaw(event.event_type).isCustody && endDate != null -> {
                    val endDayFmt = SimpleDateFormat("d MMM", ptBrLocale)
                    "At\u00e9 ${endDayFmt.format(endDate)}"
                }
                startDate != null -> timeFmt.format(startDate)
                else -> ""
            }

            val cardRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(12) }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(14).toFloat()
                    setColor(ContextCompat.getColor(ctx, R.color.gray_50))
                }
                setPadding(dp(14), dp(12), dp(14), dp(12))
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    dialog.dismiss()
                    showEventDetailSheet(event)
                }
            }

            val iconSize = dp(38)
            val iconFrame = FrameLayout(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                    marginEnd = dp(12)
                }
                background = ContextCompat.getDrawable(ctx, R.drawable.bg_icon_circle_blue)
            }
            val icon = android.widget.ImageView(ctx).apply {
                val size = dp(18)
                layoutParams = FrameLayout.LayoutParams(size, size).apply {
                    gravity = android.view.Gravity.CENTER
                }
                setImageResource(iconRes)
                setColorFilter(ContextCompat.getColor(ctx, R.color.primary_blue))
            }
            iconFrame.addView(icon)
            cardRow.addView(iconFrame)

            val textBlock = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            textBlock.addView(TextView(ctx).apply {
                text = event.title
                textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(ContextCompat.getColor(ctx, R.color.gray_900))
            })
            textBlock.addView(TextView(ctx).apply {
                text = if (timeStr.isNotEmpty()) "$typeLabel \u00b7 $timeStr" else typeLabel
                textSize = 12f
                setTextColor(ContextCompat.getColor(ctx, R.color.gray_500))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(2) }
            })
            cardRow.addView(textBlock)
            container.addView(cardRow)
        }

        dialog.show()
    }

    private fun showEventDetailSheet(event: Event) {
        val ctx = requireContext()
        val dialog = BottomSheetDialog(ctx, R.style.BottomSheetDialogTheme)
        val dp = { value: Int -> ctx.dpToPx(value) }

        val sheetView = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(24))
        }

        sheetView.addView(View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(4)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(18)
            }
            background = ContextCompat.getDrawable(ctx, R.drawable.bg_drag_handle)
        })

        val headerRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(18) }
        }

        val iconFrame = FrameLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply {
                marginEnd = dp(12)
            }
            background = ContextCompat.getDrawable(ctx, R.drawable.bg_icon_circle_blue)
        }
        iconFrame.addView(android.widget.ImageView(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(dp(22), dp(22)).apply {
                gravity = Gravity.CENTER
            }
            setImageResource(eventIconRes(event))
            setColorFilter(ContextCompat.getColor(ctx, R.color.primary_blue))
        })
        headerRow.addView(iconFrame)

        headerRow.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(ctx).apply {
                text = event.title
                textSize = 18f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(ContextCompat.getColor(ctx, R.color.gray_900))
            })
            addView(TextView(ctx).apply {
                text = eventTypeLabel(event)
                textSize = 13f
                setTextColor(ContextCompat.getColor(ctx, R.color.primary_blue))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(2) }
            })
        })
        sheetView.addView(headerRow)

        sheetView.addView(detailDivider(dp))
        sheetView.addView(detailRow(getString(R.string.event_detail_when), eventDateDetailText(event), dp))
        sheetView.addView(
            detailRow(
                getString(R.string.event_detail_created_by),
                event.created_by_name.ifBlank { getString(R.string.event_detail_not_informed) },
                dp
            )
        )

        if (event.notes.isNotBlank()) {
            sheetView.addView(detailRow(getString(R.string.event_detail_notes), event.notes, dp))
        }

        dialog.setContentView(sheetView)
        dialog.show()
    }

    private fun eventIconRes(event: Event): Int =
        AppEventType.fromRaw(event.event_type).iconRes

    private fun eventTypeLabel(event: Event): String =
        getString(AppEventType.fromRaw(event.event_type).labelRes)

    private fun eventDateDetailText(event: Event): String {
        val start = parseEventDate(event.event_date) ?: return event.event_date
        val dateTimeFmt = SimpleDateFormat(getString(R.string.event_detail_time_pattern), ptBrLocale)
        val dateOnlyFmt = SimpleDateFormat(getString(R.string.event_detail_date_pattern), ptBrLocale)
        val startText = dateTimeFmt.format(start).replaceFirstChar { it.uppercase() }
        val end = event.event_date_end?.takeIf { it.isNotBlank() }?.let { parseEventDate(it) }
            ?: return startText
        val endText = if (AppEventType.fromRaw(event.event_type).isCustody) {
            dateOnlyFmt.format(end).replaceFirstChar { it.uppercase() }
        } else {
            dateTimeFmt.format(end).replaceFirstChar { it.uppercase() }
        }
        return "$startText ${getString(R.string.event_detail_until)} $endText"
    }

    private fun detailDivider(dp: (Int) -> Int): View =
        View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1)
            ).apply { bottomMargin = dp(12) }
            setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.gray_200))
        }

    private fun detailRow(label: String, value: String, dp: (Int) -> Int): View {
        val ctx = requireContext()
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(14) }
            addView(TextView(ctx).apply {
                text = label.uppercase(ptBrLocale)
                textSize = 12f
                letterSpacing = 0.06f
                setTextColor(ContextCompat.getColor(ctx, R.color.gray_500))
            })
            addView(TextView(ctx).apply {
                text = value
                textSize = 15f
                setTextColor(ContextCompat.getColor(ctx, R.color.gray_900))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(4) }
            })
        }
    }

    private fun showDayEventsSheetForEvent(event: Event) {
        val eventDate = parseEventDate(event.event_date)
        if (eventDate == null) {
            Toast.makeText(requireContext(), getString(R.string.event_open_error), Toast.LENGTH_SHORT).show()
            return
        }
        val eventCal = Calendar.getInstance().apply { time = eventDate }
        showDayEventsSheet(eventCal.get(Calendar.DAY_OF_MONTH), eventCal)
    }

    private fun startOfDay(date: Date): Calendar =
        Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

    private fun endOfDay(date: Date): Calendar =
        Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }

    // ── Date Parsing ────────────────────────────────────────

    private fun parseEventDate(dateStr: String): Date? {
        for (fmt in dateFormats) {
            try {
                val result = fmt.parse(dateStr)
                if (result != null) return result
            } catch (_: Exception) {}
        }
        return null
    }

    // ── Data Loading ────────────────────────────────────────

    private fun loadEvents() {
        lifecycleScope.launch {
            showLoadingState()
            try {
                token = PrefsHelper.getAuthToken(requireContext())
                if (
                    token.isEmpty() ||
                    apiService == null ||
                    conversationId <= PrefsHelper.NO_CONVERSATION_ID
                ) {
                    showErrorState()
                    return@launch
                }
                val eventList = apiService!!.getEvents("Bearer $token", conversationId)

                allEvents.clear()
                allEvents.addAll(eventList)

                updateCalendarData()
                renderCalendar()
                filterEventsForDisplayedMonth()

            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.event_load_error, e.message.orEmpty()),
                    Toast.LENGTH_SHORT
                ).show()
                showErrorState()
            } finally {
                progressLoading?.visibility = View.GONE
                swipeRefresh?.isRefreshing = false
            }
        }
    }

    private fun showLoadingState() {
        errorState?.visibility = View.GONE
        emptyState?.visibility = View.GONE
        eventsRecyclerView?.visibility = View.GONE
        progressLoading?.visibility = View.VISIBLE
    }

    private fun showErrorState() {
        eventsRecyclerView?.visibility = View.GONE
        emptyState?.visibility = View.GONE
        errorState?.visibility = View.VISIBLE
    }

    private fun updateEmptyState() {
        errorState?.visibility = View.GONE
        progressLoading?.visibility = View.GONE
        if (events.isEmpty()) {
            eventsRecyclerView?.visibility = View.GONE
            emptyState?.visibility = View.VISIBLE
        } else {
            eventsRecyclerView?.visibility = View.VISIBLE
            emptyState?.visibility = View.GONE
        }
    }

    // ── Delete with confirmation ────────────────────────────

    private fun confirmDeleteEvent(event: Event) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.event_delete_title)
            .setMessage(getString(R.string.event_delete_message, event.title))
            .setPositiveButton(R.string.event_delete_confirm) { _, _ -> deleteEvent(event.id) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ── Add Event Dialog ────────────────────────────────────

    @Suppress("unused")
    private fun showAddEventDialog() {
        val dialog = BottomSheetDialog(requireContext(), R.style.BottomSheetDialogTheme)
        val dialogRoot = requireActivity().findViewById<ViewGroup>(android.R.id.content)
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_event, dialogRoot, false)
        dialog.setContentView(dialogView)

        val btnBack = dialogView.findViewById<ImageButton>(R.id.btnBack)
        val titleInput = dialogView.findViewById<TextInputEditText>(R.id.etEventTitle)
        val chipGroup = dialogView.findViewById<ChipGroup>(R.id.chipGroupType)
        val notesInput = dialogView.findViewById<TextInputEditText>(R.id.etEventNotes)
        val btnSave = dialogView.findViewById<MaterialButton>(R.id.btnSaveEvent)

        val layoutDateTimeSingle = dialogView.findViewById<LinearLayout>(R.id.layoutDateTimeSingle)
        val dateInput = dialogView.findViewById<TextInputEditText>(R.id.etEventDate)
        val timeInput = dialogView.findViewById<TextInputEditText>(R.id.etEventTime)

        val layoutDateTimeStart = dialogView.findViewById<LinearLayout>(R.id.layoutDateTimeStart)
        val layoutDateTimeEnd = dialogView.findViewById<LinearLayout>(R.id.layoutDateTimeEnd)
        val startDateInput = dialogView.findViewById<TextInputEditText>(R.id.etStartDate)
        val startTimeInput = dialogView.findViewById<TextInputEditText>(R.id.etStartTime)
        val endDateInput = dialogView.findViewById<TextInputEditText>(R.id.etEndDate)
        val endTimeInput = dialogView.findViewById<TextInputEditText>(R.id.etEndTime)

        val selectedCalendar = Calendar.getInstance()
        val startCalendar = Calendar.getInstance()
        val endCalendar = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, 1) }

        dateInput?.setText(displayDateFormat.format(selectedCalendar.time))
        timeInput?.setText(displayTimeFormat.format(selectedCalendar.time))
        startDateInput?.setText(displayDateFormat.format(startCalendar.time))
        startTimeInput?.setText(displayTimeFormat.format(startCalendar.time))
        endDateInput?.setText(displayDateFormat.format(endCalendar.time))
        endTimeInput?.setText(displayTimeFormat.format(endCalendar.time))

        btnBack.setOnClickListener { dialog.dismiss() }

        chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val isCustody = checkedIds.contains(R.id.chipCustody)
            if (isCustody) {
                layoutDateTimeSingle?.visibility = View.GONE
                layoutDateTimeStart?.visibility = View.VISIBLE
                layoutDateTimeEnd?.visibility = View.VISIBLE
                val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                bottomSheet?.let {
                    val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(it)
                    behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
                }
            } else {
                layoutDateTimeSingle?.visibility = View.VISIBLE
                layoutDateTimeStart?.visibility = View.GONE
                layoutDateTimeEnd?.visibility = View.GONE
            }
        }

        dateInput?.setOnClickListener {
            showDatePicker(selectedCalendar) { dateInput.setText(displayDateFormat.format(selectedCalendar.time)) }
        }
        timeInput?.setOnClickListener {
            showTimePicker(selectedCalendar) { timeInput.setText(displayTimeFormat.format(selectedCalendar.time)) }
        }
        startDateInput?.setOnClickListener {
            showDatePicker(startCalendar) { startDateInput.setText(displayDateFormat.format(startCalendar.time)) }
        }
        startTimeInput?.setOnClickListener {
            showTimePicker(startCalendar) { startTimeInput.setText(displayTimeFormat.format(startCalendar.time)) }
        }
        endDateInput?.setOnClickListener {
            showDatePicker(endCalendar) { endDateInput.setText(displayDateFormat.format(endCalendar.time)) }
        }
        endTimeInput?.setOnClickListener {
            showTimePicker(endCalendar) { endTimeInput.setText(displayTimeFormat.format(endCalendar.time)) }
        }

        dialogView.findViewById<Chip>(R.id.chipSchool)?.isChecked = true

        btnSave.setOnClickListener {
            val title = titleInput?.text?.toString()?.trim() ?: ""
            val notes = notesInput?.text?.toString()?.trim() ?: ""
            val type = AppEventType.fromRaw(eventTypeFromSegment(chipGroup.checkedChipId))

            if (title.isEmpty()) {
                Toast.makeText(requireContext(), R.string.event_error_title_required, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (type.isCustody) {
                if (endCalendar.before(startCalendar)) {
                    Toast.makeText(requireContext(), R.string.event_error_custody_range, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                createEvent(
                    title,
                    apiDateFormat.format(startCalendar.time),
                    type.rawValue,
                    notes,
                    apiDateFormat.format(endCalendar.time)
                )
            } else {
                createEvent(title, apiDateFormat.format(selectedCalendar.time), type.rawValue, notes, null)
            }
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showAddEventDialogFixed() {
        val dialog = BottomSheetDialog(requireContext(), R.style.AddEventBottomSheetTheme)
        val dialogRoot = requireActivity().findViewById<ViewGroup>(android.R.id.content)
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_event, dialogRoot, false)
        dialog.setContentView(dialogView)

        val btnBack = dialogView.findViewById<ImageButton>(R.id.btnBack)
        val btnSave = dialogView.findViewById<MaterialButton>(R.id.btnSaveEvent)
        val form = AddEventFormBinder(
            dialogView,
            displayDateFormat = { displayDateFormat.format(it.time) },
            displayTimeFormat = { displayTimeFormat.format(it.time) }
        )
        val layoutDateTimeSingle = dialogView.findViewById<LinearLayout>(R.id.layoutDateTimeSingle)
        val layoutDateTimeStart = dialogView.findViewById<LinearLayout>(R.id.layoutDateTimeStart)
        val layoutDateTimeEnd = dialogView.findViewById<LinearLayout>(R.id.layoutDateTimeEnd)

        val selectedCalendar = Calendar.getInstance()
        val startCalendar = Calendar.getInstance()
        val endCalendar = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, 1) }

        fun updateDateVisibility() {
            val isCustody = form.isCustodySelected()
            layoutDateTimeSingle?.visibility = if (isCustody) View.GONE else View.VISIBLE
            layoutDateTimeStart?.visibility = if (isCustody) View.VISIBLE else View.GONE
            layoutDateTimeEnd?.visibility = if (isCustody) View.VISIBLE else View.GONE
            fitBottomSheetToContent(dialog)
        }

        btnBack.setOnClickListener { dialog.dismiss() }
        setupEventFormPickers(form, selectedCalendar, startCalendar, endCalendar)
        form.setupTextFieldValidation()
        form.setupAccessibility(btnSave)
        form.setupSegmentGroup { updateDateVisibility() }
        form.segmentType.check(R.id.chipSchool)
        updateDateVisibility()

        btnSave.setOnClickListener {
            val isCustody = form.isCustodySelected()
            if (!form.validateAll(isCustody, startCalendar, endCalendar)) {
                form.rejectHaptic()
                return@setOnClickListener
            }

            val title = form.etTitle.text?.toString()?.trim().orEmpty()
            val notes = form.etNotes.text?.toString()?.trim().orEmpty()
            val type = AppEventType.fromRaw(eventTypeFromSegment(form.segmentType.checkedChipId))

            if (type.isCustody) {
                createEvent(
                    title,
                    apiDateFormat.format(startCalendar.time),
                    type.rawValue,
                    notes,
                    apiDateFormat.format(endCalendar.time)
                )
            } else {
                createEvent(title, apiDateFormat.format(selectedCalendar.time), type.rawValue, notes, null)
            }
            dialog.dismiss()
        }

        dialog.show()
        fitBottomSheetToContent(dialog)
    }

    private fun setupEventFormPickers(
        form: AddEventFormBinder,
        selectedCalendar: Calendar,
        startCalendar: Calendar,
        endCalendar: Calendar
    ) {
        form.setPickerValue(form.datePicker, selectedCalendar, isDate = true)
        form.setPickerValue(form.timePicker, selectedCalendar, isDate = false)
        form.setPickerValue(form.startDatePicker, startCalendar, isDate = true)
        form.setPickerValue(form.startTimePicker, startCalendar, isDate = false)
        form.setPickerValue(form.endDatePicker, endCalendar, isDate = true)
        form.setPickerValue(form.endTimePicker, endCalendar, isDate = false)

        form.setupPickerRow(form.datePicker) { done ->
            showDatePicker(selectedCalendar) {
                form.setPickerValue(form.datePicker, selectedCalendar, isDate = true)
                done()
            }
        }
        form.setupPickerRow(form.timePicker) { done ->
            showTimePicker(selectedCalendar) {
                form.setPickerValue(form.timePicker, selectedCalendar, isDate = false)
                done()
            }
        }
        form.setupPickerRow(form.startDatePicker) { done ->
            showDatePicker(startCalendar) {
                form.setPickerValue(form.startDatePicker, startCalendar, isDate = true)
                done()
            }
        }
        form.setupPickerRow(form.startTimePicker) { done ->
            showTimePicker(startCalendar) {
                form.setPickerValue(form.startTimePicker, startCalendar, isDate = false)
                done()
            }
        }
        form.setupPickerRow(form.endDatePicker) { done ->
            showDatePicker(endCalendar) {
                form.setPickerValue(form.endDatePicker, endCalendar, isDate = true)
                done()
            }
        }
        form.setupPickerRow(form.endTimePicker) { done ->
            showTimePicker(endCalendar) {
                form.setPickerValue(form.endTimePicker, endCalendar, isDate = false)
                done()
            }
        }
    }

    private fun eventTypeFromSegment(checkedChipId: Int): String =
        when (checkedChipId) {
            R.id.chipSchool -> AppEventType.SCHOOL.rawValue
            R.id.chipMedical -> AppEventType.MEDICAL.rawValue
            R.id.chipCustody -> AppEventType.CUSTODY.rawValue
            else -> AppEventType.OTHER.rawValue
        }

    private fun fitBottomSheetToContent(dialog: BottomSheetDialog) {
        val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?: return
        bottomSheet.layoutParams = bottomSheet.layoutParams.apply {
            height = ViewGroup.LayoutParams.WRAP_CONTENT
        }
        bottomSheet.requestLayout()
        BottomSheetBehavior.from(bottomSheet).apply {
            isFitToContents = true
            skipCollapsed = false
            peekHeight = BottomSheetBehavior.PEEK_HEIGHT_AUTO
            state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    private fun showDatePicker(calendar: Calendar, onDateSet: () -> Unit) {
        DatePickerDialog(requireContext(), { _, year, month, day ->
            calendar.set(Calendar.YEAR, year)
            calendar.set(Calendar.MONTH, month)
            calendar.set(Calendar.DAY_OF_MONTH, day)
            onDateSet()
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun showTimePicker(calendar: Calendar, onTimeSet: () -> Unit) {
        TimePickerDialog(requireContext(), { _, hour, minute ->
            calendar.set(Calendar.HOUR_OF_DAY, hour)
            calendar.set(Calendar.MINUTE, minute)
            onTimeSet()
        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
    }

    private fun createEvent(title: String, date: String, type: String, notes: String, dateEnd: String?) {
        lifecycleScope.launch {
            try {
                if (token.isEmpty() || apiService == null) {
                    Toast.makeText(requireContext(), R.string.event_token_missing, Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val eventBody = CreateEventRequest(
                    conversation_id = conversationId,
                    title = title,
                    event_date = date,
                    event_date_end = dateEnd,
                    event_type = type,
                    notes = notes
                )
                apiService!!.createEvent("Bearer $token", eventBody)
                Toast.makeText(requireContext(), R.string.event_created, Toast.LENGTH_SHORT).show()
                loadEvents()
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.event_create_error, e.message.orEmpty()),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun deleteEvent(eventId: Int) {
        lifecycleScope.launch {
            try {
                if (token.isEmpty() || apiService == null) return@launch
                apiService!!.deleteEvent("Bearer $token", eventId)
                Toast.makeText(requireContext(), R.string.event_deleted, Toast.LENGTH_SHORT).show()
                loadEvents()
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.event_delete_error, e.message.orEmpty()),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // â”€â”€ Edit Event â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Suppress("unused")
    private fun showEditEventDialog(event: Event) {
        val dialog = BottomSheetDialog(requireContext(), R.style.BottomSheetDialogTheme)
        val dialogRoot = requireActivity().findViewById<ViewGroup>(android.R.id.content)
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_event, dialogRoot, false)
        dialog.setContentView(dialogView)

        val btnBack = dialogView.findViewById<ImageButton>(R.id.btnBack)
        val titleInput = dialogView.findViewById<TextInputEditText>(R.id.etEventTitle)
        val chipGroup = dialogView.findViewById<ChipGroup>(R.id.chipGroupType)
        val notesInput = dialogView.findViewById<TextInputEditText>(R.id.etEventNotes)
        val btnSave = dialogView.findViewById<MaterialButton>(R.id.btnSaveEvent)
        val tvDialogTitle = dialogView.findViewById<TextView>(R.id.tvDialogTitle)

        val dateInput = dialogView.findViewById<TextInputEditText>(R.id.etEventDate)
        val timeInput = dialogView.findViewById<TextInputEditText>(R.id.etEventTime)

        tvDialogTitle?.setText(R.string.event_edit_title)
        btnSave.setText(R.string.action_save_changes)

        titleInput?.setText(event.title)
        notesInput?.setText(event.notes)

        when (event.event_type.uppercase()) {
            "SCHOOL" -> dialogView.findViewById<Chip>(R.id.chipSchool)?.isChecked = true
            "MEDICAL" -> dialogView.findViewById<Chip>(R.id.chipMedical)?.isChecked = true
            "CUSTODY" -> dialogView.findViewById<Chip>(R.id.chipCustody)?.isChecked = true
            else -> dialogView.findViewById<Chip>(R.id.chipOther)?.isChecked = true
        }

        val selectedCalendar = Calendar.getInstance()
        try {
            val parsed = apiDateFormat.parse(event.event_date)
            if (parsed != null) selectedCalendar.time = parsed
        } catch (_: Exception) {}

        dateInput?.setText(displayDateFormat.format(selectedCalendar.time))
        timeInput?.setText(displayTimeFormat.format(selectedCalendar.time))

        dateInput?.setOnClickListener {
            showDatePicker(selectedCalendar) { dateInput.setText(displayDateFormat.format(selectedCalendar.time)) }
        }
        timeInput?.setOnClickListener {
            showTimePicker(selectedCalendar) { timeInput.setText(displayTimeFormat.format(selectedCalendar.time)) }
        }

        btnBack.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            val title = titleInput?.text?.toString()?.trim() ?: ""
            val notes = notesInput?.text?.toString()?.trim() ?: ""
            val type = when (chipGroup.checkedChipId) {
                R.id.chipSchool -> "SCHOOL"
                R.id.chipMedical -> "MEDICAL"
                R.id.chipCustody -> "CUSTODY"
                R.id.chipOther -> "OTHER"
                else -> "OTHER"
            }
            if (title.isEmpty()) {
                Toast.makeText(requireContext(), R.string.event_error_title_required, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            updateEvent(event.id, title, apiDateFormat.format(selectedCalendar.time), type, notes)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showEditEventDialogFixed(event: Event) {
        val dialog = BottomSheetDialog(requireContext(), R.style.AddEventBottomSheetTheme)
        val dialogRoot = requireActivity().findViewById<ViewGroup>(android.R.id.content)
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_event, dialogRoot, false)
        dialog.setContentView(dialogView)

        val btnBack = dialogView.findViewById<ImageButton>(R.id.btnBack)
        val btnSave = dialogView.findViewById<MaterialButton>(R.id.btnSaveEvent)
        val tvDialogTitle = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        val form = AddEventFormBinder(
            dialogView,
            displayDateFormat = { displayDateFormat.format(it.time) },
            displayTimeFormat = { displayTimeFormat.format(it.time) }
        )
        val layoutDateTimeSingle = dialogView.findViewById<LinearLayout>(R.id.layoutDateTimeSingle)
        val layoutDateTimeStart = dialogView.findViewById<LinearLayout>(R.id.layoutDateTimeStart)
        val layoutDateTimeEnd = dialogView.findViewById<LinearLayout>(R.id.layoutDateTimeEnd)

        tvDialogTitle?.setText(R.string.event_edit_title)
        btnSave.setText(R.string.action_save_changes)
        form.etTitle.setText(event.title)
        form.etNotes.setText(event.notes)
        layoutDateTimeSingle?.visibility = View.VISIBLE
        layoutDateTimeStart?.visibility = View.GONE
        layoutDateTimeEnd?.visibility = View.GONE

        val selectedCalendar = Calendar.getInstance()
        try {
            val parsed = apiDateFormat.parse(event.event_date)
            if (parsed != null) selectedCalendar.time = parsed
        } catch (_: Exception) {}

        form.setPickerValue(form.datePicker, selectedCalendar, isDate = true)
        form.setPickerValue(form.timePicker, selectedCalendar, isDate = false)
        form.setupPickerRow(form.datePicker) { done ->
            showDatePicker(selectedCalendar) {
                form.setPickerValue(form.datePicker, selectedCalendar, isDate = true)
                done()
            }
        }
        form.setupPickerRow(form.timePicker) { done ->
            showTimePicker(selectedCalendar) {
                form.setPickerValue(form.timePicker, selectedCalendar, isDate = false)
                done()
            }
        }
        form.setupTextFieldValidation()
        form.setupAccessibility(btnSave)
        form.segmentType.check(
            when (event.event_type.uppercase()) {
                "SCHOOL" -> R.id.chipSchool
                "MEDICAL" -> R.id.chipMedical
                "CUSTODY" -> R.id.chipCustody
                else -> R.id.chipOther
            }
        )

        btnBack.setOnClickListener { dialog.dismiss() }
        btnSave.setOnClickListener {
            if (!form.validateTitle()) {
                form.rejectHaptic()
                return@setOnClickListener
            }

            val title = form.etTitle.text?.toString()?.trim().orEmpty()
            val notes = form.etNotes.text?.toString()?.trim().orEmpty()
            val type = eventTypeFromSegment(form.segmentType.checkedChipId)
            updateEvent(event.id, title, apiDateFormat.format(selectedCalendar.time), type, notes)
            dialog.dismiss()
        }

        dialog.show()
        fitBottomSheetToContent(dialog)
    }

    private fun updateEvent(eventId: Int, title: String, date: String, type: String, notes: String) {
        lifecycleScope.launch {
            try {
                if (token.isEmpty() || apiService == null) return@launch
                val body = mapOf<String, Any?>(
                    "title" to title,
                    "event_date" to date,
                    "event_type" to type,
                    "notes" to notes
                )
                apiService!!.updateEvent("Bearer $token", eventId, body)
                Toast.makeText(requireContext(), R.string.event_updated, Toast.LENGTH_SHORT).show()
                loadEvents()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), R.string.event_update_error, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
