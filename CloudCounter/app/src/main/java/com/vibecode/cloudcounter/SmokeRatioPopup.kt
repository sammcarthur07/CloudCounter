package com.vibecode.cloudcounter

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.*
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.DecimalFormat

class SmokeRatioPopup(
    private val context: Context,
    private val ratioManager: SmokeRatioManager,
    private val onRatioSaved: (SmokeRatio) -> Unit = {},
    private val onDismissListener: (() -> Unit)? = null
) {
    
    companion object {
        private const val TAG = "SmokeRatioPopup"
        private const val PREF_MULTIPLIER = "ratio_multiplier"
    }
    
    private val decimalFormat = DecimalFormat("#.####")
    private var isUpdatingValues = false
    private var isLinked = false  // Chain state - start unlinked
    private var lastSmokesValue = 3  // Track previous smokes for scaling
    private var lastThcValue = 20.0  // Track for proportional scaling
    private lateinit var dialog: Dialog
    
    private lateinit var editNumberOfSmokes: EditText
    private lateinit var editThcPercent: EditText
    private lateinit var editChopAmount: EditText
    private lateinit var textTotalGrams: TextView
    private lateinit var textBulkChop: TextView
    private lateinit var chainIcon: ImageButton
    private lateinit var multiplierButton: TextView
    private var multiplier = 3
    private lateinit var radioGroup: RadioGroup
    private lateinit var radioJoint: RadioButton
    private lateinit var radioBowl: RadioButton
    private lateinit var editRatioName: EditText
    private lateinit var recyclerJointRatios: RecyclerView
    private lateinit var recyclerBowlRatios: RecyclerView
    private lateinit var jointAdapter: RatioAdapter
    private lateinit var bowlAdapter: RatioAdapter
    
    fun show() {
        // Load saved multiplier
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        multiplier = prefs.getInt(PREF_MULTIPLIER, 3)
        
        createDialog()
        loadDefaults()
        loadSavedRatios()
        updateDisplay()
        dialog.show()
    }
    
    fun setOnDismissListener(listener: () -> Unit) {
        if (::dialog.isInitialized) {
            dialog.setOnDismissListener {
                listener()
            }
        }
    }
    
    private fun createDialog() {
        dialog = Dialog(context).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setOnDismissListener {
                onDismissListener?.invoke()
            }
            setContentView(createDialogView())
            window?.setLayout(
                (context.resources.displayMetrics.widthPixels * 0.95).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            window?.setBackgroundDrawableResource(android.R.color.transparent)
        }
    }
    
    private fun createDialogView(): View {
        val mainContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 0)
        }
        
        val cardView = CardView(context).apply {
            radius = 16.dpToPx().toFloat()
            cardElevation = 8.dpToPx().toFloat()
            setCardBackgroundColor(Color.parseColor("#1a1a1a"))
        }
        
        val scrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (context.resources.displayMetrics.heightPixels * 0.8).toInt()
            )
        }
        
        val contentLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dpToPx(), 24.dpToPx(), 24.dpToPx(), 24.dpToPx())
        }
        
        contentLayout.addView(createTitle())
        contentLayout.addView(createInputSection())
        contentLayout.addView(createCalculationSection())
        contentLayout.addView(createTypeSection())
        contentLayout.addView(createNameSection())
        contentLayout.addView(createActionButtons())
        contentLayout.addView(createSavedRatiosSection())
        
        scrollView.addView(contentLayout)
        cardView.addView(scrollView)
        mainContainer.addView(cardView)
        
        return mainContainer
    }
    
    private fun createTitle(): TextView {
        return TextView(context).apply {
            text = "Set Smoke-to-Green Ratio"
            textSize = 20f
            setTextColor(Color.parseColor("#98FB98"))
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 24.dpToPx()
            }
        }
    }
    
    private fun createInputSection(): LinearLayout {
        val inputSection = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 16.dpToPx()
            }
        }
        
        editNumberOfSmokes = createInputField("Cigs/Smoke", "0.5", inputSection).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            addTextChangedListener(createTextWatcher { onSmokesChanged() })
        }
        
        inputSection.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(16.dpToPx(), 1)
        })
        
        editThcPercent = createInputField("THC %", "20", inputSection).apply {
            addTextChangedListener(createTextWatcher { onThcChanged() })
        }
        
        inputSection.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(16.dpToPx(), 1)
        })
        
        editChopAmount = createInputField("Chop (g)", "0.75", inputSection).apply {
            addTextChangedListener(createTextWatcher { onChopChanged() })
        }
        
        // Add space between Chop and Bulk chop
        inputSection.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(16.dpToPx(), 1)
        })
        
        // Add Bulk chop section (non-editable)
        val bulkChopContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        
        TextView(context).apply {
            text = "Bulk chop"
            textSize = 12f
            setTextColor(Color.parseColor("#707070"))
            bulkChopContainer.addView(this)
        }
        
        textBulkChop = TextView(context).apply {
            text = "2.25g"
            textSize = 16f
            setTextColor(Color.parseColor("#98FB98"))
            setBackgroundColor(Color.parseColor("#2a2a2a"))
            setPadding(8.dpToPx(), 8.dpToPx(), 8.dpToPx(), 8.dpToPx())
        }
        bulkChopContainer.addView(textBulkChop)
        inputSection.addView(bulkChopContainer)
        
        return inputSection
    }
    
    private fun createInputField(label: String, defaultValue: String, parent: ViewGroup): EditText {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        
        TextView(context).apply {
            text = label
            textSize = 12f
            setTextColor(Color.parseColor("#707070"))
            container.addView(this)
        }
        
        val editText = EditText(context).apply {
            setText(defaultValue)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setTextColor(Color.parseColor("#98FB98"))
            textSize = 16f
            setBackgroundColor(Color.parseColor("#2a2a2a"))
            setPadding(8.dpToPx(), 8.dpToPx(), 8.dpToPx(), 8.dpToPx())
        }
        
        container.addView(editText)
        parent.addView(container)
        return editText
    }
    
    private fun createCalculationSection(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 16.dpToPx()
            }
            
            val totalContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
                
                addView(TextView(context).apply {
                    text = "Total grams:"
                    textSize = 14f
                    setTextColor(Color.parseColor("#707070"))
                })
                
                textTotalGrams = TextView(context).apply {
                    text = "0.75g"
                    textSize = 18f
                    setTextColor(Color.parseColor("#98FB98"))
                    typeface = Typeface.DEFAULT_BOLD
                }
                addView(textTotalGrams)
            }
            
            val iconContainer = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                gravity = Gravity.CENTER_VERTICAL
                
                // Chain icon
                chainIcon = ImageButton(context).apply {
                    setImageResource(if (isLinked) R.drawable.ic_link else R.drawable.ic_link_off)
                    setBackgroundColor(Color.TRANSPARENT)
                    layoutParams = LinearLayout.LayoutParams(
                        40.dpToPx(),
                        40.dpToPx()
                    ).apply {
                        marginEnd = 8.dpToPx()
                    }
                    setOnClickListener {
                        isLinked = !isLinked
                        setImageResource(if (isLinked) R.drawable.ic_link else R.drawable.ic_link_off)
                        Log.d(TAG, "Chain toggled: isLinked=$isLinked")
                    }
                }
                addView(chainIcon)
                
                // Multiplier button as TextView
                multiplierButton = TextView(context).apply {
                    text = "×$multiplier"
                    textSize = 14f
                    setTextColor(Color.parseColor("#98FB98"))
                    typeface = Typeface.DEFAULT_BOLD
                    setPadding(8.dpToPx(), 4.dpToPx(), 8.dpToPx(), 4.dpToPx())
                    background = GradientDrawable().apply {
                        setColor(Color.TRANSPARENT)
                        setStroke(2.dpToPx(), Color.parseColor("#98FB98"))
                        cornerRadius = 4.dpToPx().toFloat()
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    setOnClickListener {
                        showMultiplierDialog()
                    }
                }
                addView(multiplierButton)
            }
            
            addView(totalContainer)
            addView(iconContainer)
        }
    }
    
    private fun createTypeSection(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 16.dpToPx()
            }
            
            TextView(context).apply {
                text = "Type: "
                textSize = 16f
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.CENTER_VERTICAL
                }
                addView(this)
            }
            
            radioGroup = RadioGroup(context).apply {
                orientation = RadioGroup.HORIZONTAL
                
                radioJoint = RadioButton(context).apply {
                    id = View.generateViewId()
                    text = "Joint"
                    setTextColor(Color.WHITE)
                }
                addView(radioJoint)
                
                radioBowl = RadioButton(context).apply {
                    id = View.generateViewId()
                    text = "Bowl"
                    setTextColor(Color.WHITE)
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        marginStart = 24.dpToPx()
                    }
                }
                addView(radioBowl)
                
                setOnCheckedChangeListener { _, _ ->
                    loadSavedRatios()
                }
            }
            
            addView(radioGroup)
        }
    }
    
    private fun createNameSection(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 16.dpToPx()
            }
            
            TextView(context).apply {
                text = "Ratio Name:"
                textSize = 14f
                setTextColor(Color.parseColor("#707070"))
                addView(this)
            }
            
            editRatioName = EditText(context).apply {
                hint = "e.g., Evening Mix"
                setTextColor(Color.parseColor("#98FB98"))
                setHintTextColor(Color.parseColor("#505050"))
                textSize = 16f
                setBackgroundColor(Color.parseColor("#2a2a2a"))
                setPadding(8.dpToPx(), 8.dpToPx(), 8.dpToPx(), 8.dpToPx())
            }
            addView(editRatioName)
        }
    }
    
    private fun createActionButtons(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 24.dpToPx()
            }
            
            val saveButton = TextView(context).apply {
                text = "Create"
                setTextColor(Color.parseColor("#98FB98"))
                textSize = 16f
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#1a1a1a"))
                    setStroke(1.dpToPx(), Color.parseColor("#98FB98"))
                    cornerRadius = 24.dpToPx().toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    48.dpToPx(),
                    1f
                ).apply {
                    marginEnd = 8.dpToPx()
                }
                setOnClickListener { saveRatio() }
            }
            
            val cancelButton = TextView(context).apply {
                text = "Back"
                setTextColor(Color.parseColor("#98FB98"))
                textSize = 16f
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#1a1a1a"))
                    setStroke(2.dpToPx(), Color.parseColor("#707070"))
                    cornerRadius = 24.dpToPx().toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    48.dpToPx(),
                    1f
                ).apply {
                    marginStart = 8.dpToPx()
                }
                setOnClickListener { dialog.dismiss() }
            }
            
            addView(saveButton)
            addView(cancelButton)
        }
    }
    
    private fun createSavedRatiosSection(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            
            TextView(context).apply {
                text = "▼ Saved Joint Ratios"
                textSize = 16f
                setTextColor(Color.parseColor("#98FB98"))
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 8.dpToPx()
                }
                addView(this)
            }
            
            recyclerJointRatios = RecyclerView(context).apply {
                layoutManager = LinearLayoutManager(context)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    150.dpToPx()
                )
                jointAdapter = RatioAdapter(SmokeRatio.RatioType.JOINT)
                adapter = jointAdapter
                
                // Enable scrollbar and make it always visible
                isVerticalScrollBarEnabled = true
                scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
                scrollBarSize = 6.dpToPx()
                setScrollbarFadingEnabled(false)  // Keep scrollbar always visible
                
                // Set scrollbar thumb color to neon green (requires API 29+)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    verticalScrollbarThumbDrawable = GradientDrawable().apply {
                        setColor(Color.parseColor("#98FB98"))
                        cornerRadius = 3.dpToPx().toFloat()
                    }
                    verticalScrollbarTrackDrawable = GradientDrawable().apply {
                        setColor(Color.parseColor("#33707070"))
                    }
                }
            }
            addView(recyclerJointRatios)
            
            TextView(context).apply {
                text = "▼ Saved Bowl Ratios"
                textSize = 16f
                setTextColor(Color.parseColor("#98FB98"))
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 16.dpToPx()
                    bottomMargin = 8.dpToPx()
                }
                addView(this)
            }
            
            recyclerBowlRatios = RecyclerView(context).apply {
                layoutManager = LinearLayoutManager(context)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    150.dpToPx()
                )
                bowlAdapter = RatioAdapter(SmokeRatio.RatioType.BOWL)
                adapter = bowlAdapter
                
                // Enable scrollbar and make it always visible
                isVerticalScrollBarEnabled = true
                scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
                scrollBarSize = 6.dpToPx()
                setScrollbarFadingEnabled(false)  // Keep scrollbar always visible
                
                // Set scrollbar thumb color to neon green (requires API 29+)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    verticalScrollbarThumbDrawable = GradientDrawable().apply {
                        setColor(Color.parseColor("#98FB98"))
                        cornerRadius = 3.dpToPx().toFloat()
                    }
                    verticalScrollbarTrackDrawable = GradientDrawable().apply {
                        setColor(Color.parseColor("#33707070"))
                    }
                }
            }
            addView(recyclerBowlRatios)
        }
    }
    
    private fun createTextWatcher(onTextChanged: () -> Unit): TextWatcher {
        return object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!isUpdatingValues) {
                    onTextChanged()
                }
            }
        }
    }
    
    private fun onSmokesChanged() {
        if (isUpdatingValues) return
        
        // Parse cigarettes per smoke directly from UI
        val cigsPerSmoke = editNumberOfSmokes.text.toString().toDoubleOrNull() ?: 0.5
        // Calculate number of smokes from cigarettes per smoke (inverse relationship)
        val smokes = if (cigsPerSmoke > 0 && cigsPerSmoke <= 1.0) {
            Math.round(1.0 / cigsPerSmoke).toInt().coerceAtLeast(1)
        } else 1
        val thcPercent = editThcPercent.text.toString().toDoubleOrNull() ?: 20.0
        val chopAmount = editChopAmount.text.toString().toDoubleOrNull() ?: 0.75
        
        Log.d(TAG, "🔢 Smokes changed: $lastSmokesValue -> $smokes (linked=$isLinked)")
        Log.d(TAG, "   Current: THC=$thcPercent%, Chop=${chopAmount}g")
        
        if (isLinked && lastSmokesValue > 0) {
            // In linked mode, maintain the same per-smoke amount
            val perSmoke = chopAmount / lastSmokesValue
            var newChop = perSmoke * smokes
            
            // Apply reasonable bounds
            if (perSmoke < 0.1 && newChop < 0.1 * smokes) {
                newChop = 0.1 * smokes
                Log.d(TAG, "   ⚠️ Adjusted to minimum 0.1g per smoke")
            } else if (perSmoke > 3.0) {
                newChop = kotlin.math.min(newChop, 3.0 * smokes)
                Log.d(TAG, "   ⚠️ Capped at maximum 3g per smoke")
            }
            
            Log.d(TAG, "   📊 Linked: Maintaining ${perSmoke}g per smoke")
            Log.d(TAG, "   New total: ${perSmoke}g × $smokes = ${newChop}g")
            
            isUpdatingValues = true
            editChopAmount.setText(decimalFormat.format(newChop))
            isUpdatingValues = false
        } else {
            Log.d(TAG, "   📊 Unlinked: Chop stays at ${chopAmount}g")
            Log.d(TAG, "   Per smoke changes to: ${chopAmount/smokes}g")
        }
        
        lastSmokesValue = smokes
        updateDisplay()
    }
    
    private fun onThcChanged() {
        if (isUpdatingValues) return
        
        // Parse cigarettes per smoke directly from UI
        val cigsPerSmoke = editNumberOfSmokes.text.toString().toDoubleOrNull() ?: 0.5
        // Calculate number of smokes from cigarettes per smoke (inverse relationship)
        val smokes = if (cigsPerSmoke > 0 && cigsPerSmoke <= 1.0) {
            Math.round(1.0 / cigsPerSmoke).toInt().coerceAtLeast(1)
        } else 1
        val thcPercent = editThcPercent.text.toString().toDoubleOrNull() ?: 20.0
        val currentChop = editChopAmount.text.toString().toDoubleOrNull() ?: 0.75
        
        Log.d(TAG, "💚 THC changed: $lastThcValue% -> $thcPercent% (linked=$isLinked)")
        Log.d(TAG, "   Before: Smokes=$smokes, Chop=${currentChop}g")
        
        if (isLinked && lastThcValue > 0 && thcPercent > 0) {
            // EXACT mathematical inverse proportion
            // When THC% doubles, chop amount halves (and vice versa)
            // Formula: newChop = currentChop × (lastThc / newThc)
            
            val newChop = currentChop * (lastThcValue / thcPercent)
            
            Log.d(TAG, "   📊 Linked calc: Exact inverse proportion")
            Log.d(TAG, "   Formula: ${currentChop}g × (${lastThcValue}% / ${thcPercent}%)")
            Log.d(TAG, "   New chop: ${currentChop}g × ${lastThcValue/thcPercent} = ${newChop}g")
            Log.d(TAG, "   Verification - THC amounts:")
            Log.d(TAG, "   Before: ${currentChop}g × ${lastThcValue}% = ${currentChop * lastThcValue/100}g pure THC")
            Log.d(TAG, "   After: ${newChop}g × ${thcPercent}% = ${newChop * thcPercent/100}g pure THC")
            Log.d(TAG, "   Per smoke: ${newChop/smokes}g")
            
            isUpdatingValues = true
            editChopAmount.setText(decimalFormat.format(newChop))
            isUpdatingValues = false
        } else {
            Log.d(TAG, "   📊 Unlinked: Chop stays at ${currentChop}g")
            Log.d(TAG, "   Per smoke stays: ${currentChop/smokes}g")
        }
        
        lastThcValue = thcPercent
        updateDisplay()
    }
    
    private fun onChopChanged() {
        if (isUpdatingValues) return
        
        // Parse cigarettes per smoke directly from UI
        val cigsPerSmoke = editNumberOfSmokes.text.toString().toDoubleOrNull() ?: 0.5
        // Calculate number of smokes from cigarettes per smoke (inverse relationship)
        val smokes = if (cigsPerSmoke > 0 && cigsPerSmoke <= 1.0) {
            Math.round(1.0 / cigsPerSmoke).toInt().coerceAtLeast(1)
        } else 1
        val thcPercent = editThcPercent.text.toString().toDoubleOrNull() ?: 20.0
        val chopAmount = editChopAmount.text.toString().toDoubleOrNull() ?: 0.75
        
        Log.d(TAG, "🌿 Chop changed to ${chopAmount}g (linked=$isLinked)")
        Log.d(TAG, "   Current: Smokes=$smokes, THC=$thcPercent%")
        Log.d(TAG, "   Per smoke: ${chopAmount/smokes}g")
        
        // In both linked and unlinked, changing chop doesn't change other fields
        // It just changes the amount per smoke
        
        updateDisplay()
    }
    
    private fun updateDisplay() {
        try {
            // Parse cigarettes per smoke directly from UI
            val cigsPerSmoke = editNumberOfSmokes.text.toString().toDoubleOrNull() ?: 0.5
            // Calculate number of smokes from cigarettes per smoke (inverse relationship)
            val smokes = if (cigsPerSmoke > 0 && cigsPerSmoke <= 1.0) {
                Math.round(1.0 / cigsPerSmoke).toInt().coerceAtLeast(1)
            } else 1
            val chopAmount = editChopAmount.text.toString().toDoubleOrNull() ?: 0.75
            
            // Calculate tobacco weight (0.70g per cigarette)
            val tobaccoWeight = cigsPerSmoke * 0.70
            val totalWeight = chopAmount + tobaccoWeight
            
            // Update total grams (cannabis + tobacco)
            textTotalGrams.text = "${decimalFormat.format(totalWeight)}g"
            
            // Update bulk chop (chop × multiplier)
            if (::textBulkChop.isInitialized) {
                val bulkChopAmount = chopAmount * multiplier
                textBulkChop.text = "${decimalFormat.format(bulkChopAmount)}g"
            }
            
            // Update multiplier button text
            if (::multiplierButton.isInitialized) {
                updateMultiplierButton()
            }
            
            Log.d(TAG, "📱 Display updated: Chop=${chopAmount}g, Tobacco=${tobaccoWeight}g, Total=${totalWeight}g")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating display", e)
        }
    }
    
    private fun loadDefaults() {
        // Load last type
        val lastType = ratioManager.getLastType()
        when (lastType) {
            SmokeRatio.RatioType.JOINT -> radioJoint.isChecked = true
            SmokeRatio.RatioType.BOWL -> radioBowl.isChecked = true
        }
        
        // Load last defaults if available
        val defaults = ratioManager.getLastDefaults()
        if (defaults != null) {
            isUpdatingValues = true
            // Convert number of smokes to cigarettes per smoke for display
            val cigsPerSmoke = if (defaults.first > 0) 1.0 / defaults.first else 0.5
            editNumberOfSmokes.setText(decimalFormat.format(cigsPerSmoke))
            editThcPercent.setText(decimalFormat.format(defaults.second))
            editChopAmount.setText(decimalFormat.format(defaults.third))
            lastSmokesValue = defaults.first
            lastThcValue = defaults.second
            isUpdatingValues = false
        } else {
            // Set initial tracking values
            lastSmokesValue = 3
            lastThcValue = 20.0
        }
        
        updateDisplay()
    }
    
    private fun saveRatio() {
        try {
            val name = editRatioName.text.toString().trim()
            val type = if (radioJoint.isChecked) SmokeRatio.RatioType.JOINT else SmokeRatio.RatioType.BOWL
            
            if (name.isNotEmpty() && !ratioManager.isNameUnique(name, type, null)) {
                Toast.makeText(context, "A ratio with this name already exists", Toast.LENGTH_SHORT).show()
                return
            }
            
            // Parse cigarettes per smoke directly from UI
            val cigsPerSmoke = editNumberOfSmokes.text.toString().toDoubleOrNull() ?: 0.5
            // Calculate number of smokes from cigarettes per smoke (inverse relationship)
            val smokes = if (cigsPerSmoke > 0) (1.0 / cigsPerSmoke).toInt().coerceAtLeast(1) else 1
            val thcPercent = editThcPercent.text.toString().toDoubleOrNull() ?: 20.0
            val chopAmount = editChopAmount.text.toString().toDoubleOrNull() ?: 0.75
            
            val ratio = SmokeRatio.createNew(
                numberOfSmokes = smokes,
                thcPercent = thcPercent,
                chopAmount = chopAmount,
                type = type
            ).copy(
                id = java.util.UUID.randomUUID().toString(),
                name = if (name.isEmpty()) ratioManager.generateAutoName(type, thcPercent) else name,
                createdAt = System.currentTimeMillis()
            )
            
            Log.d(TAG, "🚬 Created ratio: ${ratio.name}, numberOfSmokes=$smokes, cigarettesPerSmoke=${ratio.cigarettesPerSmoke} (should be ${1.0/smokes})")
            
            ratioManager.saveRatio(ratio)
            // Auto-select the newly created ratio
            ratioManager.setSelectedRatio(ratio.id, type)
            ratioManager.saveLastDefaults(smokes, thcPercent, chopAmount, type)
            onRatioSaved(ratio)
            loadSavedRatios()
            Toast.makeText(context, "Ratio created", Toast.LENGTH_SHORT).show()
            clearFields()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving ratio", e)
            Toast.makeText(context, "Error saving ratio", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun clearFields() {
        editRatioName.setText("")
        // Keep values for next ratio
    }
    
    private fun loadSavedRatios() {
        // Both ratio lists should always be visible
        if (::recyclerJointRatios.isInitialized) {
            recyclerJointRatios.visibility = View.VISIBLE
            jointAdapter.loadRatios()
            
            // Auto-select if only one ratio
            val jointRatios = ratioManager.getRatiosForType(SmokeRatio.RatioType.JOINT)
            if (jointRatios.size == 1 && jointRatios.none { it.isSelected }) {
                ratioManager.setSelectedRatio(jointRatios[0].id, SmokeRatio.RatioType.JOINT)
                jointAdapter.loadRatios()
            }
        }
        
        if (::recyclerBowlRatios.isInitialized) {
            recyclerBowlRatios.visibility = View.VISIBLE
            bowlAdapter.loadRatios()
            
            // Auto-select if only one ratio
            val bowlRatios = ratioManager.getRatiosForType(SmokeRatio.RatioType.BOWL)
            if (bowlRatios.size == 1 && bowlRatios.none { it.isSelected }) {
                ratioManager.setSelectedRatio(bowlRatios[0].id, SmokeRatio.RatioType.BOWL)
                bowlAdapter.loadRatios()
            }
        }
    }
    
    private inner class RatioAdapter(private val type: SmokeRatio.RatioType) : RecyclerView.Adapter<RatioViewHolder>() {
        private var ratios = listOf<SmokeRatio>()
        
        fun loadRatios() {
            // Sort ratios by newest first (most recent createdAt timestamp at top)
            ratios = ratioManager.getRatiosForType(type).sortedByDescending { it.createdAt }
            notifyDataSetChanged()
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RatioViewHolder {
            val cardView = CardView(context).apply {
                radius = 8.dpToPx().toFloat()
                cardElevation = 4.dpToPx().toFloat()
                setCardBackgroundColor(Color.parseColor("#2a2a2a"))
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 4.dpToPx(), 0, 4.dpToPx())
                }
            }
            return RatioViewHolder(cardView)
        }
        
        override fun onBindViewHolder(holder: RatioViewHolder, position: Int) {
            holder.bind(ratios[position])
        }
        
        override fun getItemCount() = ratios.size
    }
    
    private inner class RatioViewHolder(private val cardView: CardView) : RecyclerView.ViewHolder(cardView) {
        fun bind(ratio: SmokeRatio) {
            cardView.removeAllViews()
            
            // Always reset the background first
            cardView.background = null
            cardView.setCardBackgroundColor(Color.parseColor("#2a2a2a"))
            
            if (ratio.isSelected) {
                val strokeDrawable = GradientDrawable().apply {
                    setColor(Color.parseColor("#2a2a2a"))
                    setStroke(2.dpToPx(), Color.parseColor("#98FB98"))
                    cornerRadius = 8.dpToPx().toFloat()
                }
                cardView.background = strokeDrawable
            }
            
            val container = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(12.dpToPx(), 12.dpToPx(), 12.dpToPx(), 12.dpToPx())
                
                setOnClickListener {
                    if (!ratio.isSelected) {
                        ratioManager.setSelectedRatio(ratio.id, ratio.type)
                        loadSavedRatios()
                        Toast.makeText(context, "${ratio.name} selected", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            
            val textContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }
            
            TextView(context).apply {
                text = if (ratio.isSelected) "✓ ${ratio.name}" else ratio.name
                textSize = 16f
                setTextColor(if (ratio.isSelected) Color.parseColor("#98FB98") else Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
                textContainer.addView(this)
            }
            
            TextView(context).apply {
                val bulkChop = ratio.chopAmount * multiplier
                text = "${decimalFormat.format(ratio.cigarettesPerSmoke)} cigs/smoke, ${ratio.thcPercent.toInt()}% THC, ${decimalFormat.format(ratio.chopAmount)}g chop, ×$multiplier ${decimalFormat.format(bulkChop)}g"
                textSize = 12f
                setTextColor(Color.parseColor("#707070"))
                textContainer.addView(this)
            }
            
            // Button container for edit and delete
            val buttonContainer = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            
            // Edit button
            ImageButton(context).apply {
                setImageResource(R.drawable.ic_edit)
                setBackgroundColor(Color.TRANSPARENT)
                layoutParams = LinearLayout.LayoutParams(32.dpToPx(), 32.dpToPx()).apply {
                    marginEnd = 8.dpToPx()
                }
                setOnClickListener {
                    showEditDialog(ratio)
                }
                buttonContainer.addView(this)
            }
            
            // Delete button
            ImageButton(context).apply {
                setImageResource(R.drawable.ic_delete)
                setBackgroundColor(Color.TRANSPARENT)
                layoutParams = LinearLayout.LayoutParams(32.dpToPx(), 32.dpToPx())
                setOnClickListener {
                    showDeleteConfirmationDialog(ratio)
                }
                buttonContainer.addView(this)
            }
            
            container.addView(textContainer)
            container.addView(buttonContainer)
            
            cardView.addView(container)
        }
    }
    
    private fun showEditDialog(ratio: SmokeRatio) {
        val editDialog = Dialog(context).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            window?.setBackgroundDrawableResource(android.R.color.transparent)
        }
        
        val cardView = CardView(context).apply {
            radius = 16.dpToPx().toFloat()
            cardElevation = 8.dpToPx().toFloat()
            setCardBackgroundColor(Color.parseColor("#1a1a1a"))
        }
        
        val contentLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dpToPx(), 24.dpToPx(), 24.dpToPx(), 24.dpToPx())
        }
        
        // Title
        TextView(context).apply {
            text = "Edit Ratio"
            textSize = 20f
            setTextColor(Color.parseColor("#98FB98"))
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 24.dpToPx()
            }
            contentLayout.addView(this)
        }
        
        // Name (display only)
        TextView(context).apply {
            text = ratio.name
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 16.dpToPx()
            }
            contentLayout.addView(this)
        }
        
        // Input fields container
        val inputContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 24.dpToPx()
            }
        }
        
        // Cigarettes per smoke field
        val smokesContainer = createEditField("Cigs/Smoke", decimalFormat.format(ratio.cigarettesPerSmoke))
        val editSmokes = smokesContainer.getChildAt(1) as EditText
        editSmokes.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        inputContainer.addView(smokesContainer)
        
        inputContainer.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(16.dpToPx(), 1)
        })
        
        // THC% field
        val thcContainer = createEditField("THC %", ratio.thcPercent.toString())
        val editThc = thcContainer.getChildAt(1) as EditText
        inputContainer.addView(thcContainer)
        
        inputContainer.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(16.dpToPx(), 1)
        })
        
        // Chop field
        val chopContainer = createEditField("Chop (g)", decimalFormat.format(ratio.chopAmount))
        val editChop = chopContainer.getChildAt(1) as EditText
        inputContainer.addView(chopContainer)
        
        contentLayout.addView(inputContainer)
        
        // Action buttons
        val buttonLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        
        TextView(context).apply {
            text = "Save"
            setTextColor(Color.parseColor("#98FB98"))
            textSize = 16f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1a1a1a"))
                setStroke(2.dpToPx(), Color.parseColor("#98FB98"))
                cornerRadius = 24.dpToPx().toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                0,
                48.dpToPx(),
                1f
            ).apply {
                marginEnd = 8.dpToPx()
            }
            setOnClickListener {
                val newCigsPerSmoke = editSmokes.text.toString().toDoubleOrNull() ?: ratio.cigarettesPerSmoke
                val newSmokes = if (newCigsPerSmoke > 0 && newCigsPerSmoke <= 1.0) {
                    Math.round(1.0 / newCigsPerSmoke).toInt().coerceAtLeast(1)
                } else ratio.numberOfSmokes
                val newThc = editThc.text.toString().toDoubleOrNull() ?: ratio.thcPercent
                val newChop = editChop.text.toString().toDoubleOrNull() ?: ratio.chopAmount
                
                // Recalculate cigarettes per smoke when number of smokes changes
                val newCigarettesPerSmoke = 1.0 / newSmokes.toDouble()
                
                val updatedRatio = ratio.copy(
                    numberOfSmokes = newSmokes,
                    thcPercent = newThc,
                    chopAmount = newChop,
                    cigarettesPerSmoke = newCigarettesPerSmoke,
                    lastModified = System.currentTimeMillis()
                )
                
                ratioManager.saveRatio(updatedRatio)
                loadSavedRatios()
                editDialog.dismiss()
                Toast.makeText(context, "Ratio updated", Toast.LENGTH_SHORT).show()
            }
            buttonLayout.addView(this)
        }
        
        TextView(context).apply {
            text = "Back"
            setTextColor(Color.parseColor("#98FB98"))
            textSize = 16f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1a1a1a"))
                setStroke(2.dpToPx(), Color.parseColor("#707070"))
                cornerRadius = 24.dpToPx().toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                0,
                48.dpToPx(),
                1f
            ).apply {
                marginStart = 8.dpToPx()
            }
            setOnClickListener {
                editDialog.dismiss()
            }
            buttonLayout.addView(this)
        }
        
        contentLayout.addView(buttonLayout)
        cardView.addView(contentLayout)
        
        editDialog.setContentView(cardView)
        editDialog.window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.9).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        editDialog.show()
    }
    
    private fun createEditField(label: String, value: String): LinearLayout {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        
        TextView(context).apply {
            text = label
            textSize = 12f
            setTextColor(Color.parseColor("#707070"))
            container.addView(this)
        }
        
        EditText(context).apply {
            setText(value)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setTextColor(Color.parseColor("#98FB98"))
            textSize = 16f
            setBackgroundColor(Color.parseColor("#2a2a2a"))
            setPadding(8.dpToPx(), 8.dpToPx(), 8.dpToPx(), 8.dpToPx())
            container.addView(this)
        }
        
        return container
    }
    
    private fun showDeleteConfirmationDialog(ratio: SmokeRatio) {
        val dialog = Dialog(context)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1a1a1a"))
            setPadding(24.dpToPx(), 24.dpToPx(), 24.dpToPx(), 24.dpToPx())
        }
        
        TextView(context).apply {
            text = "Delete Ratio"
            textSize = 18f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 16.dpToPx())
            container.addView(this)
        }
        
        TextView(context).apply {
            text = "Are you sure you want to delete \"${ratio.name}\"?"
            textSize = 14f
            setTextColor(Color.parseColor("#CCCCCC"))
            setPadding(0, 0, 0, 20.dpToPx())
            container.addView(this)
        }
        
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            
            Button(context).apply {
                text = "Cancel"
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#333333"))
                setOnClickListener { dialog.dismiss() }
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    marginEnd = 8.dpToPx()
                }
                addView(this)
            }
            
            Button(context).apply {
                text = "Delete"
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#CC4444"))
                setOnClickListener {
                    ratioManager.deleteRatio(ratio.id, ratio.type)
                    loadSavedRatios()
                    Toast.makeText(context, "Ratio deleted", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    marginStart = 8.dpToPx()
                }
                addView(this)
            }
            
            container.addView(this)
        }
        
        dialog.setContentView(container)
        dialog.show()
    }
    
    private fun showMultiplierDialog() {
        val dialog = Dialog(context)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1a1a1a"))
            setPadding(24.dpToPx(), 24.dpToPx(), 24.dpToPx(), 24.dpToPx())
        }
        
        TextView(context).apply {
            text = "Set Multiplier"
            textSize = 18f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 16.dpToPx())
            container.addView(this)
        }
        
        val editMultiplier = EditText(context).apply {
            setText(multiplier.toString())
            inputType = InputType.TYPE_CLASS_NUMBER
            setTextColor(Color.parseColor("#98FB98"))
            textSize = 20f
            setBackgroundColor(Color.parseColor("#2a2a2a"))
            setPadding(12.dpToPx(), 12.dpToPx(), 12.dpToPx(), 12.dpToPx())
            setSelection(text.length)
        }
        container.addView(editMultiplier)
        
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 16.dpToPx(), 0, 0)
            
            Button(context).apply {
                text = "Cancel"
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#333333"))
                setOnClickListener { dialog.dismiss() }
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    marginEnd = 8.dpToPx()
                }
                addView(this)
            }
            
            Button(context).apply {
                text = "Done"
                setTextColor(Color.BLACK)
                setBackgroundColor(Color.parseColor("#98FB98"))
                setOnClickListener {
                    val newMultiplier = editMultiplier.text.toString().toIntOrNull()
                    if (newMultiplier != null && newMultiplier > 0) {
                        multiplier = newMultiplier
                        // Save to preferences
                        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                        prefs.edit().putInt(PREF_MULTIPLIER, multiplier).apply()
                        updateDisplay()
                        // Reload the ratio lists to show updated multiplier
                        loadSavedRatios()
                        dialog.dismiss()
                    } else {
                        Toast.makeText(context, "Please enter a valid number", Toast.LENGTH_SHORT).show()
                    }
                }
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    marginStart = 8.dpToPx()
                }
                addView(this)
            }
            
            container.addView(this)
        }
        
        dialog.setContentView(container)
        dialog.show()
    }
    
    private fun updateMultiplierButton() {
        multiplierButton.text = "×$multiplier"
    }
    
    private fun Int.dpToPx(): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }
}