package com.fyp.tomato_guard.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.fyp.tomato_guard.R
import com.fyp.tomato_guard.models.SeasonalTip

class SeasonalTipAdapter(private val tips: List<SeasonalTip>) :
    RecyclerView.Adapter<SeasonalTipAdapter.TipViewHolder>() {

    class TipViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val monthText: TextView = itemView.findViewById(R.id.monthText)
        val emojiText: TextView = itemView.findViewById(R.id.emojiText)
        val tipText: TextView = itemView.findViewById(R.id.tipText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TipViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_seasonal_tip, parent, false)
        return TipViewHolder(view)
    }

    override fun onBindViewHolder(holder: TipViewHolder, position: Int) {
        val tip = tips[position]
        holder.monthText.text = tip.month
        holder.emojiText.text = tip.emoji
        holder.tipText.text = tip.tip
    }

    override fun getItemCount(): Int = tips.size
}
