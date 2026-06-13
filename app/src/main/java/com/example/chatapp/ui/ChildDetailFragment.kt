package com.example.chatapp.ui

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.chatapp.Child
import com.example.chatapp.PrefsHelper
import com.example.chatapp.R
import com.example.chatapp.RetrofitClient
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ChildDetailFragment : Fragment() {

    companion object {
        const val ARG_CHILD = "child"
    }

    private var child: Child? = null
    private val photoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { selectedPhoto ->
            view?.let { showLocalPhoto(it, selectedPhoto) }
            uploadChildPhoto(selectedPhoto)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_child_detail, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        @Suppress("DEPRECATION")
        child = arguments?.getSerializable(ARG_CHILD) as? Child
        if (child == null) {
            Toast.makeText(requireContext(), "Filho nao encontrado", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
            return
        }

        view.findViewById<View>(R.id.btnBack).setOnClickListener {
            findNavController().popBackStack()
        }
        view.findViewById<View>(R.id.btnEdit).setOnClickListener { openEditScreen() }
        view.findViewById<View>(R.id.btnChildPhoto).setOnClickListener {
            photoPickerLauncher.launch("image/*")
        }
        view.findViewById<MaterialButton>(R.id.btnRemoveChild).setOnClickListener {
            child?.let(::confirmDelete)
        }

        renderChild(view, child!!)
        observeEditResult()
    }

    private fun openEditScreen() {
        val currentChild = child ?: return
        val bundle = Bundle().apply {
            putSerializable(EditChildFragment.ARG_CHILD, currentChild)
        }
        findNavController().navigate(R.id.action_childDetail_to_editChild, bundle)
    }

    private fun observeEditResult() {
        val savedStateHandle = findNavController().currentBackStackEntry?.savedStateHandle
        savedStateHandle?.getLiveData<Child>(EditChildFragment.RESULT_UPDATED_CHILD)
            ?.observe(viewLifecycleOwner) { updatedChild ->
                child = updatedChild
                view?.let { renderChild(it, updatedChild) }
                savedStateHandle.remove<Child>(EditChildFragment.RESULT_UPDATED_CHILD)
            }
    }

    private fun renderChild(view: View, currentChild: Child) {
        bindHeader(view, currentChild)
        bindInfoRows(view, currentChild)
    }

    private fun bindHeader(view: View, currentChild: Child) {
        val tvInitial = view.findViewById<TextView>(R.id.tvChildInitial)
        val ivPhoto = view.findViewById<ImageView>(R.id.ivChildPhoto)

        view.findViewById<TextView>(R.id.tvChildName).text = currentChild.name
        tvInitial.text = currentChild.name.firstOrNull()?.uppercase() ?: "?"
        view.findViewById<TextView>(R.id.tvChildAge).text =
            currentChild.birth_date?.let(::calculateAge) ?: getString(R.string.child_not_informed)

        ivPhoto.visibility = View.GONE
        tvInitial.visibility = View.VISIBLE
        loadChildPhoto(ivPhoto, tvInitial, currentChild.photo_url)

        view.findViewById<LinearLayout>(R.id.badgeCustody).visibility =
            if (currentChild.has_custody) View.VISIBLE else View.GONE
    }

    private fun loadChildPhoto(ivPhoto: ImageView, tvInitial: TextView, photoUrl: String?) {
        if (photoUrl.isNullOrBlank()) return
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                runCatching {
                    URL(photoUrl).openStream().use { BitmapFactory.decodeStream(it) }
                }.getOrNull()
            }
            if (bitmap != null) {
                ivPhoto.setImageBitmap(bitmap)
                ivPhoto.visibility = View.VISIBLE
                tvInitial.visibility = View.GONE
            }
        }
    }

    private fun showLocalPhoto(view: View, uri: Uri) {
        view.findViewById<ImageView>(R.id.ivChildPhoto)?.apply {
            setImageURI(uri)
            visibility = View.VISIBLE
        }
        view.findViewById<TextView>(R.id.tvChildInitial)?.visibility = View.GONE
    }

    private fun uploadChildPhoto(photoUri: Uri) {
        val currentChild = child ?: return
        val token = PrefsHelper.getAuthToken(requireContext())
        if (token.isEmpty()) {
            Toast.makeText(requireContext(), "Sessao expirada", Toast.LENGTH_SHORT).show()
            view?.let { renderChild(it, currentChild) }
            return
        }

        lifecycleScope.launch {
            try {
                val photoPart = createPhotoPart(photoUri)
                val updatedChild = RetrofitClient.api.updateChildPhoto(
                    "Bearer $token",
                    currentChild.id,
                    photoPart
                )

                child = updatedChild
                view?.let { renderChild(it, updatedChild) }
                Toast.makeText(requireContext(), "Foto atualizada", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                view?.let { renderChild(it, currentChild) }
                Toast.makeText(requireContext(), "Erro ao atualizar foto: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun createPhotoPart(uri: Uri): MultipartBody.Part =
        withContext(Dispatchers.IO) {
            val resolver = requireContext().contentResolver
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalArgumentException("Nao foi possivel ler a foto selecionada")
            if (bytes.size > 5 * 1024 * 1024) {
                throw IllegalArgumentException("A foto deve ter no maximo 5 MB")
            }

            var filename = "child_photo.jpg"
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) {
                    filename = cursor.getString(nameIndex)
                }
            }

            val mimeType = resolver.getType(uri) ?: "image/jpeg"
            MultipartBody.Part.createFormData(
                "photo",
                filename,
                bytes.toRequestBody(mimeType.toMediaType())
            )
        }

    private fun bindInfoRows(view: View, currentChild: Child) {
        val notInformed = getString(R.string.child_not_informed)
        view.findViewById<TextView>(R.id.tvBirthDate).text =
            currentChild.birth_date?.takeIf { it.isNotBlank() }?.let(::formatBirthDate) ?: notInformed
        view.findViewById<TextView>(R.id.tvCpf).text =
            currentChild.cpf?.takeIf { it.isNotBlank() } ?: notInformed
        view.findViewById<TextView>(R.id.tvRg).text =
            currentChild.rg?.takeIf { it.isNotBlank() } ?: notInformed
        view.findViewById<TextView>(R.id.tvCustodyValue).setText(
            if (currentChild.has_custody) {
                R.string.child_custody_with_you
            } else {
                R.string.child_custody_not_with_you
            }
        )
    }

    private fun confirmDelete(currentChild: Child) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Remover filho(a)")
            .setMessage("Deseja remover ${currentChild.name}? Esta acao nao pode ser desfeita.")
            .setPositiveButton("Remover") { _, _ -> deleteChild(currentChild.id) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun deleteChild(childId: Int) {
        val token = PrefsHelper.getAuthToken(requireContext())
        if (token.isEmpty()) {
            Toast.makeText(requireContext(), "Sessao expirada", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            try {
                RetrofitClient.api.deleteChild("Bearer $token", childId)
                Toast.makeText(requireContext(), "Removido", Toast.LENGTH_SHORT).show()
                findNavController().popBackStack()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Erro ao remover: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun formatBirthDate(dateStr: String): String = try {
        val input = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val output = SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("pt-BR"))
        input.parse(dateStr)?.let(output::format) ?: dateStr
    } catch (_: Exception) {
        dateStr
    }

    private fun calculateAge(birthDateStr: String): String? = try {
        val birthDate: Date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            .parse(birthDateStr) ?: return null
        val today = Calendar.getInstance()
        val birth = Calendar.getInstance().apply { time = birthDate }
        var age = today.get(Calendar.YEAR) - birth.get(Calendar.YEAR)
        if (today.get(Calendar.DAY_OF_YEAR) < birth.get(Calendar.DAY_OF_YEAR)) age--
        when {
            age < 0 -> null
            age == 0 -> "Menos de 1 ano"
            age == 1 -> "1 ano"
            else -> "$age anos"
        }
    } catch (_: Exception) {
        null
    }
}
