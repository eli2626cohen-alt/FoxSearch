package com.lockfox.search.ui

import android.graphics.Color
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.lockfox.search.R
import com.lockfox.search.engine.T9Engine
import com.lockfox.search.model.Category
import com.lockfox.search.model.SearchListItem

class SearchAdapter(
    private val onItemOpen: (SearchListItem.Result) -> Unit,
    private val onItemCall: (String) -> Unit,
    private val onItemSms: (String) -> Unit,
    private val onItemCopy: (String) -> Unit,
    private val onItemSave: (String) -> Unit,
    private val onItemLongPress: (SearchListItem.Result) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var items: List<SearchListItem> = emptyList()
    private var currentQuery: String = ""
    var selectedPosition: Int = RecyclerView.NO_POSITION
        private set

    var isDarkMode: Boolean = false
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_RESULT = 1
        private const val TYPE_ACTION = 2
        private const val TYPE_NUMBER_ACTION = 3
    }

    fun submitList(newItems: List<SearchListItem>, query: String) {
        items = newItems
        currentQuery = query
        selectedPosition = items.indexOfFirst { it is SearchListItem.Result || it is SearchListItem.NumberAction }
        notifyDataSetChanged()
    }

    fun getSelectedItem(): SearchListItem? = items.getOrNull(selectedPosition)

    fun moveSelection(delta: Int): Int {
        if (items.isEmpty()) return RecyclerView.NO_POSITION
        var next = selectedPosition
        while (true) {
            next += delta
            if (next !in items.indices) return selectedPosition
            if (items[next] is SearchListItem.Result || items[next] is SearchListItem.NumberAction) {
                val old = selectedPosition
                selectedPosition = next
                if (old != RecyclerView.NO_POSITION) notifyItemChanged(old)
                notifyItemChanged(selectedPosition)
                return selectedPosition
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (val item = items[position]) {
            is SearchListItem.Header -> TYPE_HEADER
            is SearchListItem.NumberAction -> TYPE_NUMBER_ACTION
            is SearchListItem.Result -> {
                if (item.category == Category.ACTION) TYPE_ACTION else TYPE_RESULT
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderViewHolder(inflater.inflate(R.layout.item_search_header, parent, false))
            TYPE_NUMBER_ACTION -> NumberActionViewHolder(inflater.inflate(R.layout.item_search_number_action, parent, false))
            TYPE_ACTION -> ActionViewHolder(inflater.inflate(R.layout.item_search_action, parent, false))
            else -> ResultViewHolder(inflater.inflate(R.layout.item_search_result, parent, false))
        }
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val isSelected = position == selectedPosition
        when (val item = items[position]) {
            is SearchListItem.Header -> (holder as HeaderViewHolder).bind(item)
            is SearchListItem.NumberAction -> (holder as NumberActionViewHolder).bind(item, isSelected)
            is SearchListItem.Result -> {
                if (holder is ActionViewHolder) {
                    holder.bind(item, isSelected)
                } else if (holder is ResultViewHolder) {
                    holder.bind(item, isSelected)
                }
            }
        }
    }

    // Fila compacta para números marcados
    inner class NumberActionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val container: View = view.findViewById(R.id.numberActionContainer)
        private val tvNumber: TextView = view.findViewById(R.id.tvActionNumber)
        private val btnCall: ImageButton = view.findViewById(R.id.btnNumCall)
        private val btnSms: ImageButton = view.findViewById(R.id.btnNumSms)
        private val btnCopy: ImageButton = view.findViewById(R.id.btnNumCopy)
        private val btnSave: ImageButton = view.findViewById(R.id.btnNumSave)

        fun bind(item: SearchListItem.NumberAction, isSelected: Boolean) {
            val bgRes = if (isSelected) {
                if (isDarkMode) R.drawable.bg_item_selected_dark else R.drawable.bg_item_selected
            } else {
                if (isDarkMode) R.drawable.bg_item_normal_dark else R.drawable.bg_item_normal
            }
            container.setBackgroundResource(bgRes)
            tvNumber.text = item.number
            tvNumber.setTextColor(if (isDarkMode) Color.WHITE else Color.parseColor("#1F1F1F"))

            container.setOnClickListener {
                updateSelection(bindingAdapterPosition)
                onItemCall(item.number)
            }

            btnCall.setOnClickListener { onItemCall(item.number) }
            btnSms.setOnClickListener { onItemSms(item.number) }
            btnCopy.setOnClickListener { onItemCopy(item.number) }
            btnSave.setOnClickListener { onItemSave(item.number) }
        }
    }

    inner class ActionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val container: View = view.findViewById(R.id.actionContainer)
        private val flBadge: FrameLayout = view.findViewById(R.id.flActionBadge)
        private val ivIcon: ImageView = view.findViewById(R.id.ivActionIcon)
        private val tvTitle: TextView = view.findViewById(R.id.tvActionTitle)
        private val tvSubtitle: TextView = view.findViewById(R.id.tvActionSubtitle)

        fun bind(result: SearchListItem.Result, isSelected: Boolean) {
            val bgRes = if (isSelected) {
                if (isDarkMode) R.drawable.bg_item_selected_dark else R.drawable.bg_item_selected
            } else {
                if (isDarkMode) R.drawable.bg_item_normal_dark else R.drawable.bg_item_normal
            }
            container.setBackgroundResource(bgRes)
            flBadge.background?.mutate()?.setTint(result.category.bgPastelColor)
            ivIcon.setImageResource(result.category.iconRes)
            ivIcon.setColorFilter(result.category.fgColor)

            tvTitle.text = result.title
            tvTitle.setTextColor(if (isDarkMode) Color.WHITE else Color.parseColor("#1F1F1F"))
            tvSubtitle.text = result.subtitle
            tvSubtitle.setTextColor(if (isDarkMode) Color.parseColor("#9DA2AB") else Color.parseColor("#5F6368"))

            container.setOnClickListener {
                updateSelection(bindingAdapterPosition)
                onItemOpen(result)
            }
        }
    }

    inner class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvHeader: TextView = view.findViewById(R.id.tvHeaderTitle)
        fun bind(header: SearchListItem.Header) {
            tvHeader.text = "${header.category.getLocalizedTitle(itemView.context)} · ${header.count}"
            tvHeader.setTextColor(if (isDarkMode) Color.parseColor("#8AB4F8") else Color.parseColor("#1A73E8"))
        }
    }

    inner class ResultViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val container: View = view.findViewById(R.id.resultContainer)
        private val flBadge: FrameLayout = view.findViewById(R.id.flAvatarBadge)
        private val ivIcon: ImageView = view.findViewById(R.id.ivAvatarIcon)
        private val tvInitials: TextView = view.findViewById(R.id.tvAvatarInitials)
        private val tvTitle: TextView = view.findViewById(R.id.tvResultTitle)
        private val tvSubtitle: TextView = view.findViewById(R.id.tvResultSubtitle)
        private val btnCall: ImageButton = view.findViewById(R.id.btnQuickCall)
        private val btnSms: ImageButton = view.findViewById(R.id.btnQuickSms)

        fun bind(result: SearchListItem.Result, isSelected: Boolean) {
            val bgRes = if (isSelected) {
                if (isDarkMode) R.drawable.bg_item_selected_dark else R.drawable.bg_item_selected
            } else {
                if (isDarkMode) R.drawable.bg_item_normal_dark else R.drawable.bg_item_normal
            }
            container.setBackgroundResource(bgRes)

            // Identidad pastel sutil
            flBadge.background?.mutate()?.setTint(result.category.bgPastelColor)

            if (result.category == Category.CONTACT) {
                val initials = result.title
                    .split(" ")
                    .filter { it.isNotBlank() && it[0].isLetter() }
                    .take(2)
                    .joinToString("") { it[0].uppercase() }

                if (initials.isNotEmpty()) {
                    tvInitials.text = initials
                    tvInitials.setTextColor(result.category.fgColor)
                    tvInitials.visibility = View.VISIBLE
                    ivIcon.visibility = View.GONE
                } else {
                    ivIcon.setImageResource(result.category.iconRes)
                    ivIcon.setColorFilter(result.category.fgColor)
                    ivIcon.visibility = View.VISIBLE
                    tvInitials.visibility = View.GONE
                }
            } else {
                ivIcon.setImageResource(result.category.iconRes)
                ivIcon.setColorFilter(result.category.fgColor)
                ivIcon.visibility = View.VISIBLE
                tvInitials.visibility = View.GONE
            }

            tvTitle.text = highlightMatch(result.title, currentQuery)
            tvTitle.setTextColor(if (isDarkMode) Color.WHITE else Color.parseColor("#1F1F1F"))

            // Subtítulo secundario únicamente cuando es útil
            if (result.subtitle.isBlank()) {
                tvSubtitle.visibility = View.GONE
            } else {
                tvSubtitle.visibility = View.VISIBLE
                tvSubtitle.text = highlightMatch(result.subtitle, currentQuery)
                tvSubtitle.setTextColor(if (isDarkMode) Color.parseColor("#9DA2AB") else Color.parseColor("#5F6368"))
            }

            // Acciones directas para números/contactos
            val canCall = !result.phoneNumber.isNullOrBlank() &&
                (result.category == Category.CONTACT || result.category == Category.SMS || result.category == Category.CALL_LOG)

            btnCall.visibility = if (canCall) View.VISIBLE else View.GONE
            btnSms.visibility = if (result.category == Category.CONTACT && canCall) View.VISIBLE else View.GONE

            container.setOnClickListener {
                updateSelection(bindingAdapterPosition)
                onItemOpen(result)
            }

            container.setOnLongClickListener {
                onItemLongPress(result)
                true
            }

            btnCall.setOnClickListener { result.phoneNumber?.let { onItemCall(it) } }
            btnSms.setOnClickListener { result.phoneNumber?.let { onItemSms(it) } }
        }
    }

    private fun updateSelection(pos: Int) {
        if (pos != RecyclerView.NO_POSITION && pos != selectedPosition) {
            val prev = selectedPosition
            selectedPosition = pos
            if (prev != RecyclerView.NO_POSITION) notifyItemChanged(prev)
            notifyItemChanged(pos)
        }
    }

    private fun highlightMatch(text: String, query: String): CharSequence {
        val cleanQuery = T9Engine.normalize(query).trim()
        if (cleanQuery.isEmpty()) return text
        val cleanText = T9Engine.normalize(text)
        val start = cleanText.indexOf(cleanQuery)
        if (start < 0 || start + cleanQuery.length > text.length) return text

        val accentColor = if (isDarkMode) Color.parseColor("#8AB4F8") else Color.parseColor("#1A73E8")
        return SpannableString(text).apply {
            setSpan(StyleSpan(Typeface.BOLD), start, start + cleanQuery.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(ForegroundColorSpan(accentColor), start, start + cleanQuery.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
}
