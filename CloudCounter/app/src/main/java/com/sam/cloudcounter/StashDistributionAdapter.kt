// StashDistributionAdapter.kt
package com.sam.cloudcounter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.text.NumberFormat
import java.util.*

class StashDistributionAdapter : ListAdapter<StashDistribution, StashDistributionAdapter.ViewHolder>(DiffCallback()) {

    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale.US)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_stash_distribution, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textSmokerName: TextView = itemView.findViewById(R.id.textSmokerName)
        private val textDistribution: TextView = itemView.findViewById(R.id.textDistribution)
        private val textValue: TextView = itemView.findViewById(R.id.textValue)

        fun bind(distribution: StashDistribution) {
            textSmokerName.text = distribution.smokerName

            val parts = mutableListOf<String>()
            if (distribution.conesGiven > 0) parts.add("${distribution.conesGiven} cones")
            if (distribution.jointsGiven > 0) parts.add("${distribution.jointsGiven} joints")
            if (distribution.bowlsGiven > 0) parts.add("${distribution.bowlsGiven} bowls")

            textDistribution.text = if (parts.isNotEmpty()) {
                parts.joinToString(", ")
            } else {
                "No consumption"
            }

            textValue.text = currencyFormat.format(distribution.totalValue)
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<StashDistribution>() {
        override fun areItemsTheSame(oldItem: StashDistribution, newItem: StashDistribution): Boolean {
            return oldItem.smokerUid == newItem.smokerUid
        }

        override fun areContentsTheSame(oldItem: StashDistribution, newItem: StashDistribution): Boolean {
            return oldItem == newItem
        }
    }
}


