package com.example.chatapp.ui

import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.res.use
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.chatapp.Child
import com.example.chatapp.CreateChildRequest
import com.example.chatapp.LoginActivity
import com.example.chatapp.PdfDownloadHelper
import com.example.chatapp.PrefsHelper
import com.example.chatapp.R
import com.example.chatapp.RetrofitClient
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ProfileFragment : Fragment() {

    private var childrenContainer: LinearLayout? = null
    private var progressLoadingChildren: View? = null
    private var errorStateChildren: View? = null
    private var childPhotoCallback: ((Uri) -> Unit)? = null

    private val photoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            saveProfilePhoto(uri)
        }
    }

    private val childPhotoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            childPhotoCallback?.invoke(uri)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_profile, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val username = PrefsHelper.getUsername(requireContext())
        val displayName = username.replaceFirstChar { it.uppercase() }

        // Avatar e nome do usuário
        view.findViewById<TextView>(R.id.tvUserName)?.text = displayName
        if (displayName.isNotEmpty()) {
            view.findViewById<TextView>(R.id.tvUserAvatar)?.text = displayName.first().toString()
        }

        // Subtítulo dinâmico baseado nos filhos
        val childrenNames = PrefsHelper.getChildrenNames(requireContext())
        val subtitle = if (childrenNames.isNotEmpty()) {
            "Responsável por $childrenNames"
        } else {
            "CoParent Lite"
        }
        view.findViewById<TextView>(R.id.tvUserSubtitle)?.text = subtitle

        // Foto de perfil
        view.findViewById<View>(R.id.avatarContainer)?.setOnClickListener {
            photoPickerLauncher.launch("image/*")
        }
        loadProfilePhoto(view)

        // Editar perfil ao clicar no nome
        view.findViewById<TextView>(R.id.tvUserName)?.setOnClickListener { showEditProfileDialog() }
        view.findViewById<TextView>(R.id.tvUserSubtitle)?.setOnClickListener { showEditProfileDialog() }

        // Dados dinâmicos do coparental
        renderCoparent(view, PrefsHelper.getOtherParentName(requireContext()))
        loadCoparent(view)

        // Children
        childrenContainer = view.findViewById(R.id.childrenContainer)
        progressLoadingChildren = view.findViewById(R.id.progressLoadingChildren)
        errorStateChildren = view.findViewById(R.id.errorStateChildren)
        view.findViewById<View>(R.id.btnAddChild)?.setOnClickListener {
            showAddChildDialog()
        }
        view.findViewById<View>(R.id.btnRetryChildren)?.setOnClickListener {
            loadChildren()
        }
        loadChildren()

        // Notificações
        view.findViewById<View>(R.id.rowNotifications)?.setOnClickListener {
            try {
                findNavController().navigate(R.id.notificationSettingsFragment)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Erro ao abrir notificações", Toast.LENGTH_SHORT).show()
            }
        }

        // Exportar dados
        view.findViewById<View>(R.id.rowExport)?.setOnClickListener {
            val authToken = PrefsHelper.getAuthToken(requireContext())
            if (authToken.isEmpty()) {
                Toast.makeText(requireContext(), "Faça login para exportar", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val conversationId = PrefsHelper.getConversationId(requireContext())
            Toast.makeText(requireContext(), "Iniciando download do PDF...", Toast.LENGTH_SHORT).show()
            PdfDownloadHelper.downloadConversationPdf(
                requireContext(),
                authToken,
                conversationId,
                viewLifecycleOwner.lifecycleScope
            )
        }

        // Privacidade
        view.findViewById<View>(R.id.rowPrivacy)?.setOnClickListener {
            showPrivacyDialog()
        }

        // Logout
        view.findViewById<View>(R.id.rowLogout)?.setOnClickListener {
            confirmLogout()
        }

        // Convidar outro pai
        view.findViewById<View>(R.id.btnInvite)?.setOnClickListener {
            shareInviteLink()
        }
    }

    // ── Children ────────────────────────────────────────────

    private fun loadCoparent(view: View) {
        val token = PrefsHelper.getAuthToken(requireContext())
        if (token.isEmpty()) return

        lifecycleScope.launch {
            try {
                val conversationId = PrefsHelper.getConversationId(requireContext())
                val conversation = RetrofitClient.api.getConversations("Bearer $token")
                    .firstOrNull { it.id == conversationId }
                    ?: return@launch
                val name = conversation.participants.firstOrNull { !it.is_me }?.username.orEmpty()
                PrefsHelper.saveOtherParentName(requireContext(), name)
                renderCoparent(view, name)
            } catch (_: Exception) {
                // Keep the locally known state while offline.
            }
        }
    }

    private fun renderCoparent(view: View, name: String) {
        val connectedContent = view.findViewById<View>(R.id.coparentConnectedContent)
        val emptyState = view.findViewById<View>(R.id.emptyStateCoparent)
        if (name.isBlank()) {
            connectedContent?.visibility = View.GONE
            emptyState?.visibility = View.VISIBLE
            return
        }

        val displayOther = name.replaceFirstChar { it.uppercase() }
        connectedContent?.visibility = View.VISIBLE
        emptyState?.visibility = View.GONE
        view.findViewById<TextView>(R.id.tvCoparentName)?.text = displayOther
        view.findViewById<TextView>(R.id.tvCoparentAvatar)?.text =
            displayOther.firstOrNull()?.uppercase() ?: getString(R.string.chat_avatar_fallback)
    }

    private fun loadChildren() {
        showChildrenLoading()
        val token = PrefsHelper.getAuthToken(requireContext())
        val conversationId = PrefsHelper.getConversationId(requireContext())
        android.util.Log.d("ProfileFragment", "loadChildren called: token=${token.take(10)}..., convId=$conversationId")
        if (token.isEmpty()) {
            android.util.Log.w("ProfileFragment", "loadChildren: token empty, skipping")
            showChildrenError()
            return
        }

        lifecycleScope.launch {
            try {
                val children = RetrofitClient.api.getChildren("Bearer $token", conversationId)
                android.util.Log.d("ProfileFragment", "loadChildren success: ${children.size} filhos")
                renderChildren(children)
            } catch (e: retrofit2.HttpException) {
                val errorBody = e.response()?.errorBody()?.string()
                android.util.Log.e("ProfileFragment", "loadChildren HTTP ${e.code()}: $errorBody")
                showChildrenError()
            } catch (e: Exception) {
                android.util.Log.e("ProfileFragment", "loadChildren error: ${e.message}")
                showChildrenError()
            }
        }
    }

    private fun renderChildren(children: List<Child>) {
        val container = childrenContainer ?: return
        progressLoadingChildren?.visibility = View.GONE
        errorStateChildren?.visibility = View.GONE
        container.visibility = View.VISIBLE
        container.removeAllViews()

        // Atualizar subtítulo e prefs com nomes atualizados
        if (children.isNotEmpty()) {
            val names = children.joinToString(", ") { it.name }
            PrefsHelper.saveChildrenNames(requireContext(), names)
            view?.findViewById<TextView>(R.id.tvUserSubtitle)?.text =
                getString(R.string.profile_responsible_for, names)
        } else {
            PrefsHelper.saveChildrenNames(requireContext(), "")
            view?.findViewById<TextView>(R.id.tvUserSubtitle)?.setText(R.string.ui_coparent_lite)
        }

        if (children.isEmpty()) {
            val ctx = requireContext()
            val dp = { value: Int -> (value * ctx.resources.displayMetrics.density).toInt() }

            val emptyText = TextView(ctx).apply {
                text = getString(R.string.children_empty)
                textSize = 14f
                setTextColor(ContextCompat.getColor(ctx, R.color.gray_400))
                setPadding(0, dp(4), 0, dp(4))
                accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            }
            container.addView(emptyText)
            return
        }

        for (child in children) {
            container.addView(createChildRow(child))
        }
    }

    private fun showChildrenLoading() {
        childrenContainer?.visibility = View.GONE
        errorStateChildren?.visibility = View.GONE
        progressLoadingChildren?.visibility = View.VISIBLE
    }

    private fun showChildrenError() {
        childrenContainer?.visibility = View.GONE
        progressLoadingChildren?.visibility = View.GONE
        errorStateChildren?.visibility = View.VISIBLE
    }

    private fun createChildRow(child: Child): View {
        val ctx = requireContext()
        val dp = { value: Int -> (value * ctx.resources.displayMetrics.density).toInt() }

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            // Click na row inteira abre a tela de detalhes
            isClickable = true
            isFocusable = true
            background = ctx.obtainStyledAttributes(
                intArrayOf(android.R.attr.selectableItemBackground)
            ).use { it.getDrawable(0) }
            setOnClickListener {
                openChildDetail(child)
            }
        }

        // Avatar
        val avatarSize = dp(40)
        val avatarFrame = android.widget.FrameLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(avatarSize, avatarSize).apply {
                marginEnd = dp(12)
            }
            background = ContextCompat.getDrawable(ctx, R.drawable.bg_avatar_child)
        }

        val initial = if (child.name.isNotEmpty()) child.name.first().uppercase() else "?"
        val avatarText = TextView(ctx).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            )
            gravity = android.view.Gravity.CENTER
            text = initial
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(ctx, R.color.warning))
        }
        avatarFrame.addView(avatarText)
        row.addView(avatarFrame)

        // Text container
        val textContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val tvName = TextView(ctx).apply {
            text = child.name
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(ctx, R.color.gray_900))
        }
        textContainer.addView(tvName)

        if (!child.birth_date.isNullOrEmpty()) {
            val tvBirth = TextView(ctx).apply {
                text = formatBirthDate(child.birth_date)
                textSize = 13f
                setTextColor(ContextCompat.getColor(ctx, R.color.gray_500))
            }
            textContainer.addView(tvBirth)
        }

        if (child.has_custody) {
            val tvCustody = TextView(ctx).apply {
                text = getString(R.string.profile_has_custody)
                textSize = 12f
                setTextColor(ContextCompat.getColor(ctx, R.color.secondary_green))
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            textContainer.addView(tvCustody)
        }

        row.addView(textContainer)

        // Delete button (X)
        val btnDelete = android.widget.ImageButton(ctx).apply {
            val size = resources.getDimensionPixelSize(R.dimen.touch_target_min)
            layoutParams = LinearLayout.LayoutParams(size, size)
            setImageResource(R.drawable.ic_close)
            setColorFilter(ContextCompat.getColor(ctx, R.color.gray_400))
            setBackgroundResource(android.R.color.transparent)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            contentDescription = getString(R.string.cd_remove_child, child.name)
            setOnClickListener { confirmDeleteChild(child) }
        }
        row.addView(btnDelete)

        return row
    }

    private fun formatBirthDate(dateStr: String): String {
        return try {
            val input = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val output = SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("pt-BR"))
            val date = input.parse(dateStr)
            if (date != null) output.format(date) else dateStr
        } catch (e: Exception) {
            dateStr
        }
    }

    private fun showAddChildDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_add_child, null)

        val etName = dialogView.findViewById<EditText>(R.id.etChildName)
        val etBirthDate = dialogView.findViewById<EditText>(R.id.etChildBirthDate)
        val etCpf = dialogView.findViewById<EditText>(R.id.etChildCpf)
        val etRg = dialogView.findViewById<EditText>(R.id.etChildRg)
        val cbHasCustody = dialogView.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.cbHasCustody)
        val btnChildPhoto = dialogView.findViewById<View>(R.id.btnChildPhoto)
        val ivChildPhoto = dialogView.findViewById<ImageView>(R.id.ivChildPhoto)
        val ivCameraIcon = dialogView.findViewById<ImageView>(R.id.ivCameraIcon)
        val tvPhotoLabel = dialogView.findViewById<android.widget.TextView>(R.id.tvPhotoLabel)

        var selectedDate: String? = null
        var selectedPhotoUri: Uri? = null

        // Máscara simples de CPF
        etCpf.addTextChangedListener(object : android.text.TextWatcher {
            private var isUpdating = false
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                if (isUpdating) return
                isUpdating = true
                val digits = s.toString().replace(Regex("[^0-9]"), "")
                val formatted = when {
                    digits.length > 9 -> "${digits.substring(0,3)}.${digits.substring(3,6)}.${digits.substring(6,9)}-${digits.substring(9, minOf(digits.length, 11))}"
                    digits.length > 6 -> "${digits.substring(0,3)}.${digits.substring(3,6)}.${digits.substring(6)}"
                    digits.length > 3 -> "${digits.substring(0,3)}.${digits.substring(3)}"
                    else -> digits
                }
                etCpf.setText(formatted)
                etCpf.setSelection(formatted.length)
                isUpdating = false
            }
        })

        // Foto picker
        btnChildPhoto.setOnClickListener {
            childPhotoCallback = { uri ->
                selectedPhotoUri = uri
                ivChildPhoto.setImageURI(uri)
                ivChildPhoto.visibility = View.VISIBLE
                ivCameraIcon.visibility = View.GONE
                tvPhotoLabel.visibility = View.GONE
            }
            childPhotoPickerLauncher.launch("image/*")
        }

        etBirthDate.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(
                requireContext(),
                { _, year, month, day ->
                    selectedDate = String.format(Locale.ROOT, "%04d-%02d-%02d", year, month + 1, day)
                    etBirthDate.setText(String.format(Locale.forLanguageTag("pt-BR"), "%02d/%02d/%04d", day, month + 1, year))
                },
                cal.get(Calendar.YEAR) - 5,
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Adicionar filho(a)")
            .setView(dialogView)
            .setPositiveButton("Salvar", null)
            .setNegativeButton("Cancelar", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = etName.text.toString().trim()
                etName.error = null
                etBirthDate.error = null

                var isValid = true
                if (name.isEmpty()) {
                    etName.error = getString(R.string.child_name_required)
                    isValid = false
                }
                if (selectedDate == null) {
                    etBirthDate.error = getString(R.string.child_birth_date_required)
                    isValid = false
                }
                if (!isValid) return@setOnClickListener

                val cpf = etCpf.text.toString().trim().ifEmpty { null }
                val rg = etRg.text.toString().trim().ifEmpty { null }
                val hasCustody = cbHasCustody.isChecked
                createChild(name, selectedDate!!, cpf, rg, hasCustody) {
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    private fun createChild(
        name: String,
        birthDate: String,
        cpf: String?,
        rg: String?,
        hasCustody: Boolean,
        onSuccess: () -> Unit
    ) {
        val token = PrefsHelper.getAuthToken(requireContext())
        if (token.isEmpty()) return

        lifecycleScope.launch {
            try {
                val conversationId = PrefsHelper.getConversationId(requireContext())
                
                // Validar entrada
                if (name.isBlank()) {
                    Toast.makeText(requireContext(), "Nome é obrigatório", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                if (name.length > 100) {
                    Toast.makeText(requireContext(), "Nome máximo 100 caracteres", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                // Criar request tipado (sem Map<String, Any>)
                val request = CreateChildRequest(
                    name = name.trim(),
                    conversationId = conversationId,
                    birthDate = birthDate.trim(),
                    cpf = cpf,
                    rg = rg,
                    hasCustody = hasCustody
                )

                android.util.Log.d("ProfileFragment", "createChild: convId=$conversationId, name=$name, birthDate=$birthDate")
                val result = RetrofitClient.api.createChild("Bearer $token", request)
                android.util.Log.d("ProfileFragment", "createChild success: ${result.id} ${result.name}")
                Toast.makeText(requireContext(), "Filho(a) adicionado(a)", Toast.LENGTH_SHORT).show()
                
                // Recarregar lista após criação confirmada
                val children = RetrofitClient.api.getChildren("Bearer $token", conversationId)
                android.util.Log.d("ProfileFragment", "loadChildren after create: ${children.size} filhos")
                renderChildren(children)
                onSuccess()
            } catch (e: retrofit2.HttpException) {
                val errorBody = e.response()?.errorBody()?.string()
                android.util.Log.e("ProfileFragment", "createChild HTTP ${e.code()}: $errorBody")
                Toast.makeText(requireContext(), "Erro ${e.code()}: $errorBody", Toast.LENGTH_LONG).show()
            } catch (e: IllegalArgumentException) {
                android.util.Log.e("ProfileFragment", "createChild validation error: ${e.message}")
                Toast.makeText(requireContext(), "Validação falhou: ${e.message}", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.util.Log.e("ProfileFragment", "createChild error: ${e.message}")
                Toast.makeText(requireContext(), "Erro ao adicionar: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun confirmDeleteChild(child: Child) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Remover filho(a)")
            .setMessage("Deseja remover ${child.name}?")
            .setPositiveButton("Remover") { _, _ -> deleteChild(child.id) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /**
     * Abre a tela de detalhes do filho passando o objeto via Bundle (Serializable).
     */
    private fun openChildDetail(child: Child) {
        try {
            val bundle = Bundle().apply {
                putSerializable(ChildDetailFragment.ARG_CHILD, child)
            }
            findNavController().navigate(R.id.childDetailFragment, bundle)
        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                "Erro ao abrir detalhes: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun deleteChild(childId: Int) {
        val token = PrefsHelper.getAuthToken(requireContext())
        if (token.isEmpty()) return

        lifecycleScope.launch {
            try {
                val conversationId = PrefsHelper.getConversationId(requireContext())
                RetrofitClient.api.deleteChild("Bearer $token", childId)
                Toast.makeText(requireContext(), "Removido", Toast.LENGTH_SHORT).show()
                loadChildren()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Erro ao remover: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── Foto de perfil ──────────────────────────────────

    private fun saveProfilePhoto(uri: Uri) {
        try {
            val ctx = requireContext()
            val inputStream = ctx.contentResolver.openInputStream(uri) ?: return
            val file = java.io.File(ctx.filesDir, "profile_photo.jpg")
            file.outputStream().use { output -> inputStream.copyTo(output) }
            inputStream.close()

            // Save path
            ctx.getSharedPreferences("coparent", android.content.Context.MODE_PRIVATE)
                .edit().putString("profile_photo_path", file.absolutePath).apply()

            // Update UI
            view?.let { loadProfilePhoto(it) }
            Toast.makeText(ctx, "Foto atualizada", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Erro ao salvar foto", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadProfilePhoto(view: View) {
        val path = requireContext().getSharedPreferences("coparent", android.content.Context.MODE_PRIVATE)
            .getString("profile_photo_path", null)

        val ivPhoto = view.findViewById<ImageView>(R.id.ivProfilePhoto)
        val tvAvatar = view.findViewById<TextView>(R.id.tvUserAvatar)

        if (path != null) {
            val file = java.io.File(path)
            if (file.exists()) {
                val bitmap = BitmapFactory.decodeFile(path)
                ivPhoto?.setImageBitmap(bitmap)
                ivPhoto?.visibility = View.VISIBLE
                tvAvatar?.visibility = View.GONE
                return
            }
        }
        ivPhoto?.visibility = View.GONE
        tvAvatar?.visibility = View.VISIBLE
    }

    // ── Editar perfil ───────────────────────────────────

    private fun showEditProfileDialog() {
        val token = PrefsHelper.getAuthToken(requireContext())
        if (token.isEmpty()) return

        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_edit_profile, null)

        val etFirstName = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etFirstName)
        val etLastName = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etLastName)
        val etEmail = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etEmail)

        // Carregar dados atuais
        lifecycleScope.launch {
            try {
                val profile = RetrofitClient.api.getProfile("Bearer $token")
                etFirstName.setText((profile["first_name"] as? String) ?: "")
                etLastName.setText((profile["last_name"] as? String) ?: "")
                etEmail.setText((profile["email"] as? String) ?: "")
            } catch (_: Exception) {}
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Editar perfil")
            .setView(dialogView)
            .setPositiveButton("Salvar") { _, _ ->
                val firstName = etFirstName.text?.toString()?.trim() ?: ""
                val lastName = etLastName.text?.toString()?.trim() ?: ""
                val email = etEmail.text?.toString()?.trim() ?: ""
                updateProfile(firstName, lastName, email)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun updateProfile(firstName: String, lastName: String, email: String) {
        val token = PrefsHelper.getAuthToken(requireContext())
        if (token.isEmpty()) return

        lifecycleScope.launch {
            try {
                RetrofitClient.api.updateProfile(
                    "Bearer $token",
                    mapOf(
                        "first_name" to firstName,
                        "last_name" to lastName,
                        "email" to email
                    )
                )

                // Atualizar nome na tela
                val displayName = if (firstName.isNotEmpty()) {
                    "$firstName $lastName".trim()
                } else {
                    PrefsHelper.getUsername(requireContext()).replaceFirstChar { it.uppercase() }
                }
                view?.findViewById<TextView>(R.id.tvUserName)?.text = displayName

                Toast.makeText(requireContext(), "Perfil atualizado", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Erro: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── Privacidade ──────────────────────────────────────

    private fun showPrivacyDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_privacy, null)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Pol\u00edtica de Privacidade")
            .setView(dialogView)
            .setPositiveButton("Fechar", null)
            .show()
    }

    // ── Convite ──────────────────────────────────────────

    private fun shareInviteLink() {
        val username = PrefsHelper.getUsername(requireContext())
        val displayName = username.replaceFirstChar { it.uppercase() }
        val conversationId = PrefsHelper.getConversationId(requireContext())

        val inviteText = """
            |$displayName convidou você para o CoParent Lite!
            |
            |Baixe o app e entre com o código de convite: CONV-$conversationId
            |
            |CoParent Lite — Comunicação clara e confiável entre pais.
        """.trimMargin()

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Convite para CoParent Lite")
            putExtra(Intent.EXTRA_TEXT, inviteText)
        }
        startActivity(Intent.createChooser(shareIntent, "Enviar convite via"))
    }

    // ── Logout ──────────────────────────────────────────────

    private fun confirmLogout() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Sair")
            .setMessage("Deseja realmente sair da conta?")
            .setPositiveButton("Sair") { _, _ ->
                PrefsHelper.clearAll(requireContext())

                val intent = Intent(requireContext(), LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                requireActivity().finish()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
