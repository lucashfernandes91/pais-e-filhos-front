package com.example.chatapp.ui

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import com.example.chatapp.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.util.Calendar

/**
 * Formulário de novo/editar evento: labels, pickers de data/hora, segmented control e feedback.
 */
class AddEventFormBinder(
    private val root: View,
    displayDateFormat: (Calendar) -> String,
    displayTimeFormat: (Calendar) -> String
) {

    val tilTitle: TextInputLayout = root.findViewById(R.id.tilEventTitle)
    val etTitle: TextInputEditText = root.findViewById(R.id.etEventTitle)
    val lblTitle: TextView = root.findViewById(R.id.lblEventTitle)

    val tilNotes: TextInputLayout = root.findViewById(R.id.tilEventNotes)
    val etNotes: TextInputEditText = root.findViewById(R.id.etEventNotes)
    val lblNotes: TextView = root.findViewById(R.id.lblEventNotes)

    val lblType: TextView = root.findViewById(R.id.lblEventType)
    val segmentType: ChipGroup = root.findViewById(R.id.segmentEventType)

    val bannerFeedback: LinearLayout = root.findViewById(R.id.bannerFormFeedback)
    val progressFeedback: ProgressBar = root.findViewById(R.id.progressFormFeedback)
    val ivFeedback: ImageView = root.findViewById(R.id.ivFormFeedback)
    val tvFeedback: TextView = root.findViewById(R.id.tvFormFeedback)

    lateinit var datePicker: PickerRow
    lateinit var timePicker: PickerRow
    lateinit var startDatePicker: PickerRow
    lateinit var startTimePicker: PickerRow
    lateinit var endDatePicker: PickerRow
    lateinit var endTimePicker: PickerRow

    private val errorColor = ContextCompat.getColor(root.context, R.color.error)
    private val labelColor = ContextCompat.getColor(root.context, R.color.gray_700)
    private val primaryColor = ContextCompat.getColor(root.context, R.color.primary_blue)
    private val strokeDefault = ContextCompat.getColor(root.context, R.color.gray_300)

    private val formatDate = displayDateFormat
    private val formatTime = displayTimeFormat

    data class Field(
        val label: TextView,
        val layout: TextInputLayout,
        val editText: TextInputEditText
    )

    data class PickerRow(
        val root: View,
        val card: MaterialCardView,
        val tvLabel: TextView,
        val tvValue: TextView,
        val tvError: TextView
    )

    val titleField = Field(lblTitle, tilTitle, etTitle)
    val notesField = Field(lblNotes, tilNotes, etNotes)

    init {
        val ctx = root.context
        datePicker = inflatePicker(
            root.findViewById(R.id.containerEventDate),
            R.drawable.ic_calendar,
            ctx.getString(R.string.event_label_date),
            ctx.getString(R.string.event_pick_date)
        )
        timePicker = inflatePicker(
            root.findViewById(R.id.containerEventTime),
            R.drawable.ic_clock,
            ctx.getString(R.string.event_label_time),
            ctx.getString(R.string.event_pick_time)
        )
        startDatePicker = inflatePicker(
            root.findViewById(R.id.containerStartDate),
            R.drawable.ic_calendar,
            ctx.getString(R.string.event_label_date),
            ctx.getString(R.string.event_pick_date)
        )
        startTimePicker = inflatePicker(
            root.findViewById(R.id.containerStartTime),
            R.drawable.ic_clock,
            ctx.getString(R.string.event_label_time),
            ctx.getString(R.string.event_pick_time)
        )
        endDatePicker = inflatePicker(
            root.findViewById(R.id.containerEndDate),
            R.drawable.ic_calendar,
            ctx.getString(R.string.event_label_date),
            ctx.getString(R.string.event_pick_date)
        )
        endTimePicker = inflatePicker(
            root.findViewById(R.id.containerEndTime),
            R.drawable.ic_clock,
            ctx.getString(R.string.event_label_time),
            ctx.getString(R.string.event_pick_time)
        )
    }

    private fun inflatePicker(
        container: FrameLayout,
        iconRes: Int,
        label: String,
        placeholder: String
    ): PickerRow {
        val rowView = LayoutInflater.from(container.context)
            .inflate(R.layout.view_event_picker_row, container, true)
        val card = rowView.findViewById<MaterialCardView>(R.id.pickerCard)
        val ivIcon = rowView.findViewById<ImageView>(R.id.ivPickerIcon)
        val tvLabel = rowView.findViewById<TextView>(R.id.tvPickerLabel)
        val tvValue = rowView.findViewById<TextView>(R.id.tvPickerValue)
        val tvError = rowView.findViewById<TextView>(R.id.tvPickerError)
        ivIcon.setImageResource(iconRes)
        tvLabel.text = label
        tvValue.text = placeholder
        tvValue.setTextColor(ContextCompat.getColor(container.context, R.color.gray_400))
        return PickerRow(rowView, card, tvLabel, tvValue, tvError)
    }

    fun setPickerValue(row: PickerRow, calendar: Calendar, isDate: Boolean) {
        val formatted = if (isDate) formatDate(calendar) else formatTime(calendar)
        row.tvValue.text = formatted
        row.tvValue.setTextColor(ContextCompat.getColor(root.context, R.color.gray_900))
        val cdRes = if (isDate) R.string.cd_event_picker_date else R.string.cd_event_picker_time
        row.card.contentDescription = root.context.getString(cdRes, formatted)
        clearPickerError(row)
    }

    fun setupPickerRow(row: PickerRow, onPick: (onDismiss: () -> Unit) -> Unit) {
        row.card.setOnClickListener {
            if (!row.card.isEnabled) return@setOnClickListener
            clearPickerError(row)
            row.card.strokeColor = primaryColor
            onPick {
                row.card.strokeColor = strokeDefault
            }
        }
    }

    fun setupTextFieldValidation() {
        setupEditableField(titleField) { validateTitle() }
        setupEditableField(notesField) {
            clearField(notesField)
            true
        }
        setupRealtimeValidation()
    }

    private fun setupRealtimeValidation() {
        etTitle.addTextChangedListener(object : TextWatcher {
            private var pending: Runnable? = null

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable?) {
                pending?.let { etTitle.removeCallbacks(it) }
                pending = Runnable {
                    val text = s?.toString()?.trim().orEmpty()
                    when {
                        text.isEmpty() && etTitle.hasFocus() -> clearField(titleField)
                        text.isNotEmpty() -> validateTitle()
                    }
                }
                etTitle.postDelayed(pending!!, 300)
            }
        })
    }

    fun setupAccessibility(saveButton: MaterialButton) {
        root.findViewById<View>(R.id.btnBack)?.contentDescription =
            root.context.getString(R.string.cd_event_sheet_back)
        saveButton.contentDescription = root.context.getString(R.string.cd_event_save)
        etTitle.contentDescription = root.context.getString(R.string.cd_event_title_field)
        etNotes.contentDescription = root.context.getString(R.string.cd_event_notes_field)

        ViewCompat.setAccessibilityHeading(lblType, true)

        mapOf(
            R.id.segSchool to R.string.seg_school,
            R.id.segMedical to R.string.seg_medical,
            R.id.segCustody to R.string.seg_custody,
            R.id.segOther to R.string.seg_other
        ).forEach { (id, labelRes) ->
            segmentType.findViewById<Chip>(id)?.contentDescription =
                root.context.getString(
                    R.string.cd_event_type_segment,
                    root.context.getString(labelRes)
                )
        }

        bannerFeedback.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }

    fun setupSegmentGroup(onTypeChanged: () -> Unit) {
        segmentType.setOnCheckedStateChangeListener { _, _ ->
            updateTitlePlaceholder()
            onTypeChanged()
        }
    }

    fun isCustodySelected(): Boolean =
        segmentType.checkedChipId == R.id.segCustody

    fun updateTitlePlaceholder() {
        val placeholder = when (segmentType.checkedChipId) {
            R.id.segSchool -> R.string.event_placeholder_school
            R.id.segMedical -> R.string.event_placeholder_medical
            R.id.segCustody -> R.string.event_placeholder_custody
            R.id.segOther -> R.string.event_placeholder_other
            else -> R.string.event_placeholder_title
        }
        etTitle.hint = root.context.getString(placeholder)
    }

    fun validateTitle(): Boolean {
        val text = etTitle.text?.toString()?.trim().orEmpty()
        return if (text.isEmpty()) {
            setFieldError(titleField, root.context.getString(R.string.event_error_title_required))
            false
        } else {
            clearField(titleField)
            true
        }
    }

    fun validateCustodyRange(start: Calendar, end: Calendar): Boolean {
        return if (end.before(start)) {
            val message = root.context.getString(R.string.event_error_custody_range)
            setPickerError(endDatePicker, message)
            setPickerError(endTimePicker, message)
            false
        } else {
            clearPickerError(endDatePicker)
            clearPickerError(endTimePicker)
            true
        }
    }

    fun validateAll(isCustody: Boolean, start: Calendar, end: Calendar): Boolean {
        hideFeedback()
        clearAllErrors()
        var valid = validateTitle()
        if (isCustody) {
            valid = validateCustodyRange(start, end) && valid
        }
        return valid
    }

    fun clearAllErrors() {
        listOf(titleField, notesField).forEach { clearField(it) }
        listOf(datePicker, timePicker, startDatePicker, startTimePicker, endDatePicker, endTimePicker)
            .forEach { clearPickerError(it) }
    }

    fun setFormEnabled(
        enabled: Boolean,
        saveButton: MaterialButton? = null
    ) {
        listOf(titleField, notesField).forEach { field ->
            field.layout.isEnabled = enabled
            field.editText.isEnabled = enabled
            field.label.isEnabled = enabled
        }
        listOf(datePicker, timePicker, startDatePicker, startTimePicker, endDatePicker, endTimePicker)
            .forEach { row ->
                row.card.isEnabled = enabled
                row.card.alpha = if (enabled) 1f else 0.55f
            }
        lblType.isEnabled = enabled
        segmentType.isEnabled = enabled
        for (i in 0 until segmentType.childCount) {
            segmentType.getChildAt(i).isEnabled = enabled
        }
        saveButton?.isEnabled = enabled
    }

    fun showLoading(message: String) {
        revealFeedbackBanner()
        bannerFeedback.setBackgroundResource(R.drawable.bg_form_feedback_loading)
        progressFeedback.visibility = View.VISIBLE
        ivFeedback.visibility = View.GONE
        tvFeedback.text = message
        tvFeedback.setTextColor(ContextCompat.getColor(root.context, R.color.feedback_loading_text))
        bannerFeedback.contentDescription = root.context.getString(R.string.cd_event_feedback_loading)
    }

    fun showSuccess(message: String) {
        revealFeedbackBanner()
        bannerFeedback.setBackgroundResource(R.drawable.bg_form_feedback_success)
        progressFeedback.visibility = View.GONE
        ivFeedback.visibility = View.VISIBLE
        ivFeedback.setImageResource(R.drawable.ic_check_circle)
        tvFeedback.text = message
        tvFeedback.setTextColor(ContextCompat.getColor(root.context, R.color.feedback_success_text))
        bannerFeedback.contentDescription = root.context.getString(R.string.cd_event_feedback_success)
    }

    fun showError(message: String) {
        revealFeedbackBanner()
        bannerFeedback.setBackgroundResource(R.drawable.bg_form_feedback_error)
        progressFeedback.visibility = View.GONE
        ivFeedback.visibility = View.VISIBLE
        ivFeedback.setImageResource(R.drawable.ic_error_circle)
        tvFeedback.text = message
        tvFeedback.setTextColor(ContextCompat.getColor(root.context, R.color.feedback_error_text))
        bannerFeedback.contentDescription = root.context.getString(R.string.cd_event_feedback_error)
    }

    fun hideFeedback() {
        if (bannerFeedback.visibility != View.VISIBLE) return
        bannerFeedback.animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction {
                bannerFeedback.visibility = View.GONE
                bannerFeedback.alpha = 1f
            }
            .start()
    }

    private fun revealFeedbackBanner() {
        if (bannerFeedback.visibility == View.VISIBLE) {
            bannerFeedback.animate().cancel()
            bannerFeedback.alpha = 1f
            return
        }
        bannerFeedback.alpha = 0f
        bannerFeedback.visibility = View.VISIBLE
        bannerFeedback.animate().alpha(1f).setDuration(250).start()
    }

    fun rejectHaptic() {
        root.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
    }

    private fun setupEditableField(field: Field, validate: () -> Boolean) {
        field.editText.setOnFocusChangeListener { _, hasFocus ->
            field.layout.isActivated = hasFocus
            if (!hasFocus) validate() else clearField(field)
        }
    }

    private fun setFieldError(field: Field, message: String) {
        field.layout.error = message
        field.layout.isErrorEnabled = true
        field.label.setTextColor(errorColor)
    }

    private fun clearField(field: Field) {
        field.layout.error = null
        field.layout.isErrorEnabled = false
        field.layout.isActivated = false
        field.label.setTextColor(labelColor)
    }

    private fun setPickerError(row: PickerRow, message: String) {
        row.tvError.text = message
        row.tvError.visibility = View.VISIBLE
        row.card.strokeColor = errorColor
        row.card.strokeWidth = 2
    }

    private fun clearPickerError(row: PickerRow) {
        row.tvError.visibility = View.GONE
        row.tvError.text = null
        row.card.strokeColor = strokeDefault
        row.card.strokeWidth = 1
    }
}
