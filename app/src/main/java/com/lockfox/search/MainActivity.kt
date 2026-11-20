package com.lockfox.search

import android.Manifest
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.StrictMode
import android.provider.ContactsContract
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lockfox.search.data.FoxIndexRepository
import com.lockfox.search.model.Category
import com.lockfox.search.model.SearchListItem
import com.lockfox.search.model.SortOrder
import com.lockfox.search.ui.SearchAdapter
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var mainRootLayout: View
    private lateinit var llSearchBarContainer: View
    private lateinit var btnToggleTheme: ImageButton
    private lateinit var etSearch: EditText
    private lateinit var btnClear: ImageButton
    private lateinit var btnFilterCategory: View
    private lateinit var ivFilterIcon: ImageView
    private lateinit var tvFilterLabel: TextView
    private lateinit var rvResults: RecyclerView
    private lateinit var tvFooterHint: TextView
    private lateinit var repository: FoxIndexRepository
    private lateinit var adapter: SearchAdapter

    private var isDarkMode: Boolean = false
    private var currentSortOrder: SortOrder = SortOrder.RELEVANCE
    private var currentFilter: Category = Category.ALL
    private var searchJob: Job? = null

    private val filterOrder = listOf(
        Category.ALL,
        Category.APP,
        Category.CONTACT,
        Category.SMS,
        Category.CALL_LOG,
        Category.FILE,
        Category.NOTE,
        Category.SETTINGS
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mainRootLayout = findViewById(R.id.mainRootLayout)
        llSearchBarContainer = findViewById(R.id.llSearchBarContainer)
        btnToggleTheme = findViewById(R.id.btnToggleTheme)
        etSearch = findViewById(R.id.etSearch)
        btnClear = findViewById(R.id.btnClear)
        btnFilterCategory = findViewById(R.id.btnFilterCategory)
        ivFilterIcon = findViewById(R.id.ivFilterIcon)
        tvFilterLabel = findViewById(R.id.tvFilterLabel)
        rvResults = findViewById(R.id.rvResults)
        tvFooterHint = findViewById(R.id.tvFooterHint)

        val prefs = getSharedPreferences("fox_search_prefs", MODE_PRIVATE)
        isDarkMode = prefs.getBoolean("is_dark_mode", false)
        currentSortOrder = if (prefs.getString("sort_order", "relevance") == "date") SortOrder.DATE else SortOrder.RELEVANCE

        repository = FoxIndexRepository(this)
        setupRecyclerView()
        setupSearchBar()
        applyTheme()
        updateFilterUi()
        requestRequiredPermissions()

        lifecycleScope.launch {
            repository.preloadAll()
            handleIncomingIntent(intent)
            if (etSearch.text.isEmpty()) {
                runSearch("")
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        intent?.getStringExtra("initial_query")?.let { initial ->
            etSearch.setText(initial)
            etSearch.setSelection(initial.length)
        }
    }

    private fun setupRecyclerView() {
        adapter = SearchAdapter(
            onItemOpen = { executePrimaryAction(it) },
            onItemCall = { dialNumber(it) },
            onItemSms = { openFoxMessages(it, null, null) },
            onItemCopy = { copyToClipboard(it) },
            onItemSave = { saveContact(it) },
            onItemLongPress = { showContextMenu(it) }
        )
        rvResults.layoutManager = LinearLayoutManager(this)
        rvResults.adapter = adapter
        rvResults.itemAnimator = null
    }

    private fun setupSearchBar() {
        val btnPinWidgetHeader = findViewById<ImageButton>(R.id.btnPinWidgetHeader)

        btnToggleTheme.setOnClickListener {
            isDarkMode = !isDarkMode
            getSharedPreferences("fox_search_prefs", MODE_PRIVATE).edit().putBoolean("is_dark_mode", isDarkMode).apply()
            applyTheme()
        }

        etSearch.requestFocus()
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString() ?: ""
                btnClear.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                btnPinWidgetHeader?.visibility = if (query.isNotEmpty()) View.GONE else View.VISIBLE
                runSearch(query)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnFilterCategory.setOnClickListener {
            if (currentFilter != Category.ALL) {
                // Si ya hay un filtro activo, pulsar el chip permite resetear a Todo o cambiar
                showCategoryMenu(btnFilterCategory)
            } else {
                showCategoryMenu(btnFilterCategory)
            }
        }

        btnPinWidgetHeader?.setOnClickListener {
            val appWidgetManager = getSystemService(android.appwidget.AppWidgetManager::class.java)
            val myProvider = android.content.ComponentName(this, com.lockfox.search.widget.FoxClockSearchWidget::class.java)
            if (appWidgetManager.isRequestPinAppWidgetSupported) {
                appWidgetManager.requestPinAppWidget(myProvider, null, null)
            } else {
                Toast.makeText(this, getString(R.string.widget_not_supported), Toast.LENGTH_SHORT).show()
            }
        }

        btnClear.setOnClickListener {
            if (etSearch.text.isNotEmpty()) {
                etSearch.setText("")
            } else if (currentFilter != Category.ALL) {
                setCategoryFilter(Category.ALL)
            } else {
                closeAndFreeMemory()
            }
        }

        val rootView = findViewById<View>(android.R.id.content)
        rootView.viewTreeObserver.addOnGlobalLayoutListener {
            val heightDiff = rootView.rootView.height - rootView.height
            tvFooterHint.visibility = if (heightDiff > 160) View.GONE else View.VISIBLE
        }
    }

    private fun applyTheme() {
        if (isDarkMode) {
            mainRootLayout.setBackgroundColor(Color.parseColor("#000000"))
            llSearchBarContainer.setBackgroundResource(R.drawable.bg_search_bar_dark)
            etSearch.setTextColor(Color.WHITE)
            etSearch.setHintTextColor(Color.parseColor("#9DA2AB"))
            btnToggleTheme.setImageResource(R.drawable.ic_theme_light)
            btnToggleTheme.setColorFilter(Color.parseColor("#FDD663"))
            btnClear.setColorFilter(Color.parseColor("#9DA2AB"))
            tvFooterHint.setTextColor(Color.parseColor("#70757A"))
            window.statusBarColor = Color.parseColor("#000000")
            window.decorView.systemUiVisibility = window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        } else {
            mainRootLayout.setBackgroundColor(Color.parseColor("#F8F9FA"))
            llSearchBarContainer.setBackgroundResource(R.drawable.bg_search_bar)
            etSearch.setTextColor(Color.parseColor("#1F1F1F"))
            etSearch.setHintTextColor(Color.parseColor("#8E9196"))
            btnToggleTheme.setImageResource(R.drawable.ic_theme_dark)
            btnToggleTheme.setColorFilter(Color.parseColor("#5F6368"))
            btnClear.setColorFilter(Color.parseColor("#5F6368"))
            tvFooterHint.setTextColor(Color.parseColor("#9AA0A6"))
            window.statusBarColor = Color.parseColor("#F8F9FA")
            window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
        adapter.isDarkMode = isDarkMode
    }

    private fun showCategoryMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        filterOrder.forEachIndexed { index, cat ->
            val prefix = if (cat == currentFilter) "✓ " else "  "
            popup.menu.add(0, index, index, "$prefix${cat.getLocalizedTitle(this)}")
        }

        val sortRelevanceId = 100
        val sortDateId = 101
        val relevancePrefix = if (currentSortOrder == SortOrder.RELEVANCE) "✓ " else "  "
        val datePrefix = if (currentSortOrder == SortOrder.DATE) "✓ " else "  "
        popup.menu.add(1, sortRelevanceId, 100, "$relevancePrefix${getString(R.string.sort_relevance)}")
        popup.menu.add(1, sortDateId, 101, "$datePrefix${getString(R.string.sort_date)}")

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                sortRelevanceId -> {
                    currentSortOrder = SortOrder.RELEVANCE
                    getSharedPreferences("fox_search_prefs", MODE_PRIVATE).edit().putString("sort_order", "relevance").apply()
                    runSearch(etSearch.text.toString())
                    Toast.makeText(this, getString(R.string.toast_sort_relevance), Toast.LENGTH_SHORT).show()
                }
                sortDateId -> {
                    currentSortOrder = SortOrder.DATE
                    getSharedPreferences("fox_search_prefs", MODE_PRIVATE).edit().putString("sort_order", "date").apply()
                    runSearch(etSearch.text.toString())
                    Toast.makeText(this, getString(R.string.toast_sort_date), Toast.LENGTH_SHORT).show()
                }
                else -> {
                    val selected = filterOrder.getOrNull(menuItem.itemId) ?: Category.ALL
                    setCategoryFilter(selected)
                }
            }
            true
        }
        popup.show()
    }

    private fun setCategoryFilter(category: Category) {
        currentFilter = category
        updateFilterUi()
        runSearch(etSearch.text.toString())
    }

    private fun updateFilterUi() {
        if (currentFilter == Category.ALL) {
            tvFilterLabel.visibility = View.GONE
            ivFilterIcon.setImageResource(R.drawable.ic_filter)
            ivFilterIcon.setColorFilter(Color.parseColor("#5F6368"))
            btnFilterCategory.background?.mutate()?.setTint(Color.parseColor("#F1F3F4"))
        } else {
            tvFilterLabel.visibility = View.VISIBLE
            tvFilterLabel.text = "${currentFilter.getLocalizedTitle(this)} ✕"
            tvFilterLabel.setTextColor(currentFilter.fgColor)
            ivFilterIcon.setImageResource(currentFilter.iconRes)
            ivFilterIcon.setColorFilter(currentFilter.fgColor)
            btnFilterCategory.background?.mutate()?.setTint(currentFilter.bgPastelColor)
        }
    }

    private fun cycleCategoryFilter(direction: Int) {
        val currentIndex = filterOrder.indexOf(currentFilter)
        val nextIndex = (currentIndex + direction + filterOrder.size) % filterOrder.size
        setCategoryFilter(filterOrder[nextIndex])
    }

    private fun runSearch(query: String) {
        searchJob?.cancel()
        searchJob = lifecycleScope.launch {
            val results = repository.search(query, currentFilter, currentSortOrder)
            adapter.submitList(results, query)
            if (adapter.selectedPosition != RecyclerView.NO_POSITION) {
                rvResults.scrollToPosition(adapter.selectedPosition)
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    hideSoftKeyboard()
                    val pos = adapter.moveSelection(1)
                    if (pos != RecyclerView.NO_POSITION) rvResults.smoothScrollToPosition(pos)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    hideSoftKeyboard()
                    val pos = adapter.moveSelection(-1)
                    if (pos != RecyclerView.NO_POSITION) rvResults.smoothScrollToPosition(pos)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (!etSearch.hasFocus() || etSearch.text.isEmpty()) {
                        cycleCategoryFilter(-1)
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (!etSearch.hasFocus() || etSearch.text.isEmpty()) {
                        cycleCategoryFilter(1)
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    when (val selected = adapter.getSelectedItem()) {
                        is SearchListItem.NumberAction -> {
                            dialNumber(selected.number)
                            return true
                        }
                        is SearchListItem.Result -> {
                            if (event.isLongPress) {
                                showContextMenu(selected)
                            } else {
                                executePrimaryAction(selected)
                            }
                            return true
                        }
                        else -> {}
                    }
                }
                KeyEvent.KEYCODE_CALL -> {
                    when (val selected = adapter.getSelectedItem()) {
                        is SearchListItem.NumberAction -> {
                            dialNumber(selected.number)
                            return true
                        }
                        is SearchListItem.Result -> {
                            if (!selected.phoneNumber.isNullOrBlank()) {
                                dialNumber(selected.phoneNumber)
                                return true
                            }
                        }
                        else -> {}
                    }
                }
                KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ENDCALL -> {
                    val text = etSearch.text
                    if (!text.isNullOrEmpty()) {
                        text.delete(text.length - 1, text.length)
                    } else if (currentFilter != Category.ALL) {
                        setCategoryFilter(Category.ALL)
                    } else {
                        closeAndFreeMemory()
                    }
                    return true
                }
                KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_NUMPAD_1 -> {
                    if (!etSearch.hasFocus() && handlePhysicalShortcut(1)) return true
                }
                KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_NUMPAD_2 -> {
                    if (!etSearch.hasFocus() && handlePhysicalShortcut(2)) return true
                }
                KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_NUMPAD_3 -> {
                    if (!etSearch.hasFocus() && handlePhysicalShortcut(3)) return true
                }
                KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_NUMPAD_4 -> {
                    if (!etSearch.hasFocus() && handlePhysicalShortcut(4)) return true
                }
            }

            // Teclas físicas numéricas o símbolos en cualquier momento
            if (!etSearch.hasFocus()) {
                val unicode = event.unicodeChar
                if (unicode != 0 && (unicode.toChar().isDigit() || unicode.toChar() in "*#+")) {
                    etSearch.append(unicode.toChar().toString())
                    etSearch.requestFocus()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun handlePhysicalShortcut(actionType: Int): Boolean {
        val selected = adapter.getSelectedItem() ?: return false
        val targetNumber = when (selected) {
            is SearchListItem.NumberAction -> selected.number
            is SearchListItem.Result -> selected.phoneNumber
            else -> null
        }

        return when (actionType) {
            1 -> { // 1: Llamar
                if (!targetNumber.isNullOrBlank()) {
                    dialNumber(targetNumber)
                    true
                } else false
            }
            2 -> { // 2: SMS
                if (!targetNumber.isNullOrBlank()) {
                    val threadId = if (selected is SearchListItem.Result && selected.category == Category.SMS) selected.extraData?.substringBefore("|") else null
                    val msgId = if (selected is SearchListItem.Result && selected.category == Category.SMS) selected.id.removePrefix("sms_") else null
                    openFoxMessages(targetNumber, threadId, null, msgId)
                    true
                } else false
            }
            3 -> { // 3: Copiar
                if (selected is SearchListItem.Result && selected.category == Category.SMS) {
                    copyToClipboard(selected.subtitle)
                    true
                } else if (!targetNumber.isNullOrBlank()) {
                    copyToClipboard(targetNumber)
                    true
                } else if (selected is SearchListItem.Result) {
                    copyToClipboard(selected.title)
                    true
                } else false
            }
            4 -> { // 4: Guardar
                if (!targetNumber.isNullOrBlank()) {
                    saveContact(targetNumber)
                    true
                } else false
            }
            else -> false
        }
    }

    private fun executePrimaryAction(item: SearchListItem.Result) {
        when (item.category) {
            Category.ACTION, Category.SETTINGS -> {
                val extra = item.extraData ?: return
                when {
                    extra.startsWith("COPY:") -> copyToClipboard(extra.removePrefix("COPY:"))
                    extra.startsWith("NOTE:") -> {
                        repository.saveQuickNote(extra.removePrefix("NOTE:"))
                        Toast.makeText(this, getString(R.string.note_saved), Toast.LENGTH_SHORT).show()
                        etSearch.setText("")
                    }
                    extra.startsWith("SMS:") -> {
                        val payload = extra.removePrefix("SMS:").split("|", limit = 2)
                        openFoxMessages(payload[0], null, payload.getOrNull(1))
                    }
                    extra.startsWith("CALL:") -> dialNumber(extra.removePrefix("CALL:"))
                    extra == "PIN_WIDGET" -> {
                        val appWidgetManager = getSystemService(android.appwidget.AppWidgetManager::class.java)
                        val myProvider = android.content.ComponentName(this, com.lockfox.search.widget.FoxSearchWidget::class.java)
                        if (appWidgetManager.isRequestPinAppWidgetSupported) {
                            appWidgetManager.requestPinAppWidget(myProvider, null, null)
                        } else {
                            Toast.makeText(this, getString(R.string.widget_not_supported), Toast.LENGTH_SHORT).show()
                        }
                    }
                    extra == "OPEN_LOCALE_SETTINGS" -> {
                        startActivity(Intent(android.provider.Settings.ACTION_LOCALE_SETTINGS))
                    }
                    extra.startsWith("SAVE_CONTACT:") -> {
                        saveContact(extra.removePrefix("SAVE_CONTACT:"))
                    }
                    else -> {
                        // Puede ser paquete de settings o app de ajustes
                        val pkg = item.extraData
                        if (!pkg.isNullOrBlank()) {
                            packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it) }
                        }
                    }
                }
            }
            Category.CONTACT -> {
                val contactId = item.extraData?.toLongOrNull() ?: return
                val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, contactId.toString())
                startActivity(Intent(Intent.ACTION_VIEW, uri))
            }
            Category.SMS -> {
                val threadId = item.extraData?.substringBefore("|")
                val msgId = item.id.removePrefix("sms_").ifBlank {
                    item.extraData?.substringAfter("|", "") ?: ""
                }
                openFoxMessages(item.phoneNumber, threadId, null, msgId)
            }
            Category.CALL_LOG -> dialNumber(item.phoneNumber)
            Category.APP -> {
                val pkg = item.extraData ?: return
                packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it) }
            }
            Category.NOTE -> {
                AlertDialog.Builder(this)
                    .setTitle(item.title)
                    .setMessage(item.extraData)
                    .setPositiveButton(getString(R.string.action_copy)) { _, _ -> copyToClipboard(item.extraData ?: "") }
                    .setNegativeButton(getString(R.string.action_close), null)
                    .show()
            }
            Category.FILE -> openLocalFile(item.extraData)
            Category.ALL -> {}
        }
    }

    private fun openFoxMessages(address: String?, threadId: String?, body: String?, messageId: String? = null) {
        val msgIdLong = messageId?.toLongOrNull()
        val threadIdLong = threadId?.toLongOrNull()

        // 1. Intent para FoxMessages (si está instalada - ecosistema Argentina)
        val foxIntent = Intent(Intent.ACTION_VIEW).apply {
            setPackage("com.lockfox.messages")
            data = Uri.parse("sms:${address ?: ""}")
            if (threadIdLong != null) {
                putExtra("thread_id", threadIdLong)
                putExtra("threadId", threadIdLong)
            }
            if (msgIdLong != null) {
                putExtra("searched_message_id", msgIdLong)
                putExtra("select_id", msgIdLong)
                putExtra("message_id", msgIdLong)
                putExtra("highlight", msgIdLong)
            }
            if (!body.isNullOrBlank()) putExtra("sms_body", body)
        }

        try {
            startActivity(foxIntent)
            return
        } catch (_: Exception) {
            // FoxMessages no está instalada (por ej. ROM stock o usuarios de Israel)
        }

        // 2. Intent para la app de SMS por defecto del sistema (Israel / ROM stock)
        if (msgIdLong != null) {
            val viewMsgIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse("content://sms/$msgIdLong"), "vnd.android-dir/mms-sms")
                if (threadIdLong != null) {
                    putExtra("thread_id", threadIdLong)
                    putExtra("threadId", threadIdLong)
                }
                putExtra("searched_message_id", msgIdLong)
                putExtra("select_id", msgIdLong)
                putExtra("message_id", msgIdLong)
                putExtra("highlight", msgIdLong)
            }
            try {
                startActivity(viewMsgIntent)
                return
            } catch (_: Exception) {}

            val viewMsgIntent2 = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("content://sms/$msgIdLong")
                if (threadIdLong != null) {
                    putExtra("thread_id", threadIdLong)
                    putExtra("threadId", threadIdLong)
                }
                putExtra("searched_message_id", msgIdLong)
                putExtra("select_id", msgIdLong)
                putExtra("message_id", msgIdLong)
                putExtra("highlight", msgIdLong)
            }
            try {
                startActivity(viewMsgIntent2)
                return
            } catch (_: Exception) {}
        }

        // 3. Fallback estándar si content://sms no es soportado
        val defaultIntent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("sms:${address ?: ""}")
            if (threadIdLong != null) {
                putExtra("thread_id", threadIdLong)
                putExtra("threadId", threadIdLong)
            }
            if (msgIdLong != null) {
                putExtra("searched_message_id", msgIdLong)
                putExtra("select_id", msgIdLong)
                putExtra("message_id", msgIdLong)
                putExtra("highlight", msgIdLong)
            }
            if (!body.isNullOrBlank()) putExtra("sms_body", body)
        }
        try {
            startActivity(defaultIntent)
        } catch (_: Exception) {
            val fallback = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${address ?: ""}")).apply {
                if (threadIdLong != null) putExtra("thread_id", threadIdLong)
                if (msgIdLong != null) putExtra("select_id", msgIdLong)
            }
            try {
                startActivity(fallback)
            } catch (e: Exception) {
                Toast.makeText(this, getString(R.string.no_app_to_open_file), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun dialNumber(phone: String?) {
        if (phone.isNullOrBlank()) return
        val uri = Uri.parse("tel:${Uri.encode(phone)}")
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            startActivity(Intent(Intent.ACTION_CALL, uri))
        } else {
            startActivity(Intent(Intent.ACTION_DIAL, uri))
        }
    }

    private fun saveContact(phone: String) {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            type = ContactsContract.Contacts.CONTENT_TYPE
            putExtra(ContactsContract.Intents.Insert.PHONE, phone)
        }
        startActivity(intent)
    }

    private fun openLocalFile(path: String?) {
        if (path.isNullOrBlank()) return
        try {
            val builder = StrictMode.VmPolicy.Builder()
            StrictMode.setVmPolicy(builder.build())
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.fromFile(File(path)), "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, getString(R.string.no_app_to_open_file), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showContextMenu(item: SearchListItem.Result) {
        val options = mutableListOf(getString(R.string.action_copy_text), getString(R.string.action_share))
        if (!item.phoneNumber.isNullOrBlank()) {
            options.add("${getString(R.string.action_copy)} (${item.phoneNumber})")
            options.add(getString(R.string.action_save_in_contacts))
        }

        AlertDialog.Builder(this)
            .setTitle(item.title)
            .setItems(options.toTypedArray()) { _, which ->
                when (which) {
                    0 -> copyToClipboard("${item.title}\n${item.subtitle}")
                    1 -> {
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "${item.title}: ${item.subtitle}")
                        }
                        startActivity(Intent.createChooser(share, getString(R.string.action_share)))
                    }
                    2 -> copyToClipboard(item.phoneNumber ?: "")
                    3 -> saveContact(item.phoneNumber ?: "")
                }
            }
            .show()
    }

    private fun copyToClipboard(text: String) {
        val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cb.setPrimaryClip(ClipData.newPlainText("Fox Search", text))
        Toast.makeText(this, getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
    }

    private fun hideSoftKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(etSearch.windowToken, 0)
    }

    private fun closeAndFreeMemory() {
        finishAndRemoveTask()
    }

    private fun requestRequiredPermissions() {
        val perms = mutableListOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
        try {
            val requested = packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS).requestedPermissions
            if (requested?.contains(Manifest.permission.READ_SMS) == true) {
                perms.add(Manifest.permission.READ_SMS)
            }
            if (requested?.contains(Manifest.permission.CALL_PHONE) == true) {
                perms.add(Manifest.permission.CALL_PHONE)
            }
        } catch (_: Exception) {}

        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 101)
        }
    }
}
