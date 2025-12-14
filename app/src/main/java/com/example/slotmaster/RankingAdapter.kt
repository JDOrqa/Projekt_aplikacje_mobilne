package com.example.slotmaster

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
class RankingAdapter(private var rankingItems: List<RankingItem>) : 
    RecyclerView.Adapter<RankingAdapter.RankingViewHolder>() {

    fun updateData(newItems: List<RankingItem>) {
        // Przypisz poprawne miejsca w rankingu
        val rankedItems = newItems.mapIndexed { index, item ->
            item.copy(rank = index + 1)
        }
        this.rankingItems = rankedItems
        notifyDataSetChanged()
    }
override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RankingViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ranking, parent, false)
        return RankingViewHolder(view)
}
