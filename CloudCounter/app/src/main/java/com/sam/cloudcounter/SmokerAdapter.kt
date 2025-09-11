package com.sam.cloudcounter

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat

class SmokerAdapter(
    private val context: Context,
    private val layoutInflater: LayoutInflater,
    private val smokerManager: SmokerManager,
    private val onAddSmokerClick: () -> Unit,
    private val onDeleteAllClick: () -> Unit,
    private val onSmokerSelected: (Smoker?) -> Unit,
    private val onReorderClick: (() -> Unit)? = null
) : ArrayAdapter<Smoker?>(context, R.layout.custom_spinner_item, mutableListOf()) {

    private var organizedSmokers: List<Smoker> = emptyList()
    private var allSmokers: List<Smoker> = emptyList()
    private val fontList = listOf(
        R.font.bitcount_prop_double,
        R.font.exile,
        R.font.modak,
        R.font.oi,
        R.font.rubik_glitch,
        R.font.sankofa_display,
        R.font.silkscreen,
        R.font.rubik_puddles,
        R.font.rubik_beastly,
        R.font.sixtyfour,
        R.font.monoton,
        R.font.sedgwick_ave_display,
        R.font.splash
    )

    fun refreshOrganizedList(smokers: List<Smoker>, currentShareCode: String?, pausedSmokerIds: List<String>, awaySmokers: List<String>) {
        allSmokers = smokers
        organizedSmokers = organizeSmokers(smokers, currentShareCode, pausedSmokerIds, awaySmokers)
            .flatMap { it.smokers }
        notifyDataSetChanged()
    }
    
    fun getAllSmokers(): List<Smoker> = allSmokers

    override fun getCount(): Int {
        return organizedSmokers.size + 1
    }

    override fun getItem(position: Int): Smoker? {
        return if (position < organizedSmokers.size) {
            organizedSmokers[position]
        } else {
            null
        }
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: layoutInflater.inflate(R.layout.spinner_item, parent, false)
        val item = getItem(position)
        val container = view as? FrameLayout
        val textName = container?.findViewById<TextView>(R.id.textName)

        // Hide the sync status dot in the spinner selected view (not dropdown)
        val syncDot = container?.findViewById<View>(R.id.syncStatusDot)
        syncDot?.visibility = View.GONE

        if (item == null) {
            setupAddSmokerView(textName, view)
        } else {
            setupSmokerView(textName, item)
        }

        return view
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        val item = getItem(position)

        if (item == null) {
            return createAddSmokerDropdownView(parent)
        }

        return createSmokerDropdownView(item, parent, position)
    }

    private fun setupAddSmokerView(textName: TextView?, view: View) {
        textName?.text = "Add smoker..."
        textName?.textSize = 24f
        textName?.setTextColor(Color.WHITE)
        textName?.typeface = getDefaultFont()

        if (organizedSmokers.isEmpty()) {
            textName?.let { tv ->
                (tv.tag as? ShimmerTextAnimation)?.stopShimmer()
                val shimmer = ShimmerTextAnimation(tv, fontList)
                tv.tag = shimmer
            }

            view.setOnClickListener {
                (textName?.tag as? ShimmerTextAnimation)?.stopShimmer()
                textName?.tag = null
                dismissSpinnerDropDown()
                onAddSmokerClick()
            }

            textName?.setOnClickListener {
                (textName.tag as? ShimmerTextAnimation)?.stopShimmer()
                textName.tag = null
                dismissSpinnerDropDown()
                onAddSmokerClick()
            }

            view.isClickable = true
            view.isFocusable = true
            textName?.isClickable = true
            textName?.isFocusable = true
        } else {
            cleanupAddSmokerView(view, textName)
        }
    }

    private fun cleanupAddSmokerView(view: View, textName: TextView?) {
        view.setOnClickListener(null)
        textName?.setOnClickListener(null)
        view.isClickable = false
        view.isFocusable = false
        textName?.isClickable = false
        textName?.isFocusable = false
        (textName?.tag as? ShimmerTextAnimation)?.stopShimmer()
        textName?.tag = null
        textName?.setTextColor(Color.WHITE)
    }

    private fun setupSmokerView(textName: TextView?, item: Smoker) {
        // Show only the name without icons in the spinner display
        textName?.text = item.name
        textName?.textSize = 32f

        val smokerId = if (item.isCloudSmoker) item.cloudUserId else "local_${item.smokerId}"
        textName?.alpha = smokerManager.getSmokerAlpha(smokerId, item.cloudUserId)

        textName?.typeface = smokerManager.getFontForSmoker(item.smokerId)
        textName?.setTextColor(smokerManager.getColorForSmoker(item.smokerId))

        (textName?.tag as? ShimmerTextAnimation)?.stopShimmer()
        textName?.tag = null
    }

    private fun createAddSmokerDropdownView(parent: ViewGroup): View {
        // Use the new layout if reorder callback is provided
        val layoutRes = if (onReorderClick != null) {
            R.layout.spinner_dropdown_add_item_with_reorder
        } else {
            R.layout.spinner_dropdown_add_item
        }
        
        val view = layoutInflater.inflate(layoutRes, parent, false)
        val addButton = view.findViewById<Button>(R.id.btnAddSmokerDropdown)
        val deleteAllBtn = view.findViewById<Button>(R.id.btnDeleteAllDropdown)
        val reorderBtn = view.findViewById<Button>(R.id.btnReorderDropdown)
        val shimmerText = view.findViewById<TextView>(R.id.textAddSmokerShimmer)

        if (organizedSmokers.isEmpty()) {
            shimmerText.visibility = View.VISIBLE
            addButton.visibility = View.GONE
            deleteAllBtn.visibility = View.GONE
            reorderBtn?.visibility = View.GONE

            shimmerText.post {
                (shimmerText.tag as? ShimmerTextAnimation)?.stopShimmer()
                val shimmer = ShimmerTextAnimation(shimmerText, fontList)
                shimmerText.tag = shimmer
            }
        } else {
            shimmerText.visibility = View.GONE
            addButton.visibility = View.VISIBLE
            deleteAllBtn.visibility = View.VISIBLE
            reorderBtn?.visibility = View.VISIBLE
        }

        setupDropdownButton(addButton, Color.WHITE, ContextCompat.getColor(context, R.color.my_dark_grey_background)) {
            (shimmerText.tag as? ShimmerTextAnimation)?.stopShimmer()
            dismissSpinnerDropDown()
            onAddSmokerClick()
        }

        setupDropdownButton(deleteAllBtn, ContextCompat.getColor(context, R.color.neon_orange), ContextCompat.getColor(context, R.color.my_dark_grey_background)) {
            dismissSpinnerDropDown()
            onDeleteAllClick()
        }
        
        // Setup reorder button if it exists
        reorderBtn?.let { btn ->
            setupDropdownButton(btn, ContextCompat.getColor(context, R.color.neon_green), ContextCompat.getColor(context, R.color.my_dark_grey_background)) {
                dismissSpinnerDropDown()
                onReorderClick?.invoke()
            }
        }

        return view
    }

    private fun setupDropdownButton(button: Button, pressedBgColor: Int, pressedTextColor: Int, onClick: () -> Unit) {
        button.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    button.setBackgroundColor(pressedBgColor)
                    button.setTextColor(pressedTextColor)
                    false
                }
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> {
                    button.setBackgroundColor(Color.TRANSPARENT)
                    button.setTextColor(pressedBgColor)
                    if (event.action == android.view.MotionEvent.ACTION_UP) {
                        v.performClick()
                    }
                    true
                }
                else -> false
            }
        }
        button.setOnClickListener { onClick() }
    }

    private fun createSmokerDropdownView(smoker: Smoker, parent: ViewGroup, position: Int): View {
        val sections = organizeSmokers(listOf(smoker), smokerManager.currentShareCode, smokerManager.pausedSmokerIds, smokerManager.awaySmokers)
        val container = createSectionContainer(parent)

        addSectionHeaderIfNeeded(container, sections, position)

        val smokerView = createSmokerItemView(container, smoker)
        container.addView(smokerView)

        return container
    }

    private fun createSectionContainer(parent: ViewGroup): LinearLayout {
        return LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun addSectionHeaderIfNeeded(container: LinearLayout, sections: List<SmokerSection>, position: Int) {
        var sectionTitle: String? = null
        var isFirstInSection = false
        var currentPosition = 0

        for (section in sections) {
            if (currentPosition == position) {
                sectionTitle = section.title
                isFirstInSection = true
                break
            } else if (currentPosition < position && position < currentPosition + section.smokers.size) {
                sectionTitle = section.title
                isFirstInSection = false
                break
            }
            currentPosition += section.smokers.size
        }

        if (isFirstInSection && sectionTitle != null) {
            val headerView = TextView(container.context).apply {
                text = sectionTitle
                textSize = 12f
                setTextColor(ContextCompat.getColor(context, android.R.color.darker_gray))
                setPadding(16, 8, 16, 4)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            container.addView(headerView)
        }
    }

    private fun createSmokerItemView(container: LinearLayout, smoker: Smoker): View {
        val smokerView = layoutInflater.inflate(R.layout.spinner_dropdown_item, container, false)
        val textName = smokerView.findViewById<TextView>(R.id.textName)
        val syncDot = smokerView.findViewById<View>(R.id.syncStatusDot)
        val btnDelete = smokerView.findViewById<ImageButton>(R.id.btnDelete)
        val btnSync = smokerView.findViewById<ImageButton>(R.id.btnSync)
        val btnEdit = smokerView.findViewById<ImageButton>(R.id.btnEdit)
        val btnPassword = smokerView.findViewById<ImageButton>(R.id.btnPassword)
        val btnPausePlay = smokerView.findViewById<ImageButton>(R.id.btnPausePlay)

        textName.text = smokerManager.formatSmokerNameWithStatus(smoker)

        setupDropdownItemButtons(smoker, btnDelete, btnSync, btnEdit, btnPassword, btnPausePlay, syncDot)

        return smokerView
    }

    private fun setupDropdownItemButtons(
        smoker: Smoker,
        btnDelete: ImageButton,
        btnSync: ImageButton,
        btnEdit: ImageButton,
        btnPassword: ImageButton,
        btnPausePlay: ImageButton,
        syncDot: View
    ) {
        smokerManager.setupSmokerDropdownButtons(
            smoker, btnDelete, btnSync, btnEdit, btnPassword, btnPausePlay, syncDot
        ) { dismissSpinnerDropDown() }
    }

    private fun dismissSpinnerDropDown() {
        smokerManager.dismissSpinnerDropDown()
    }

    private fun getDefaultFont(): Typeface {
        return try {
            ResourcesCompat.getFont(context, R.font.sedgwick_ave_display)!!
        } catch (e: Exception) {
            Typeface.DEFAULT
        }
    }

    private fun organizeSmokers(
        smokers: List<Smoker>,
        currentShareCode: String?,
        pausedSmokerIds: List<String>,
        awaySmokers: List<String>
    ): List<SmokerSection> {
        val sections = mutableListOf<SmokerSection>()
        val activeSmokers = mutableListOf<Smoker>()
        val pausedSmokers = mutableListOf<Smoker>()
        val awaySmokersInSection = mutableListOf<Smoker>()

        smokers.forEach { smoker ->
            val smokerId = if (smoker.isCloudSmoker) smoker.cloudUserId else "local_${smoker.smokerId}"
            val userId = smoker.cloudUserId

            if (currentShareCode != null) {
                when {
                    pausedSmokerIds.contains(smokerId) -> pausedSmokers.add(smoker)
                    awaySmokers.contains(userId) -> awaySmokersInSection.add(smoker)
                    else -> activeSmokers.add(smoker)
                }
            } else {
                activeSmokers.add(smoker)
            }
        }

        if (activeSmokers.isNotEmpty()) {
            sections.add(SmokerSection(null, activeSmokers))
        }
        if (pausedSmokers.isNotEmpty()) {
            sections.add(SmokerSection("Paused", pausedSmokers))
        }
        if (awaySmokersInSection.isNotEmpty()) {
            sections.add(SmokerSection("Away", awaySmokersInSection))
        }

        return sections
    }

    data class SmokerSection(
        val title: String?,
        val smokers: List<Smoker>
    )
}