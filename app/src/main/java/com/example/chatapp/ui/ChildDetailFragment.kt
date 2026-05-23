package com.example.chatapp.ui

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.chatapp.Child
import com.example.chatapp.PrefsHelper
import com.example.chatapp.R
import com.example.chatapp.RetrofitClient
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Tela de detalhes de um filho.
 *
 * Recebe o objeto Child via argumento Serializable ("child").
 * Permite visualizar dados e remover o filho.
 */
class ChildDetailFragment : Fragment() {

    companion object {
        const val ARG_CHILD = "child"
    }

    private var child: Child? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_child_detail, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        @Suppress("DEPRECATION")
        child = arguments?.getSerializable(ARG_CHILD) as? Child

        val c = child
        if (c == null) {
            Toast.makeText(requireContext(), "Filho não encontrado", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
            return
        }

        // Back button
        view.findViewById<ImageView>(R.id.btnBack)?.setOnClickListener {
            findNavController().popBackStack()
        }

        bindHeader(view, c)
        bindInfoRows(view, c)
        bindRemoveButton(view, c)
    }

    // ── Header (foto, nome, idade, badge guarda) ────────────

    private fun bindHeader(view: View, c: Child) {
        val tvName = view.findViewById<TextView>(R.id.tvChildName)
        val tvAge = view.findViewById<TextView>(R.id.tvChildAge)
        val tvInitial = view.findViewById<TextView>(R.id.tvChildInitial)
        val ivPhoto = view.findViewById<ImageView>(R.id.ivChildPhoto)
        val badgeCustody = view.findViewById<LinearLayout>(R.id.badgeCustody)

        tvName?.text = c.name
        tvInitial?.text = if (c.name.isNotEmpty()) c.name.first().uppercase() else "?"

        // Idade calculada a partir da data de nascimento
        val ageText = c.birth_date?.let { calculateAge(it) }
        if (!ageText.isNullOrEmpty()) {
            tvAge?.text = ageText
            tvAge?.visibility = View.VISIBLE
        } else {
            tvAge?.visibility = View.GONE
        }

        // Foto (se houver path local salvo) — por enquanto não há fluxo de upload,
        // então apenas mostramos a inicial. Hook deixado para evolução futura.
        ivPhoto?.visibility = View.GONE
        tvInitial?.visibility = View.VISIBLE

        // Badge "Sob sua guarda"
        badgeCustody?.visibility = if (c.has_custody) View.VISIBLE else View.GONE
    }

    // ── Rows de informações (data, CPF, RG) ─────────────────

    private fun bindInfoRows(view: View, c: Child) {
        val rowBirth = view.findViewById<View>(R.id.rowBirthDate)
        val rowCpf = view.findViewById<View>(R.id.rowCpf)
        val rowRg = view.findViewById<View>(R.id.rowRg)
        val dividerCpf = view.findViewById<View>(R.id.dividerCpf)
        val dividerRg = view.findViewById<View>(R.id.dividerRg)

        val tvBirth = view.findViewById<TextView>(R.id.tvBirthDate)
        val tvCpf = view.findViewById<TextView>(R.id.tvCpf)
        val tvRg = view.findViewById<TextView>(R.id.tvRg)

        // Data de nascimento
        val birth = c.birth_date
        if (!birth.isNullOrEmpty()) {
            tvBirth?.text = formatBirthDate(birth)
            rowBirth?.visibility = View.VISIBLE
        } else {
            rowBirth?.visibility = View.GONE
            dividerCpf?.visibility = View.GONE
        }

        // CPF
        val cpf = c.cpf
        if (!cpf.isNullOrEmpty()) {
            tvCpf?.text = cpf
            rowCpf?.visibility = View.VISIBLE
        } else {
            rowCpf?.visibility = View.GONE
            dividerCpf?.visibility = View.GONE
        }

        // RG
        val rg = c.rg
        if (!rg.isNullOrEmpty()) {
            tvRg?.text = rg
            rowRg?.visibility = View.VISIBLE
        } else {
            rowRg?.visibility = View.GONE
            dividerRg?.visibility = View.GONE
        }

        // Esconder último divider se a próxima row estiver oculta
        if (rowCpf?.visibility == View.GONE) {
            dividerCpf?.visibility = View.GONE
        }
        if (rowRg?.visibility == View.GONE) {
            dividerRg?.visibility = View.GONE
        }
    }

    // ── Botão Remover ───────────────────────────────────────

    private fun bindRemoveButton(view: View, c: Child) {
        view.findViewById<MaterialButton>(R.id.btnRemoveChild)?.setOnClickListener {
            confirmDelete(c)
        }
    }

    private fun confirmDelete(c: Child) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Remover filho(a)")
            .setMessage("Deseja remover ${c.name}? Esta ação não pode ser desfeita.")
            .setPositiveButton("Remover") { _, _ -> deleteChild(c.id) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun deleteChild(childId: Int) {
        val token = PrefsHelper.getAuthToken(requireContext())
        if (token.isEmpty()) {
            Toast.makeText(requireContext(), "Sessão expirada", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                RetrofitClient.api.deleteChild("Bearer $token", childId)
                Toast.makeText(requireContext(), "Removido", Toast.LENGTH_SHORT).show()
                findNavController().popBackStack()
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    "Erro ao remover: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // ── Utils ───────────────────────────────────────────────

    private fun formatBirthDate(dateStr: String): String = try {
        val input = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val output = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
        val date: Date? = input.parse(dateStr)
        if (date != null) output.format(date) else dateStr
    } catch (_: Exception) {
        dateStr
    }

    /**
     * Calcula a idade em anos a partir da data de nascimento (yyyy-MM-dd).
     * Retorna string formatada ou null se o parse falhar.
     */
    private fun calculateAge(birthDateStr: String): String? = try {
        val input = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val birthDate = input.parse(birthDateStr) ?: return null

        val today = Calendar.getInstance()
        val birth = Calendar.getInstance().apply { time = birthDate }

        var age = today.get(Calendar.YEAR) - birth.get(Calendar.YEAR)
        if (today.get(Calendar.DAY_OF_YEAR) < birth.get(Calendar.DAY_OF_YEAR)) {
            age--
        }

        when {
            age < 0  -> null
            age == 0 -> "Menos de 1 ano"
            age == 1 -> "1 ano"
            else     -> "$age anos"
        }
    } catch (_: Exception) {
        null
    }
}
