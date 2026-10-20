package com.sakura.home

import android.animation.ObjectAnimator
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class CardAdapter(
    private val widthDp: Int,
    private val heightDp: Int,
    private val listener: Listener,
) : RecyclerView.Adapter<CardAdapter.Holder>() {

    interface Listener {
        fun onFocused(app: AppEntry, row: CardAdapter)
        fun onClicked(app: AppEntry, view: View)
        fun onLongClicked(app: AppEntry, row: CardAdapter)
    }

    var items: List<AppEntry> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val banner: ImageView = v.findViewById(R.id.banner)
        val fallback: LinearLayout = v.findViewById(R.id.fallback)
        val icon: ImageView = v.findViewById(R.id.icon)
        val label: TextView = v.findViewById(R.id.label)
        val shimmer: View = v.findViewById(R.id.shimmer)
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_card, parent, false)
        val d = parent.resources.displayMetrics.density
        v.layoutParams = (v.layoutParams as ViewGroup.MarginLayoutParams).apply {
            width = (widthDp * d).toInt()
            height = (heightDp * d).toInt()
        }
        v.clipToOutline = true
        // Pink shadows turn the elevation into a soft glow around the focused card
        v.outlineSpotShadowColor = Color.parseColor("#FF6FA0")
        v.outlineAmbientShadowColor = Color.parseColor("#FFB7CF")
        return Holder(v)
    }

    override fun onBindViewHolder(h: Holder, position: Int) {
        val app = items[position]
        if (app.banner != null) {
            h.banner.setImageDrawable(app.banner)
            h.banner.visibility = View.VISIBLE
            h.fallback.visibility = View.GONE
        } else {
            h.banner.setImageDrawable(null)
            h.banner.visibility = View.GONE
            h.fallback.visibility = View.VISIBLE
            h.icon.setImageDrawable(app.icon)
            h.label.text = app.label
        }
        h.itemView.contentDescription = app.label
        h.itemView.setOnClickListener { listener.onClicked(app, it) }
        h.itemView.setOnLongClickListener { listener.onLongClicked(app, this); true }
        h.itemView.setOnFocusChangeListener { v, hasFocus ->
            val d = v.resources.displayMetrics.density
            v.animate()
                .scaleX(if (hasFocus) 1.12f else 1f)
                .scaleY(if (hasFocus) 1.12f else 1f)
                .translationZ(if (hasFocus) 22 * d else 0f)
                .setInterpolator(if (hasFocus) OvershootInterpolator(1.6f) else AccelerateDecelerateInterpolator())
                .setDuration(if (hasFocus) 320 else 180)
                .start()
            if (hasFocus) {
                listener.onFocused(app, this)
                sweep(h, v)
            }
        }
    }

    // A single light sweep across the card when it gains focus
    private fun sweep(h: Holder, card: View) {
        h.shimmer.visibility = View.VISIBLE
        h.shimmer.rotation = 18f
        val from = -h.shimmer.width.toFloat() - 40
        ObjectAnimator.ofFloat(h.shimmer, View.TRANSLATION_X, from, card.width + 40f).apply {
            duration = 850
            startDelay = 120
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }
}
