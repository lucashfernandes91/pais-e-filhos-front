package com.example.chatapp.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.example.chatapp.CalendarColor
import com.example.chatapp.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * BottomSheet com a paleta de cores personalizáveis do calendário.
 *
 * Uso:
 *   ColorPickerBottomSheet.show(
 *       parentFragmentManager,
 *       title = "Sua cor",
 *       currentColorId = "BLUE"
 *   ) { selectedId ->
 *       // salvar e aplicar
 *   }
 */
class ColorPickerBottomSheet : BottomSheetDialogFragment() {

    private var title: String = ""
    private var currentColorId: String = CalendarColor.DEFAULT_USER.id
    private var unavailableColorIds: Set<String> = emptySet()
    private var unavailableColorOwnerName: String = ""
    private var onColorSelected: ((String) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.bottom_sheet_color_picker, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<TextView>(R.id.tvColorPickerTitle)?.text = title
        view.findViewById<TextView>(R.id.tvColorPickerAvailability)?.visibility =
            if (unavailableColorIds.isEmpty()) View.GONE else View.VISIBLE

        val container = view.findViewById<LinearLayout>(R.id.colorPickerContainer)
        val ctx = requireContext()
        val density = ctx.resources.displayMetrics.density
        val dotSizePx = (40 * density).toInt()
        val marginPx = (12 * density).toInt()
        val checkSizePx = (20 * density).toInt()

        CalendarColor.values().forEach { color ->
            // Wrapper para dot + check
            val wrapper = FrameLayoutCompat(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(dotSizePx, dotSizePx).also {
                    it.marginEnd = marginPx
                }
            }

            // Círculo colorido
            val dot = View(ctx).apply {
                layoutParams = android.widget.FrameLayout.LayoutParams(dotSizePx, dotSizePx)
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(android.graphics.Color.parseColor(color.dotColorHex))
                }
            }

            // Check icon (mostrado apenas quando selecionado)
            val check = ImageView(ctx).apply {
                layoutParams = android.widget.FrameLayout.LayoutParams(
                    checkSizePx, checkSizePx,
                    android.view.Gravity.CENTER
                )
                setImageResource(R.drawable.ic_check)
                visibility = if (color.id == currentColorId) View.VISIBLE else View.GONE
            }

            val lock = ImageView(ctx).apply {
                layoutParams = android.widget.FrameLayout.LayoutParams(
                    checkSizePx, checkSizePx,
                    android.view.Gravity.CENTER
                )
                setImageResource(R.drawable.ic_lock)
                visibility = View.GONE
            }

            wrapper.addView(dot)
            wrapper.addView(check)
            wrapper.addView(lock)

            val isUnavailable = color.id in unavailableColorIds && color.id != currentColorId
            wrapper.alpha = if (isUnavailable) 0.48f else 1f
            wrapper.contentDescription = if (isUnavailable) {
                ctx.getString(
                    R.string.agenda_color_unavailable_content_description,
                    color.displayName(ctx),
                    unavailableColorOwnerName
                )
            } else {
                color.displayName(ctx)
            }
            lock.visibility = if (isUnavailable) View.VISIBLE else View.GONE
            wrapper.setOnClickListener {
                if (isUnavailable) {
                    Toast.makeText(
                        ctx,
                        ctx.getString(
                            R.string.agenda_color_in_use,
                            color.displayName(ctx),
                            unavailableColorOwnerName
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    onColorSelected?.invoke(color.id)
                    dismiss()
                }
            }

            container?.addView(wrapper)
        }
    }

    companion object {
        private const val TAG = "ColorPickerBottomSheet"

        fun show(
            fm: androidx.fragment.app.FragmentManager,
            title: String,
            currentColorId: String,
            unavailableColorIds: Set<String> = emptySet(),
            unavailableColorOwnerName: String = "",
            onColorSelected: (String) -> Unit
        ) {
            ColorPickerBottomSheet().apply {
                this.title = title
                this.currentColorId = currentColorId
                this.unavailableColorIds = unavailableColorIds
                this.unavailableColorOwnerName = unavailableColorOwnerName
                this.onColorSelected = onColorSelected
            }.show(fm, TAG)
        }
    }
}

// Alias para evitar import verboso dentro de um único arquivo
private typealias FrameLayoutCompat = android.widget.FrameLayout
