package com.lockfox.search.data

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Environment
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.lockfox.search.R
import com.lockfox.search.engine.CommandParser
import com.lockfox.search.engine.T9Engine
import com.lockfox.search.model.Category
import com.lockfox.search.model.SearchListItem
import com.lockfox.search.model.SortOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FoxIndexRepository(private val context: Context) {

    private var cachedContacts = emptyList<SearchListItem.Result>()
    private var cachedSms = emptyList<SearchListItem.Result>()
    private var cachedCalls = emptyList<SearchListItem.Result>()
    private var cachedApps = emptyList<SearchListItem.Result>()
    private var cachedSettings = emptyList<SearchListItem.Result>()
    private var cachedFiles = emptyList<SearchListItem.Result>()
    private var cachedNotes = emptyList<SearchListItem.Result>()

    private val dateFormat = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())

    suspend fun preloadAll() = coroutineScope {
        val contactsJob = async(Dispatchers.IO) { loadContacts() }
        val smsJob = async(Dispatchers.IO) { loadRecentSms() }
        val callsJob = async(Dispatchers.IO) { loadCallLog() }
        val appsJob = async(Dispatchers.IO) { loadInstalledApps() }
        val filesJob = async(Dispatchers.IO) { loadLocalFiles() }
        val notesJob = async(Dispatchers.IO) { loadFoxNotes() }

        cachedContacts = contactsJob.await()
        cachedSms = smsJob.await()
        cachedCalls = callsJob.await()
        val allApps = appsJob.await()
        cachedApps = allApps.filter { it.category == Category.APP }
        cachedSettings = allApps.filter { it.category == Category.SETTINGS }
        cachedFiles = filesJob.await()
        cachedNotes = notesJob.await()
    }

    suspend fun search(
        query: String,
        filterCategory: Category = Category.ALL,
        sortOrder: SortOrder = SortOrder.RELEVANCE
    ): List<SearchListItem> = withContext(Dispatchers.Default) {
        val trimmed = query.trim()

        // 3-C: Pantalla inicial inteligente cuando el buscador está vacío
        if (trimmed.isEmpty()) {
            val initial = mutableListOf<SearchListItem>()
            when (filterCategory) {
                Category.ALL -> {
                    // Muestra llamadas recientes, mensajes, notas y apps principales
                    if (cachedCalls.isNotEmpty()) {
                        initial.addAll(cachedCalls.take(3))
                    }
                    if (cachedSms.isNotEmpty()) {
                        initial.addAll(cachedSms.take(2))
                    }
                    if (cachedNotes.isNotEmpty()) {
                        initial.addAll(cachedNotes.take(2))
                    }
                    if (cachedApps.isNotEmpty()) {
                        initial.addAll(cachedApps.take(6))
                    }
                }
                Category.APP -> initial.addAll(cachedApps.take(20))
                Category.CONTACT -> initial.addAll(cachedContacts.take(20))
                Category.SMS -> initial.addAll(cachedSms.take(20))
                Category.CALL_LOG -> initial.addAll(cachedCalls.take(20))
                Category.NOTE -> initial.addAll(cachedNotes.take(20))
                Category.FILE -> initial.addAll(cachedFiles.take(20))
                Category.SETTINGS -> initial.addAll(cachedSettings.take(20))
                Category.ACTION -> {}
            }
            return@withContext initial
        }

        val finalItems = mutableListOf<SearchListItem>()

        // 1. Acciones rápidas (calculadora, comandos, fila compacta para números)
        if (filterCategory == Category.ALL || filterCategory == Category.ACTION || filterCategory == Category.SETTINGS) {
            val quickActions = CommandParser.parseQuickActions(context, trimmed, cachedContacts)
            if (quickActions.isNotEmpty()) {
                finalItems.addAll(quickActions)
            }
        }

        // 2. Definir los depósitos a buscar según la categoría seleccionada
        val buckets = when (filterCategory) {
            Category.ALL -> listOf(
                Category.CONTACT to cachedContacts,
                Category.SMS to cachedSms,
                Category.CALL_LOG to cachedCalls,
                Category.NOTE to cachedNotes,
                Category.APP to cachedApps,
                Category.SETTINGS to cachedSettings,
                Category.FILE to cachedFiles
            )
            Category.APP -> listOf(Category.APP to cachedApps)
            Category.CONTACT -> listOf(Category.CONTACT to cachedContacts)
            Category.SMS -> listOf(Category.SMS to cachedSms)
            Category.CALL_LOG -> listOf(Category.CALL_LOG to cachedCalls)
            Category.NOTE -> listOf(Category.NOTE to cachedNotes)
            Category.FILE -> listOf(Category.FILE to cachedFiles)
            Category.SETTINGS -> listOf(Category.SETTINGS to cachedSettings)
            Category.ACTION -> emptyList()
        }

        // 3. Búsqueda continua unificada con coincidencia T9
        val allMatches = mutableListOf<SearchListItem.Result>()

        for ((_, sourceList) in buckets) {
            for (item in sourceList) {
                val score = T9Engine.matchScore(
                    queryRaw = trimmed,
                    itemNormalized = item.normalizedText,
                    itemT9 = item.t9Signature,
                    itemT9Alt = item.t9AltSignature,
                    cleanPhone = item.phoneNumber?.let { T9Engine.cleanPhone(it) }
                )
                if (score > 0) {
                    allMatches.add(item.copy(score = score))
                }
            }
        }

        // 4. Ordenamiento configurable: Por Relevancia o Por Fecha
        if (sortOrder == SortOrder.DATE) {
            // Ordenar por fecha cronológica más reciente primero
            allMatches.sortWith(
                compareByDescending<SearchListItem.Result> { it.timestamp }
                    .thenByDescending { it.score }
            )
        } else {
            // Ordenar por relevancia de coincidencia T9
            allMatches.sortByDescending { it.score }
        }

        finalItems.addAll(allMatches.take(50))
        finalItems
    }

    private fun hasPermission(perm: String): Boolean =
        ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED

    private fun loadContacts(): List<SearchListItem.Result> {
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) return emptyList()
        val list = mutableListOf<SearchListItem.Result>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.LAST_TIME_CONTACTED
        )
        try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val lastContactIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LAST_TIME_CONTACTED)
                val seen = HashSet<String>()

                while (cursor.moveToNext()) {
                    val id = cursor.getString(idIdx) ?: continue
                    val name = cursor.getString(nameIdx) ?: "Sin nombre"
                    val number = cursor.getString(numIdx) ?: ""
                    val lastContact = if (lastContactIdx >= 0) cursor.getLong(lastContactIdx) else 0L
                    val key = "$id-$number"
                    if (seen.add(key)) {
                        list.add(
                            SearchListItem.Result(
                                id = "contact_$key",
                                category = Category.CONTACT,
                                title = name,
                                subtitle = number,
                                phoneNumber = number,
                                extraData = id,
                                t9Signature = T9Engine.toT9(name),
                                t9AltSignature = T9Engine.toT9Alt(name),
                                normalizedText = T9Engine.normalize("$name $number"),
                                timestamp = lastContact
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {}
        return list
    }

    private fun loadRecentSms(): List<SearchListItem.Result> {
        if (!hasPermission(Manifest.permission.READ_SMS)) return emptyList()
        val list = mutableListOf<SearchListItem.Result>()
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE
        )
        try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                null,
                null,
                "${Telephony.Sms.DATE} DESC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(Telephony.Sms._ID)
                val threadIdx = cursor.getColumnIndex(Telephony.Sms.THREAD_ID)
                val addrIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY)
                val dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE)

                var count = 0
                while (cursor.moveToNext() && count < 300) {
                    count++
                    val id = cursor.getString(idIdx) ?: continue
                    val threadId = cursor.getString(threadIdx) ?: ""
                    val address = cursor.getString(addrIdx) ?: context.getString(R.string.call_unknown)
                    val body = cursor.getString(bodyIdx) ?: ""
                    val dateVal = cursor.getLong(dateIdx)
                    val dateStr = dateFormat.format(Date(dateVal))
                    val combined = "$address $body"

                    list.add(
                        SearchListItem.Result(
                            id = "sms_$id",
                            category = Category.SMS,
                            title = "$address · $dateStr",
                            subtitle = body.replace("\n", " "),
                            phoneNumber = address,
                            extraData = threadId,
                            t9Signature = T9Engine.toT9(combined),
                            t9AltSignature = T9Engine.toT9Alt(combined),
                            normalizedText = T9Engine.normalize(combined),
                            timestamp = dateVal
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        return list
    }

    private fun loadCallLog(): List<SearchListItem.Result> {
        if (!hasPermission(Manifest.permission.READ_CALL_LOG)) return emptyList()
        val list = mutableListOf<SearchListItem.Result>()
        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.NUMBER,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE
        )
        try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(CallLog.Calls._ID)
                val nameIdx = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val numIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                val typeIdx = cursor.getColumnIndex(CallLog.Calls.TYPE)
                val dateIdx = cursor.getColumnIndex(CallLog.Calls.DATE)

                var count = 0
                while (cursor.moveToNext() && count < 100) {
                    count++
                    val id = cursor.getString(idIdx) ?: continue
                    val cachedName = cursor.getString(nameIdx)
                    val number = cursor.getString(numIdx) ?: context.getString(R.string.call_private)
                    val typeLabel = when (cursor.getInt(typeIdx)) {
                        CallLog.Calls.INCOMING_TYPE -> context.getString(R.string.call_type_incoming)
                        CallLog.Calls.OUTGOING_TYPE -> context.getString(R.string.call_type_outgoing)
                        CallLog.Calls.MISSED_TYPE -> context.getString(R.string.call_type_missed)
                        else -> context.getString(R.string.call_type_other)
                    }
                    val dateVal = cursor.getLong(dateIdx)
                    val dateStr = dateFormat.format(Date(dateVal))
                    val display = if (!cachedName.isNullOrBlank()) cachedName else number
                    val combined = "$display $number $typeLabel"

                    list.add(
                        SearchListItem.Result(
                            id = "call_$id",
                            category = Category.CALL_LOG,
                            title = display,
                            subtitle = "$typeLabel · $dateStr ($number)",
                            phoneNumber = number,
                            t9Signature = T9Engine.toT9(combined),
                            t9AltSignature = T9Engine.toT9Alt(combined),
                            normalizedText = T9Engine.normalize(combined),
                            timestamp = dateVal
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        return list
    }

    private fun loadInstalledApps(): List<SearchListItem.Result> {
        return try {
            val pm = context.packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            pm.queryIntentActivities(mainIntent, 0).mapNotNull { resolveInfo ->
                val pkg = resolveInfo.activityInfo.packageName
                if (pkg == context.packageName) return@mapNotNull null
                val label = resolveInfo.loadLabel(pm).toString()

                val isSettings = pkg == "com.android.settings" ||
                        pkg.contains(".settings") ||
                        label.equals("Ajustes", ignoreCase = true) ||
                        label.equals("Configuración", ignoreCase = true) ||
                        label.equals("Settings", ignoreCase = true)

                val category = if (isSettings) Category.SETTINGS else Category.APP
                val subtitle = if (isSettings) context.getString(R.string.settings_system) else ""

                val lastUpdateTime = try {
                    pm.getPackageInfo(pkg, 0).lastUpdateTime
                } catch (_: Exception) {
                    0L
                }

                SearchListItem.Result(
                    id = "app_$pkg",
                    category = category,
                    title = label,
                    subtitle = subtitle,
                    extraData = pkg,
                    t9Signature = T9Engine.toT9(label),
                    t9AltSignature = T9Engine.toT9Alt(label),
                    normalizedText = T9Engine.normalize("$label $pkg"),
                    timestamp = lastUpdateTime
                )
            }.sortedBy { it.title }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun loadLocalFiles(): List<SearchListItem.Result> {
        if (!hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE)) return emptyList()
        val root = Environment.getExternalStorageDirectory() ?: return emptyList()
        val allowedExt = setOf("mp3", "m4a", "amr", "wav", "ogg", "pdf", "txt", "doc", "docx")
        val targetDirs = listOf("Recordings", "Sounds", "Download", "Documents", "Music")
        val results = mutableListOf<SearchListItem.Result>()

        try {
            for (dirName in targetDirs) {
                val dir = File(root, dirName)
                if (dir.exists() && dir.isDirectory) {
                    dir.walkTopDown().maxDepth(3).forEach { file ->
                        if (file.isFile && file.extension.lowercase() in allowedExt) {
                            results.add(
                                SearchListItem.Result(
                                    id = "file_${file.absolutePath.hashCode()}",
                                    category = Category.FILE,
                                    title = file.name,
                                    subtitle = "/${file.parentFile?.name ?: "sdcard"} · ${file.length() / 1024} KB",
                                    extraData = file.absolutePath,
                                    t9Signature = T9Engine.toT9(file.nameWithoutExtension),
                                    t9AltSignature = T9Engine.toT9Alt(file.nameWithoutExtension),
                                    normalizedText = T9Engine.normalize(file.name),
                                    timestamp = file.lastModified()
                                )
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return results.take(200)
    }

    private fun loadFoxNotes(): List<SearchListItem.Result> {
        val prefs = context.getSharedPreferences("fox_quick_notes", Context.MODE_PRIVATE)
        return prefs.all.mapNotNull { (key, value) ->
            val text = value as? String ?: return@mapNotNull null
            val lines = text.split("\n", limit = 2)
            val title = lines[0].take(40)
            val noteTime = key.toLongOrNull() ?: 0L
            SearchListItem.Result(
                id = "note_$key",
                category = Category.NOTE,
                title = title,
                subtitle = text.replace("\n", " "),
                extraData = text,
                t9Signature = T9Engine.toT9(text),
                t9AltSignature = T9Engine.toT9Alt(text),
                normalizedText = T9Engine.normalize(text),
                timestamp = noteTime
            )
        }
    }

    fun saveQuickNote(text: String) {
        val prefs = context.getSharedPreferences("fox_quick_notes", Context.MODE_PRIVATE)
        prefs.edit().putString(System.currentTimeMillis().toString(), text).apply()
        cachedNotes = loadFoxNotes()
    }
}
