package com.sam.cloudcounter

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.firebase.Timestamp
import kotlinx.coroutines.launch

class StatsControlsDialog : DialogFragment() {

    companion object {
        private const val TAG = "StatsControlsDialog"

        fun newInstance(
            currentStats: SamStats,
            currentAdjustments: StatsAdjustments
        ): StatsControlsDialog {
            val fragment = StatsControlsDialog()
            val args = Bundle().apply {
                putInt("todayCones", currentStats.todayCones)
                putInt("todayJoints", currentStats.todayJoints)
                putInt("allTimeCones", currentStats.allTimeCones)
                putInt("allTimeJoints", currentStats.allTimeJoints)
                putInt("todayConesAdj", currentAdjustments.todayConesAdjustment)
                putInt("todayJointsAdj", currentAdjustments.todayJointsAdjustment)
                putInt("allTimeConesAdj", currentAdjustments.allTimeConesAdjustment)
                putInt("allTimeJointsAdj", currentAdjustments.allTimeJointsAdjustment)
            }
            fragment.arguments = args
            return fragment
        }
    }

    private lateinit var statsManager: StatsManager
    private var currentAdjustments = StatsAdjustments()

    // UI elements
    private lateinit var textTodayConesActual: TextView
    private lateinit var textTodayConesAdjustment: TextView
    private lateinit var textTodayConesDisplay: TextView
    private lateinit var editTodayCones: EditText
    private lateinit var btnTodayConesPlus: ImageButton
    private lateinit var btnTodayConesMinus: ImageButton

    private lateinit var textTodayJointsActual: TextView
    private lateinit var textTodayJointsAdjustment: TextView
    private lateinit var textTodayJointsDisplay: TextView
    private lateinit var editTodayJoints: EditText
    private lateinit var btnTodayJointsPlus: ImageButton
    private lateinit var btnTodayJointsMinus: ImageButton

    private lateinit var textAllTimeConesActual: TextView
    private lateinit var textAllTimeConesAdjustment: TextView
    private lateinit var textAllTimeConesDisplay: TextView
    private lateinit var editAllTimeCones: EditText
    private lateinit var btnAllTimeConesPlus: ImageButton
    private lateinit var btnAllTimeConesMinus: ImageButton

    private lateinit var textAllTimeJointsActual: TextView
    private lateinit var textAllTimeJointsAdjustment: TextView
    private lateinit var textAllTimeJointsDisplay: TextView
    private lateinit var editAllTimeJoints: EditText
    private lateinit var btnAllTimeJointsPlus: ImageButton
    private lateinit var btnAllTimeJointsMinus: ImageButton

    private lateinit var btnSave: Button
    private lateinit var btnReset: Button

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireContext())
        val inflater = requireActivity().layoutInflater
        val view = inflater.inflate(R.layout.dialog_stats_controls, null)

        setupViews(view)
        loadCurrentValues()
        setupListeners()

        builder.setView(view)
            .setTitle("Stats Controls")
            .setNegativeButton("Cancel") { _, _ -> dismiss() }

        return builder.create()
    }

    private fun setupViews(view: View) {
        // Today's Cones
        textTodayConesActual = view.findViewById(R.id.textTodayConesActual)
        textTodayConesAdjustment = view.findViewById(R.id.textTodayConesAdjustment)
        textTodayConesDisplay = view.findViewById(R.id.textTodayConesDisplay)
        editTodayCones = view.findViewById(R.id.editTodayCones)
        btnTodayConesPlus = view.findViewById(R.id.btnTodayConesPlus)
        btnTodayConesMinus = view.findViewById(R.id.btnTodayConesMinus)

        // Today's Joints
        textTodayJointsActual = view.findViewById(R.id.textTodayJointsActual)
        textTodayJointsAdjustment = view.findViewById(R.id.textTodayJointsAdjustment)
        textTodayJointsDisplay = view.findViewById(R.id.textTodayJointsDisplay)
        editTodayJoints = view.findViewById(R.id.editTodayJoints)
        btnTodayJointsPlus = view.findViewById(R.id.btnTodayJointsPlus)
        btnTodayJointsMinus = view.findViewById(R.id.btnTodayJointsMinus)

        // All-Time Cones
        textAllTimeConesActual = view.findViewById(R.id.textAllTimeConesActual)
        textAllTimeConesAdjustment = view.findViewById(R.id.textAllTimeConesAdjustment)
        textAllTimeConesDisplay = view.findViewById(R.id.textAllTimeConesDisplay)
        editAllTimeCones = view.findViewById(R.id.editAllTimeCones)
        btnAllTimeConesPlus = view.findViewById(R.id.btnAllTimeConesPlus)
        btnAllTimeConesMinus = view.findViewById(R.id.btnAllTimeConesMinus)

        // All-Time Joints
        textAllTimeJointsActual = view.findViewById(R.id.textAllTimeJointsActual)
        textAllTimeJointsAdjustment = view.findViewById(R.id.textAllTimeJointsAdjustment)
        textAllTimeJointsDisplay = view.findViewById(R.id.textAllTimeJointsDisplay)
        editAllTimeJoints = view.findViewById(R.id.editAllTimeJoints)
        btnAllTimeJointsPlus = view.findViewById(R.id.btnAllTimeJointsPlus)
        btnAllTimeJointsMinus = view.findViewById(R.id.btnAllTimeJointsMinus)

        btnSave = view.findViewById(R.id.btnSaveAdjustments)
        btnReset = view.findViewById(R.id.btnResetAdjustments)
    }

    private fun loadCurrentValues() {
        val args = arguments ?: return

        val todayCones = args.getInt("todayCones")
        val todayJoints = args.getInt("todayJoints")
        val allTimeCones = args.getInt("allTimeCones")
        val allTimeJoints = args.getInt("allTimeJoints")

        currentAdjustments = StatsAdjustments(
            todayConesAdjustment = args.getInt("todayConesAdj"),
            todayJointsAdjustment = args.getInt("todayJointsAdj"),
            allTimeConesAdjustment = args.getInt("allTimeConesAdj"),
            allTimeJointsAdjustment = args.getInt("allTimeJointsAdj")
        )

        updateDisplay(todayCones, todayJoints, allTimeCones, allTimeJoints)
    }

    private fun updateDisplay(
        actualTodayCones: Int,
        actualTodayJoints: Int,
        actualAllTimeCones: Int,
        actualAllTimeJoints: Int
    ) {
        // Today's Cones
        textTodayConesActual.text = "Actual: $actualTodayCones"
        val todayConesAdj = currentAdjustments.todayConesAdjustment
        textTodayConesAdjustment.text = "Adjustment: ${if (todayConesAdj >= 0) "+" else ""}$todayConesAdj"
        textTodayConesDisplay.text = "Display: ${actualTodayCones + todayConesAdj}"
        editTodayCones.setText(todayConesAdj.toString())

        // Today's Joints
        textTodayJointsActual.text = "Actual: $actualTodayJoints"
        val todayJointsAdj = currentAdjustments.todayJointsAdjustment
        textTodayJointsAdjustment.text = "Adjustment: ${if (todayJointsAdj >= 0) "+" else ""}$todayJointsAdj"
        textTodayJointsDisplay.text = "Display: ${actualTodayJoints + todayJointsAdj}"
        editTodayJoints.setText(todayJointsAdj.toString())

        // All-Time Cones
        textAllTimeConesActual.text = "Actual: $actualAllTimeCones"
        val allTimeConesAdj = currentAdjustments.allTimeConesAdjustment
        textAllTimeConesAdjustment.text = "Adjustment: ${if (allTimeConesAdj >= 0) "+" else ""}$allTimeConesAdj"
        textAllTimeConesDisplay.text = "Display: ${actualAllTimeCones + allTimeConesAdj}"
        editAllTimeCones.setText(allTimeConesAdj.toString())

        // All-Time Joints
        textAllTimeJointsActual.text = "Actual: $actualAllTimeJoints"
        val allTimeJointsAdj = currentAdjustments.allTimeJointsAdjustment
        textAllTimeJointsAdjustment.text = "Adjustment: ${if (allTimeJointsAdj >= 0) "+" else ""}$allTimeJointsAdj"
        textAllTimeJointsDisplay.text = "Display: ${actualAllTimeJoints + allTimeJointsAdj}"
        editAllTimeJoints.setText(allTimeJointsAdj.toString())
    }

    private fun setupListeners() {
        // Today's Cones
        btnTodayConesPlus.setOnClickListener {
            adjustValue { currentAdjustments.todayConesAdjustment++ }
        }
        btnTodayConesMinus.setOnClickListener {
            adjustValue { currentAdjustments.todayConesAdjustment-- }
        }
        editTodayCones.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val value = s.toString().toIntOrNull() ?: 0
                currentAdjustments.todayConesAdjustment = value
                updateDisplayWithoutEditTexts()
            }
        })

        // Today's Joints
        btnTodayJointsPlus.setOnClickListener {
            adjustValue { currentAdjustments.todayJointsAdjustment++ }
        }
        btnTodayJointsMinus.setOnClickListener {
            adjustValue { currentAdjustments.todayJointsAdjustment-- }
        }
        editTodayJoints.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val value = s.toString().toIntOrNull() ?: 0
                currentAdjustments.todayJointsAdjustment = value
                updateDisplayWithoutEditTexts()
            }
        })

        // All-Time Cones
        btnAllTimeConesPlus.setOnClickListener {
            adjustValue { currentAdjustments.allTimeConesAdjustment++ }
        }
        btnAllTimeConesMinus.setOnClickListener {
            adjustValue { currentAdjustments.allTimeConesAdjustment-- }
        }
        editAllTimeCones.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val value = s.toString().toIntOrNull() ?: 0
                currentAdjustments.allTimeConesAdjustment = value
                updateDisplayWithoutEditTexts()
            }
        })

        // All-Time Joints
        btnAllTimeJointsPlus.setOnClickListener {
            adjustValue { currentAdjustments.allTimeJointsAdjustment++ }
        }
        btnAllTimeJointsMinus.setOnClickListener {
            adjustValue { currentAdjustments.allTimeJointsAdjustment-- }
        }
        editAllTimeJoints.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val value = s.toString().toIntOrNull() ?: 0
                currentAdjustments.allTimeJointsAdjustment = value
                updateDisplayWithoutEditTexts()
            }
        })

        // Save button
        btnSave.setOnClickListener {
            saveAdjustments()
        }

        // Reset button
        btnReset.setOnClickListener {
            resetAdjustments()
        }
    }

    // Add this new method to update display without changing edit texts
    private fun updateDisplayWithoutEditTexts() {
        val args = arguments ?: return

        val actualTodayCones = args.getInt("todayCones")
        val actualTodayJoints = args.getInt("todayJoints")
        val actualAllTimeCones = args.getInt("allTimeCones")
        val actualAllTimeJoints = args.getInt("allTimeJoints")

        // Update display texts only (not edit texts)
        textTodayConesActual.text = "Actual: $actualTodayCones"
        val todayConesAdj = currentAdjustments.todayConesAdjustment
        textTodayConesAdjustment.text = "Adjustment: ${if (todayConesAdj >= 0) "+" else ""}$todayConesAdj"
        textTodayConesDisplay.text = "Display: ${actualTodayCones + todayConesAdj}"

        textTodayJointsActual.text = "Actual: $actualTodayJoints"
        val todayJointsAdj = currentAdjustments.todayJointsAdjustment
        textTodayJointsAdjustment.text = "Adjustment: ${if (todayJointsAdj >= 0) "+" else ""}$todayJointsAdj"
        textTodayJointsDisplay.text = "Display: ${actualTodayJoints + todayJointsAdj}"

        textAllTimeConesActual.text = "Actual: $actualAllTimeCones"
        val allTimeConesAdj = currentAdjustments.allTimeConesAdjustment
        textAllTimeConesAdjustment.text = "Adjustment: ${if (allTimeConesAdj >= 0) "+" else ""}$allTimeConesAdj"
        textAllTimeConesDisplay.text = "Display: ${actualAllTimeCones + allTimeConesAdj}"

        textAllTimeJointsActual.text = "Actual: $actualAllTimeJoints"
        val allTimeJointsAdj = currentAdjustments.allTimeJointsAdjustment
        textAllTimeJointsAdjustment.text = "Adjustment: ${if (allTimeJointsAdj >= 0) "+" else ""}$allTimeJointsAdj"
        textAllTimeJointsDisplay.text = "Display: ${actualAllTimeJoints + allTimeJointsAdj}"
    }

    private fun adjustValue(adjustment: () -> Unit) {
        adjustment()
        reloadDisplay()
    }

    private fun reloadDisplay() {
        val args = arguments ?: return
        updateDisplay(
            args.getInt("todayCones"),
            args.getInt("todayJoints"),
            args.getInt("allTimeCones"),
            args.getInt("allTimeJoints")
        )
    }

    private fun saveAdjustments() {
        // Update from edit texts
        currentAdjustments.todayConesAdjustment = editTodayCones.text.toString().toIntOrNull() ?: 0
        currentAdjustments.todayJointsAdjustment = editTodayJoints.text.toString().toIntOrNull() ?: 0
        currentAdjustments.allTimeConesAdjustment = editAllTimeCones.text.toString().toIntOrNull() ?: 0
        currentAdjustments.allTimeJointsAdjustment = editAllTimeJoints.text.toString().toIntOrNull() ?: 0
        currentAdjustments.lastUpdated = Timestamp.now()

        lifecycleScope.launch {
            val app = requireActivity().application as CloudCounterApplication
            val statsManager = StatsManager(requireContext(), app.repository)

            statsManager.saveAdjustments(currentAdjustments).fold(
                onSuccess = {
                    Toast.makeText(context, "Adjustments saved", Toast.LENGTH_SHORT).show()
                    dismiss()
                },
                onFailure = { error ->
                    Toast.makeText(context, "Failed to save: ${error.message}", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    private fun resetAdjustments() {
        currentAdjustments = StatsAdjustments()
        reloadDisplay()
    }
}