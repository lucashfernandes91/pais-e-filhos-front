package com.example.chatapp.ui

import android.app.DatePickerDialog
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.chatapp.Child
import com.example.chatapp.PrefsHelper
import com.example.chatapp.R
import com.example.chatapp.RemoteImageLoader
import com.example.chatapp.RetrofitClient
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Tela full-screen de edição dos dados de um filho.
 *
 * Recebe o objeto Child via argumento Serializable ("child").
 * Preenche os campos com os dados atuais e salva via PATCH.
 * Ao salvar com sucesso, faz popBackStack para voltar ao detalhe.
 */
class EditChildFragment : Fragment() {

    companion object {
        const val ARG_CHILD = "child"
        const val RESULT_UPDATED_CHILD = "updated_child"
    }

    private var child: Child? = null
    private var selectedBirthDate: String? = null
    private var selectedPhotoUri: Uri? = null

    private val photoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            selectedPhotoUri = it
            view?.let { root -> showLocalPhoto(root, it) }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_edit_child, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        @Suppress("DEPRECATION")
        child = arguments?.getSerializable(ARG_CHILD) as? Child

        val c = child
        if (c == null) {
            Toast.makeText(requireContext(), getString(R.string.child_data_not_found), Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
            return
        }

        view.findViewById<ImageView>(R.id.btnBack)?.setOnClickListener {
            findNavController().popBackStack()
        }

        populateFields(view, c)
        setupPhotoPicker(view, c)
        setupDatePicker(view, c)
        setupSaveButton(view, c)
    }

    private fun populateFields(view: View, c: Child) {
        val etName = view.findViewById<TextInputEditText>(R.id.etChildName)
        val etBirth = view.findViewById<TextInputEditText>(R.id.etBirthDate)
        val cbCustody = view.findViewById<MaterialCheckBox>(R.id.cbHasCustody)
        val tvInitial = view.findViewById<TextView>(R.id.tvChildInitial)

        etName.setText(c.name)
        tvInitial?.text = if (c.name.isNotEmpty()) c.name.first().uppercase() else "?"

        c.birth_date?.let { bd ->
            selectedBirthDate = bd
            etBirth.setText(formatDateForDisplay(bd))
        }

        cbCustody.isChecked = c.has_custody
    }

    private fun setupPhotoPicker(view: View, c: Child) {
        view.findViewById<View>(R.id.btnChildPhoto).setOnClickListener {
            photoPickerLauncher.launch("image/*")
        }
        loadRemotePhoto(view, c.photo_url)
    }

    private fun showLocalPhoto(view: View, uri: Uri) {
        view.findViewById<ImageView>(R.id.ivChildPhoto)?.apply {
            setImageURI(uri)
            visibility = View.VISIBLE
        }
        view.findViewById<TextView>(R.id.tvChildInitial)?.visibility = View.GONE
    }

    private fun loadRemotePhoto(view: View, photoUrl: String?) {
        if (photoUrl.isNullOrBlank()) return
        val imageView = view.findViewById<ImageView>(R.id.ivChildPhoto) ?: return
        RemoteImageLoader.load(imageView, photoUrl) {
            if (selectedPhotoUri == null) {
                imageView.visibility = View.VISIBLE
                view.findViewById<TextView>(R.id.tvChildInitial)?.visibility = View.GONE
            }
        }
    }

    private fun showBitmapPhoto(view: View, bitmap: Bitmap) {
        view.findViewById<ImageView>(R.id.ivChildPhoto)?.apply {
            setImageBitmap(bitmap)
            visibility = View.VISIBLE
        }
        view.findViewById<TextView>(R.id.tvChildInitial)?.visibility = View.GONE
    }

    private fun setupDatePicker(view: View, c: Child) {
        val etBirth = view.findViewById<TextInputEditText>(R.id.etBirthDate)

        etBirth.setOnClickListener {
            val cal = Calendar.getInstance()

            c.birth_date?.let { bd ->
                try {
                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    sdf.parse(bd)?.let { date ->
                        cal.time = date
                    }
                } catch (_: Exception) {}
            }

            DatePickerDialog(
                requireContext(),
                { _, year, month, day ->
                    selectedBirthDate = String.format(Locale.ROOT, "%04d-%02d-%02d", year, month + 1, day)
                    etBirth.setText(String.format(Locale.forLanguageTag("pt-BR"), "%02d/%02d/%04d", day, month + 1, year))
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
    }

    private fun setupSaveButton(view: View, c: Child) {
        val btnSave = view.findViewById<MaterialButton>(R.id.btnSave)

        btnSave.setOnClickListener {
            val name = view.findViewById<TextInputEditText>(R.id.etChildName).text?.toString()?.trim()
            val hasCustody = view.findViewById<MaterialCheckBox>(R.id.cbHasCustody).isChecked

            if (name.isNullOrBlank()) {
                view.findViewById<TextInputEditText>(R.id.etChildName)
                    .error = getString(R.string.child_name_required)
                return@setOnClickListener
            }

            if (selectedBirthDate.isNullOrBlank()) {
                view.findViewById<TextInputEditText>(R.id.etBirthDate)
                    .error = getString(R.string.child_birth_date_required)
                return@setOnClickListener
            }

            btnSave.isEnabled = false
            btnSave.setText(R.string.child_saving)

            updateChild(c.id, name, selectedBirthDate, hasCustody, btnSave)
        }
    }

    private fun updateChild(
        childId: Int,
        name: String,
        birthDate: String?,
        hasCustody: Boolean,
        btnSave: MaterialButton
    ) {
        val token = PrefsHelper.getAuthToken(requireContext())
        if (token.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.child_session_expired), Toast.LENGTH_SHORT).show()
            btnSave.isEnabled = true
            btnSave.setText(R.string.action_save_changes)
            return
        }

        lifecycleScope.launch {
            try {
                val textType = "text/plain".toMediaType()
                val photoPart = createPhotoPart()
                val updated = RetrofitClient.api.updateChildWithPhoto(
                    "Bearer $token",
                    childId,
                    name.toRequestBody(textType),
                    birthDate.orEmpty().toRequestBody(textType),
                    hasCustody.toString().toRequestBody(textType),
                    photoPart
                )

                Toast.makeText(requireContext(), getString(R.string.child_update_success), Toast.LENGTH_SHORT).show()

                // Passa o resultado para o fragment anterior via savedStateHandle
                findNavController()
                    .previousBackStackEntry
                    ?.savedStateHandle
                    ?.set(RESULT_UPDATED_CHILD, updated)

                findNavController().popBackStack()
            } catch (e: retrofit2.HttpException) {
                val errorBody = e.response()?.errorBody()?.string()
                Toast.makeText(
                    requireContext(),
                    getString(R.string.child_update_http_error, e.code(), errorBody ?: e.message.orEmpty()),
                    Toast.LENGTH_LONG
                ).show()
                btnSave.isEnabled = true
                btnSave.setText(R.string.action_save_changes)
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.child_update_error, e.message.orEmpty()),
                    Toast.LENGTH_SHORT
                ).show()
                btnSave.isEnabled = true
                btnSave.setText(R.string.action_save_changes)
            }
        }
    }

    private suspend fun createPhotoPart(): MultipartBody.Part? {
        val uri = selectedPhotoUri ?: return null
        return withContext(Dispatchers.IO) {
            val resolver = requireContext().contentResolver
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalArgumentException(getString(R.string.child_photo_read_error))
            if (bytes.size > 5 * 1024 * 1024) {
                throw IllegalArgumentException(getString(R.string.child_photo_size_error))
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
    }

    private fun formatDateForDisplay(dateStr: String): String = try {
        val input = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val output = SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("pt-BR"))
        val date: Date? = input.parse(dateStr)
        if (date != null) output.format(date) else dateStr
    } catch (_: Exception) {
        dateStr
    }
}
