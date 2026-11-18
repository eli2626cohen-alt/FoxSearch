package com.lockfox.search.model

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.lockfox.search.R

enum class Category(
    @param:StringRes val titleRes: Int,
    val badge: String,
    val priority: Int,
    val bgPastelColor: Int,
    val fgColor: Int,
    @param:DrawableRes val iconRes: Int
) {
    ALL(R.string.category_all, "ALL", -1, 0xFFF1F3F4.toInt(), 0xFF3C4043.toInt(), R.drawable.ic_filter),
    ACTION(R.string.category_action, "⚡", 0, 0xFFECEFF1.toInt(), 0xFF455A64.toInt(), R.drawable.ic_calc),
    APP(R.string.category_app, "AP", 1, 0xFFEBF3FB.toInt(), 0xFF1976D2.toInt(), R.drawable.ic_apps),
    CONTACT(R.string.category_contact, "CT", 2, 0xFFE1F4F5.toInt(), 0xFF00695C.toInt(), R.drawable.ic_person),
    SMS(R.string.category_sms, "MS", 3, 0xFFE4F5E9.toInt(), 0xFF1B5E20.toInt(), R.drawable.ic_sms),
    CALL_LOG(R.string.category_call_log, "LL", 4, 0xFFE5EEFF.toInt(), 0xFF1565C0.toInt(), R.drawable.ic_call),
    FILE(R.string.category_file, "AR", 5, 0xFFF0EAFE.toInt(), 0xFF5E35B1.toInt(), R.drawable.ic_file),
    NOTE(R.string.category_note, "NT", 6, 0xFFFFF3D8.toInt(), 0xFFB78103.toInt(), R.drawable.ic_note),
    SETTINGS(R.string.category_settings, "CF", 7, 0xFFECEFF1.toInt(), 0xFF37474F.toInt(), R.drawable.ic_settings);

    fun getLocalizedTitle(context: Context): String = context.getString(titleRes)
}

enum class SortOrder {
    RELEVANCE,
    DATE
}

sealed class SearchListItem {
    data class Header(
        val category: Category,
        val count: Int
    ) : SearchListItem()

    data class Result(
        val id: String,
        val category: Category,
        val title: String,
        val subtitle: String,
        val phoneNumber: String? = null,
        val extraData: String? = null,
        val t9Signature: String = "",
        val t9AltSignature: String = "",
        val normalizedText: String = "",
        var score: Int = 0,
        val timestamp: Long = 0L
    ) : SearchListItem()

    data class NumberAction(
        val number: String,
        val score: Int = 1000
    ) : SearchListItem()
}
