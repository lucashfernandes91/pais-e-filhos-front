package com.example.chatapp.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
import com.example.chatapp.CreateEventRequest
import com.example.chatapp.Event
import com.example.chatapp.EventsAdapter
import com.example.chatapp.PrefsHelper
import com.example.chatapp.R
import com.example.chatapp.RetrofitClient
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class AgendaFragment : Fragment() {

    private var btnAddEvent: MaterialButton? = null
    private var eventsRecyclerView: RecyclerView? = null
    private var swipeRefresh: SwipeRefreshLayout? = null
    private var emptyState: View? = null
    private var apiService: ApiService? = null
    private var conversationId: Int = 1
    private var token: String = ""
    // All events (for calendar markers)
    private var allEvents = mutableListOf<Event>()
    // Filtered events (today + future, for the list)
    private var events = mutableListOf<Event>()
    private var eventsAdapter: EventsAdapter? = null

    // Calendar
    private var calendarGrid: LinearLayout? = null
    private var tvCalendarMonth: TextView? = null
    private var tvCurrentMonth: TextView? = null
    private var displayedCalendar = Calendar.getInstance()
    private var eventDays = mutableSetOf<Int>() // days in current month that have events

    private val apiDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale("pt", "BR"))
    private val displayDateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
    private val displayTimeFormat = SimpleDateFormat("HH:mm", Locale("pt", "BR"))

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
            conversationId = arguments?.getInt("conversationId")
                ?: PrefsHelper.getConversationId(requireContext())

            btnAddEvent = view.findViewById(R.id.btnAddEvent)
            eventsRecyclerView = view.findViewById(R.id.eventsRecyclerView)
            swipeRefresh = view.findViewById(R.id.swipeRefresh)
            emptyState = view.findViewById(R.id.emptyStateAgenda)
            calendarGrid = view.findViewById(R.id.calendarGrid)
            tvCalendarMonth = view.findViewById(R.id.tvCalendarMonth)
            tvCurrentMonth = view.findViewById(R.id.tvCurrentMonth)

            setupRecyclerView()
            setupCalendarNavigation(view)
            setupSwipeRefresh()

            btnAddEvent?.setOnClickListener { showAddEventDialog() }

            loadEvents()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Erro ao inicializar: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupRecyclerView() {
        eventsAdapter = EventsAdapter(
            events,
            onDeleteClick = { event -> confirmDeleteEvent(event) },
            onEditClick = { event -> showEditEventDialog(event) }
        )
        eventsRecyclerView?.layoutManager = LinearLayoutManager(requireContext())
        eventsRecyclerView?.adapter = eventsAdapter
    }

    private fun setupSwipeRefresh() {
        swipeRefresh?.setColorSchemeResources(R.color.primary_blue)
        swipeRefresh?.setOnRefreshListener { loadEvents() }
    }

    private fun setupCalendarNavigation(view: View) {
        view.findViewById<ImageButton>(R.id.btnPrevMonth)?.setOnClickListener {
            displayedCalendar.add(Calendar.MONTH, -1)
            renderCalendar()
        }
        view.findViewById<ImageButton>(R.id.btnNextMonth)?.setOnClickListener {
            displayedCalendar.add(Calendar.MONTH, 1)
            renderCalendar()
        }
        renderCalendar()
    }

    // ── Calendar Rendering ──────────────────────────────────

    private fun renderCalendar() {
        val grid = calendarGrid ?: return
        grid.removeAllViews()

        val monthFormat = SimpleDateFormat("MMMM 'de' yyyy", Locale("pt", "BR"))
        val monthText = monthFormat.format(displayedCalendar.time)
            .replaceFirstChar { it.uppercase() }
        tvCalendarMonth?.text = monthText
        tvCurrentMonth?.text = monthText

        val cal = displayedCalendar.clone() as Calendar
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // 1=Sun, 7=Sat
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

        val today = Calendar.getInstance()
        val isCurrentMonth = today.get(Calendar.YEAR) == displayedCalendar.get(Calendar.YEAR) &&
                today.get(Calendar.MONTH) == displayedCalendar.get(Calendar.MONTH)
        val todayDay = today.get(Calendar.DAY_OF_MONTH)

        val ctx = requireContext()
        val dp = { value: Int -> (value * ctx.resources.displayMetrics.density).toInt() }

        var dayCounter = 1
        val totalCells = firstDayOfWeek - 1 + daysInMonth
        val totalRows = (totalCells + 6) / 7

        for (row in 0 until totalRows) {
            val rowLayout = LinearLayout(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(44)
                )
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }

            for (col in 0 until 7) {
                val cellIndex = row * 7 + col
                val frame = FrameLayout(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f)
                }

                if (cellIndex >= firstDayOfWeek - 1 && dayCounter <= daysInMonth) {
                    val day = dayCounter
                    val isToday = isCurrentMonth && day == todayDay
                    val hasEvent = eventDays.contains(day)

                    if (isToday) {
                        val circle = View(ctx).apply {
                            layoutParams = FrameLayout.LayoutParams(dp(36), dp(36)).apply {
                                gravity = Gravity.CENTER
                            }
                            background = ContextCompat.getDrawable(ctx, R.drawable.bg_today_circle)
                        }
                        frame.addView(circle)
                    } else if (hasEvent) {
                        val circle = View(ctx).apply {
                            layoutParams = FrameLayout.LayoutParams(dp(36), dp(36)).apply {
                                gravity = Gravity.CENTER
                            }
                            background = ContextCompat.getDrawable(ctx, R.drawable.bg_event_circle_blue)
                        }
                        frame.addView(circle)
                    }

                    val tv = TextView(ctx).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        )
                        text = day.toString()
                        gravity = Gravity.CENTER
                        textSize = 14f
                        setTextColor(
                            ContextCompat.getColor(
                                ctx,
                                if (isToday) R.color.white
                                else if (hasEvent) R.color.primary_blue
                                else R.color.gray_700
                            )
                        )
                        if (isToday) setTypeface(typeface, android.graphics.Typeface.BOLD)
                    }
                    frame.addView(tv)
                    dayCounter++
                }

                rowLayout.addView(frame)
            }

            grid.addView(rowLayout)
        }
    }

    private fun updateEventDays() {
        eventDays.clear()
        val currentYear = displayedCalendar.get(Calendar.YEAR)
        val currentMonth = displayedCalendar.get(Calendar.MONTH)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val dateFormatAlt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        for (event in allEvents) {
            try {
                val date = try {
                    dateFormat.parse(event.event_date)
                } catch (e: Exception) {
                    dateFormatAlt.parse(event.event_date)
                }
                if (date != null) {
                    val eventCal = Calendar.getInstance().apply { time = date }
                    if (eventCal.get(Calendar.YEAR) == currentYear &&
                        eventCal.get(Calendar.MONTH) == currentMonth
                    ) {
                        eventDays.add(eventCal.get(Calendar.DAY_OF_MONTH))
                    }
                }
            } catch (_: Exception) {}
        }
    }

    // ── Data Loading ────────────────────────────────────────

    private fun parseEventDate(dateStr: String): Date? {
        val formats = listOf(
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS", Locale.getDefault()),
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

    private fun loadEvents() {
        lifecycleScope.launch {
            try {
                if (token.isEmpty() || apiService == null) return@launch
                val eventList = apiService!!.getEvents("Bearer $token", conversationId)

                // All events for calendar markers
                allEvents.clear()
                allEvents.addAll(eventList)

                // Filter: only today and future for the list
                val todayCal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val todayStart = todayCal.time

                events.clear()
                events.addAll(
                    eventList.filter {
                        val d = parseEventDate(it.event_date)
                        d != null && !d.before(todayStart)
                    }.sortedBy { it.event_date }
                )
                eventsAdapter?.notifyDataSetChanged()

                // Calendar uses ALL events (past included)
                updateEventDays()
                renderCalendar()

                updateEmptyState()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Erro ao carregar eventos: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                swipeRefresh?.isRefreshing = false
            }
        }
    }

    private fun updateEmptyState() {
        if (events.isEmpty()) {
            eventsRecyclerView?.visibility = View.GONE
            emptyState?.visibility = View.VISIBLE
        } else {
            eventsRecyclerView?.visibility = View.VISIBLE
            emptyState?.visibility = View.GONE
        }
    }

    // ── Delete with confirmation (Item 26 bonus) ────────────

    private fun confirmDeleteEvent(event: Event) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Excluir evento")
            .setMessage("Deseja excluir \"${event.title}\"?")
            .setPositiveButton("Excluir") { _, _ -> deleteEvent(event.id) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ── Add Event Dialog ────────────────────────────────────

    private fun showAddEventDialog() {
        val dialog = BottomSheetDialog(requireContext(), R.style.BottomSheetDialogTheme)
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_event, null)
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

        dialogView.findViewById<com.google.android.material.chip.Chip>(R.id.chipSchool)?.isChecked = true

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
                Toast.makeText(requireContext(), "Título é obrigatório", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (type == "CUSTODY") {
                if (endCalendar.before(startCalendar)) {
                    Toast.makeText(requireContext(), "A data de fim deve ser após o início", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                createEvent(title, apiDateFormat.format(startCalendar.time), type, notes, apiDateFormat.format(endCalendar.time))
            } else {
                createEvent(title, apiDateFormat.format(selectedCalendar.time), type, notes, null)
            }
            dialog.dismiss()
        }

        dialog.show()
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
                    Toast.makeText(requireContext(), "Token não encontrado", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(requireContext(), "Evento criado!", Toast.LENGTH_SHORT).show()
                loadEvents()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Erro ao criar evento: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deleteEvent(eventId: Int) {
        lifecycleScope.launch {
            try {
                if (token.isEmpty() || apiService == null) return@launch
                apiService!!.deleteEvent("Bearer $token", eventId)
                Toast.makeText(requireContext(), "Evento excluído", Toast.LENGTH_SHORT).show()
                loadEvents()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Erro ao excluir evento: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── Edit Event ────────────────────────────────────────

    private fun showEditEventDialog(event: Event) {
        val dialog = BottomSheetDialog(requireContext(), R.style.BottomSheetDialogTheme)
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_event, null)
        dialog.setContentView(dialogView)

        val btnBack = dialogView.findViewById<ImageButton>(R.id.btnBack)
        val titleInput = dialogView.findViewById<TextInputEditText>(R.id.etEventTitle)
        val chipGroup = dialogView.findViewById<ChipGroup>(R.id.chipGroupType)
        val notesInput = dialogView.findViewById<TextInputEditText>(R.id.etEventNotes)
        val btnSave = dialogView.findViewById<MaterialButton>(R.id.btnSaveEvent)
        val tvDialogTitle = dialogView.findViewById<TextView>(R.id.tvDialogTitle)

        val dateInput = dialogView.findViewById<TextInputEditText>(R.id.etEventDate)
        val timeInput = dialogView.findViewById<TextInputEditText>(R.id.etEventTime)

        tvDialogTitle?.text = "Editar Evento"
        btnSave.text = "Salvar alterações"

        titleInput?.setText(event.title)
        notesInput?.setText(event.notes)

        when (event.event_type.uppercase()) {
            "SCHOOL" -> dialogView.findViewById<com.google.android.material.chip.Chip>(R.id.chipSchool)?.isChecked = true
            "MEDICAL" -> dialogView.findViewById<com.google.android.material.chip.Chip>(R.id.chipMedical)?.isChecked = true
            "CUSTODY" -> dialogView.findViewById<com.google.android.material.chip.Chip>(R.id.chipCustody)?.isChecked = true
            else -> dialogView.findViewById<com.google.android.material.chip.Chip>(R.id.chipOther)?.isChecked = true
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
                Toast.makeText(requireContext(), "Título é obrigatório", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            updateEvent(event.id, title, apiDateFormat.format(selectedCalendar.time), type, notes)
            dialog.dismiss()
        }

        dialog.show()
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
                Toast.makeText(requireContext(), "Evento atualizado!", Toast.LENGTH_SHORT).show()
                loadEvents()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Erro ao atualizar: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
