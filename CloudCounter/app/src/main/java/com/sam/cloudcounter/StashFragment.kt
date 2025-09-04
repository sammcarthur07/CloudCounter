package com.sam.cloudcounter

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseUser
import com.sam.cloudcounter.databinding.FragmentStashBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.DecimalFormat
import android.widget.EditText
import android.app.Dialog

/**
 * ENHANCED StashFragment with smart add/remove functionality
 * Supports ratio-based calculations and single value entry modes
 */
class StashFragment : Fragment() {

    companion object {
        private const val TAG = "StashFragment"
    }

    private var _binding: FragmentStashBinding? = null
    private val binding get() = _binding!!

    private val prefs by lazy {
        requireContext().getSharedPreferences("stash_prefs", Context.MODE_PRIVATE)
    }

    private lateinit var stashViewModel: StashViewModel
    private lateinit var sessionStatsViewModel: SessionStatsViewModel
    private lateinit var distributionAdapter: StashDistributionAdapter

    private lateinit var authManager: FirebaseAuthManager
    private lateinit var googleSignInClient: GoogleSignInClient

    private val decimalFormat = DecimalFormat("#.##")
    private val currencyFormat = DecimalFormat("$#,##0.00")

    private var currentUserId: String? = null
    private var lastCompletedSessionId: Long? = null

    private var lastScrollPosition = 0

    private var theirStashTotalGrams = 0.0
    private var theirStashTotalCost = 0.0

    private val googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)!!
            lifecycleScope.launch {
                authManager.firebaseAuthWithGoogle(account.idToken!!) { firebaseUser ->
                    updateUI(firebaseUser)
                }
            }
        } catch (e: ApiException) {
            Log.w("StashFragment", "Google sign in failed", e)
            updateUI(null)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentStashBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail().build()
        googleSignInClient = GoogleSignIn.getClient(requireActivity(), gso)

        authManager = (requireActivity().application as CloudCounterApplication).authManager
        stashViewModel = ViewModelProvider(requireActivity()).get(StashViewModel::class.java)
        sessionStatsViewModel = ViewModelProvider(requireActivity(), SessionStatsViewModelFactory()).get(SessionStatsViewModel::class.java)

        binding.layoutTheirStashCounter.visibility = View.VISIBLE

        setupWindowInsets()
        setupUI()
        setupListeners()
        observeViewModels()
        observeSessionState()

        // Sync radio button with ViewModel on view creation
        syncRadioWithViewModel()

        // Also observe stash source changes to ensure radio stays in sync
        stashViewModel.stashSource.observe(viewLifecycleOwner) { source ->
            Log.d(TAG, "🎯 StashSource changed in ViewModel to: $source")
            val currentRadioId = binding.radioGroupAttribution.checkedRadioButtonId
            val expectedRadioId = when(source) {
                StashSource.MY_STASH -> R.id.radioMyStashAttribution
                StashSource.THEIR_STASH -> R.id.radioTheirStashAttribution
                StashSource.EACH_TO_OWN -> R.id.radioEachToOwnAttribution
            }

            if (currentRadioId != expectedRadioId) {
                Log.d(TAG, "🎯 Radio out of sync! Fixing...")
                setAttributionRadioSilently(source)
            }
        }

        lifecycleScope.launch {
            calculateTheirStashConsumption()
        }
    }

    override fun onStart() {
        super.onStart()
        updateUI(authManager.getCurrentUser())
    }
    
    override fun onResume() {
        super.onResume()
        Log.d("PROJ_TIMER", "StashFragment.onResume() called")
        // Start projection timer if needed when fragment becomes visible
        stashViewModel.onStashTabResumed()
    }
    
    override fun onPause() {
        super.onPause()
        Log.d("PROJ_TIMER", "StashFragment.onPause() called")
        // Stop projection timer to save resources when fragment is not visible
        stashViewModel.onStashTabPaused()
    }

    private fun saveScrollPosition() {
        _binding?.let { validBinding ->
            try {
                val scrollY = validBinding.root.scrollY
                requireContext().getSharedPreferences("stash_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putInt("stash_scroll_position", scrollY)
                    .apply()
            } catch (e: Exception) {
                Log.w(TAG, "Could not save scroll position: ${e.message}")
            }
        }
    }

    private fun restoreScrollPosition() {
        val binding = _binding ?: return
        try {
            val scrollY = requireContext().getSharedPreferences("stash_prefs", Context.MODE_PRIVATE)
                .getInt("stash_scroll_position", 0)
            if (scrollY > 0) {
                binding.root.post {
                    _binding?.root?.scrollTo(0, scrollY)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not restore scroll position: ${e.message}")
        }
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(bottom = insets.bottom)
            

            WindowInsetsCompat.CONSUMED
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            binding.root.requestApplyInsets()
        } else {
            @Suppress("DEPRECATION")
            binding.root.requestApplyInsets()
        }
    }

    private fun setupUI() {
        distributionAdapter = StashDistributionAdapter()
        binding.recyclerDistribution.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = distributionAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun setupListeners() {
        binding.btnGoogleSignIn.setOnClickListener {
            googleSignInLauncher.launch(googleSignInClient.signInIntent)
        }

        binding.btnLogout.setOnClickListener {
            lifecycleScope.launch {
                authManager.signOut()
                googleSignInClient.signOut().await()
                updateUI(null)
            }
        }

        binding.btnAddStash.setOnClickListener { showAddStashDialog() }
        binding.btnRemoveStash.setOnClickListener { showRemoveStashDialog() }
        binding.btnSetRatio.setOnClickListener { showSetRatioDialog() }

        binding.chipGroupDataScope.setOnCheckedStateChangeListener { group, checkedIds ->
            Log.d("STASH_UI", "Data scope chip changed")
            val scope = when (group.checkedChipId) {
                R.id.chipMyStash -> StashStatsCalculator.DataScope.MY_STASH
                R.id.chipTheirStash -> StashStatsCalculator.DataScope.THEIR_STASH
                R.id.chipOnlyMe -> StashStatsCalculator.DataScope.ONLY_ME
                else -> StashStatsCalculator.DataScope.MY_STASH
            }
            updateCostRatiosVisibility(scope)
            recalculateStats()
        }

        binding.chipGroupStatsType.setOnCheckedStateChangeListener { _, _ ->
            Log.d("STASH_UI", "Stats type chip changed")
            recalculateStats()
        }

        binding.chipGroupTimePeriod.setOnCheckedStateChangeListener { _, _ ->
            Log.d("STASH_UI", "Time period chip changed")
            recalculateStats()
        }

        binding.radioGroupAttribution.setOnCheckedChangeListener { _, checkedId ->
            val source = when (checkedId) {
                R.id.radioMyStashAttribution -> StashSource.MY_STASH
                R.id.radioTheirStashAttribution -> StashSource.THEIR_STASH
                R.id.radioEachToOwnAttribution -> StashSource.EACH_TO_OWN
                else -> StashSource.MY_STASH
            }
            stashViewModel.updateStashSource(source)
        }
    }

    private fun recalculateStats() {
        _binding?.let { validBinding ->
            saveScrollPosition()

            val statsType = when (validBinding.chipGroupStatsType.checkedChipId) {
                R.id.chipPast -> StashStatsCalculator.StatsType.PAST
                R.id.chipCurrent -> StashStatsCalculator.StatsType.CURRENT
                R.id.chipProjected -> StashStatsCalculator.StatsType.PROJECTED
                else -> StashStatsCalculator.StatsType.CURRENT
            }

            val timePeriod = when (validBinding.chipGroupTimePeriod.checkedChipId) {
                R.id.chipSesh -> StashTimePeriod.THIS_SESH
                R.id.chipHour -> StashTimePeriod.HOUR
                R.id.chip12h -> StashTimePeriod.TWELVE_H
                R.id.chipToday -> StashTimePeriod.TODAY
                R.id.chipWeek -> StashTimePeriod.WEEK
                R.id.chipMonth -> StashTimePeriod.MONTH
                R.id.chipYear -> StashTimePeriod.YEAR
                else -> StashTimePeriod.THIS_SESH
            }

            val dataScope = when (validBinding.chipGroupDataScope.checkedChipId) {
                R.id.chipMyStash -> StashStatsCalculator.DataScope.MY_STASH
                R.id.chipTheirStash -> StashStatsCalculator.DataScope.THEIR_STASH
                R.id.chipOnlyMe -> StashStatsCalculator.DataScope.ONLY_ME
                else -> StashStatsCalculator.DataScope.MY_STASH
            }

            Log.d("STASH_UI", "Recalculating stats: type=$statsType, period=$timePeriod, scope=$dataScope")

            if (timePeriod == StashTimePeriod.THIS_SESH) {
                if (statsType == StashStatsCalculator.StatsType.PAST) {
                    stashViewModel.setLastCompletedSessionId(sessionStatsViewModel.lastCompletedSessionId)
                    stashViewModel.setSessionStartTime(null)
                } else {
                    stashViewModel.setSessionStartTime(sessionStatsViewModel.sessionStartTime)
                    stashViewModel.setLastCompletedSessionId(null)
                }
            } else {
                stashViewModel.setSessionStartTime(null)
                stashViewModel.setLastCompletedSessionId(null)
            }

            stashViewModel.recalculateStats(statsType, timePeriod, dataScope)
            validBinding.root.postDelayed({ restoreScrollPosition() }, 100)
        } ?: run {
            Log.w("STASH_UI", "recalculateStats called but binding is null")
        }
    }

    private fun observeSessionState() {
        sessionStatsViewModel.isSessionActive.observe(viewLifecycleOwner) { isActive ->
            stashViewModel.setSessionStartTime(
                if (isActive) sessionStatsViewModel.sessionStartTime else null
            )
            if (!isActive) {
                sessionStatsViewModel.lastCompletedSessionId?.let {
                    stashViewModel.setLastCompletedSessionId(it)
                }
            }
        }
    }

    private fun observeViewModels() {
        stashViewModel.currentStash.observe(viewLifecycleOwner) { stash ->
            updateStashDisplay(stash ?: Stash())
        }

        stashViewModel.ratios.observe(viewLifecycleOwner) { ratios ->
            updateCostRatios(ratios ?: ConsumptionRatio())
        }

        stashViewModel.stashSource.observe(viewLifecycleOwner) { source ->
            binding.layoutTheirStashCounter.visibility = View.VISIBLE
            lifecycleScope.launch {
                calculateTheirStashConsumption()
            }
        }

        stashViewModel.stashStats.observe(viewLifecycleOwner) { stats ->
            Log.d("PROJ_TIMER", "StashFragment stats observer triggered - stats is ${if (stats == null) "NULL" else "NOT NULL"}")
            if (stats == null) {
                clearDistribution()
                return@observe
            }
            
            Log.d("PROJ_TIMER", "Updating UI with stats - Type: ${stats.statsType}, Grams: ${stats.totalGrams}, Scale: ${stats.projectionScale}")
            updateStatsFromCalculator(stats)

            lifecycleScope.launch {
                calculateTheirStashConsumption()
            }

            val distributions = stats.distributions ?: emptyList()
            if (distributions.isNotEmpty()) {
                val mappedDistributions = distributions.map { dist ->
                    StashDistribution(
                        smokerName = dist.smokerName,
                        smokerUid = dist.smokerName,
                        cones = dist.cones,
                        joints = dist.joints,
                        bowls = dist.bowls,
                        conesGiven = dist.cones,
                        jointsGiven = dist.joints,
                        bowlsGiven = dist.bowls,
                        totalGrams = dist.totalGrams,
                        totalCost = dist.totalCost,
                        totalValue = dist.totalCost,
                        percentage = dist.percentage
                    )
                }
                distributionAdapter.submitList(mappedDistributions)

                binding.textGivenToLabel.text = when (stats.dataScope) {
                    StashStatsCalculator.DataScope.MY_STASH -> "Given to:"
                    StashStatsCalculator.DataScope.THEIR_STASH -> "Their Stash Distribution:"
                    StashStatsCalculator.DataScope.ONLY_ME -> "My Consumption:"
                    else -> "Distribution:"
                }
                binding.textGivenToLabel.visibility = View.VISIBLE
                binding.recyclerDistribution.visibility = View.VISIBLE

                Log.d(TAG, "Showing ${distributions.size} distributions for scope: ${stats.dataScope}")
            } else {
                clearDistribution()
            }

            setupWindowInsets()
        }
    }

    private fun updateStatsFromCalculator(stats: StashStatsCalculator.StashStats?) {
        if (stats == null) return

        val stash = stashViewModel.currentStash.value ?: Stash()

        val coneCount = stats.counts[ActivityType.CONE] ?: 0
        val jointCount = stats.counts[ActivityType.JOINT] ?: 0
        val bowlCount = stats.counts[ActivityType.BOWL] ?: 0

        val coneGrams = stats.grams[ActivityType.CONE] ?: 0.0
        val jointGrams = stats.grams[ActivityType.JOINT] ?: 0.0
        val bowlGrams = stats.grams[ActivityType.BOWL] ?: 0.0

        val conesCost = coneGrams * (if (stats.totalGrams > 0) stats.totalCost / stats.totalGrams else stash.pricePerGram)
        val jointsCost = jointGrams * (if (stats.totalGrams > 0) stats.totalCost / stats.totalGrams else stash.pricePerGram)
        val bowlsCost = bowlGrams * (if (stats.totalGrams > 0) stats.totalCost / stats.totalGrams else stash.pricePerGram)

        binding.textStatsCones.text = "$coneCount (${currencyFormat.format(conesCost)})"
        binding.textStatsJoints.text = "$jointCount (${currencyFormat.format(jointsCost)})"
        binding.textStatsBowls.text = "$bowlCount (${currencyFormat.format(bowlsCost)})"
        binding.textStatsTotal.text = currencyFormat.format(stats.totalCost)

        binding.rowCones.visibility = if (coneCount > 0) View.VISIBLE else View.GONE
        binding.rowJoints.visibility = if (jointCount > 0) View.VISIBLE else View.GONE
        binding.rowBowls.visibility = if (bowlCount > 0) View.VISIBLE else View.GONE

        setupWindowInsets()
    }

    private fun clearDistribution() {
        distributionAdapter.submitList(emptyList())
        binding.textGivenToLabel.visibility = View.GONE
        binding.recyclerDistribution.visibility = View.GONE
        setupWindowInsets()
    }

    private fun updateUI(user: FirebaseUser?) {
        if (user != null) {
            currentUserId = user.uid
            binding.layoutSignInPrompt.visibility = View.GONE
            binding.layoutSignedIn.visibility = View.VISIBLE
            binding.textUserEmail.text = user.email
            stashViewModel.setCurrentUserId(user.uid)
            stashViewModel.loadCurrentStash()
            stashViewModel.loadRatios()
            recalculateStats()
        } else {
            currentUserId = null
            binding.layoutSignInPrompt.visibility = View.VISIBLE
            binding.layoutSignedIn.visibility = View.GONE
        }
    }

    private fun updateStashDisplay(stash: Stash) {
        binding.textStashGrams.text = "${decimalFormat.format(stash.currentGrams)} grams"
        binding.textStashValue.text = currencyFormat.format(stash.currentGrams * stash.pricePerGram)
    }

    private fun updateCostRatios(ratios: ConsumptionRatio) {
        val stash = stashViewModel.currentStash.value ?: Stash()
        val pricePerGram = stash.pricePerGram

        binding.textCostPerCone.text = "Cone: ${currencyFormat.format(ratios.coneGrams * pricePerGram)}"
        binding.textCostPerJoint.text = "Joint: ${currencyFormat.format(ratios.jointGrams * pricePerGram)}"
        binding.textCostPerBowl.text = "Bowl: ${currencyFormat.format(ratios.bowlGrams * pricePerGram)}"

        binding.textRatioCone.text = "(${decimalFormat.format(ratios.coneGrams)}g)"
        binding.textRatioJoint.text = "(${decimalFormat.format(ratios.jointGrams)}g)"
        binding.textRatioBowl.text = "(${decimalFormat.format(ratios.bowlGrams)}g)"
    }

    private fun updateCostRatiosVisibility(scope: StashStatsCalculator.DataScope) {
        binding.layoutCostInfo.visibility = View.VISIBLE
    }

    private suspend fun calculateTheirStashConsumption() {
        val currentSessionStart = sessionStatsViewModel.sessionStartTime ?: 0L

        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(requireContext())
            val activities = if (currentSessionStart > 0) {
                db.activityLogDao().getLogsBetweenTimestamps(currentSessionStart, System.currentTimeMillis())
            } else {
                db.activityLogDao().getRecentActivities(50)
            }

            val theirStashActivities = activities.filter {
                it.payerStashOwnerId == "their_stash" ||
                        (it.payerStashOwnerId != null && it.payerStashOwnerId!!.startsWith("other_"))
            }

            theirStashTotalGrams = theirStashActivities.sumOf { it.gramsAtLog }
            theirStashTotalCost = theirStashActivities.sumOf { it.cost }

            withContext(Dispatchers.Main) {
                binding.textTheirStashGrams.text = "${decimalFormat.format(theirStashTotalGrams)}g"
                binding.textTheirStashValue.text = currencyFormat.format(theirStashTotalCost)
            }
        }
    }

    // Public method for showing dialog from outside the fragment
    fun showAddStashDialogPublic() {
        Log.d("WELCOME_DEBUG", "💊 StashFragment.showAddStashDialogPublic() called")
        showAddStashDialog()
    }
    
    // Public method for showing ratio dialog from outside the fragment
    fun showSetRatioDialogPublic() {
        Log.d("WELCOME_DEBUG", "⚖️ StashFragment.showSetRatioDialogPublic() called")
        showSetRatioDialog()
    }
    
    private fun showAddStashDialog() {
        Log.d("WELCOME_DEBUG", "💊 StashFragment.showAddStashDialog() - Creating dialog")
        val dialog = Dialog(requireContext(), android.R.style.Theme_Translucent_NoTitleBar_Fullscreen)

        // Root container - full screen
        val rootContainer = android.widget.FrameLayout(requireContext()).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }

        // Create a vertical LinearLayout to hold spacer and card
        val contentWrapper = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // INVISIBLE SPACER - Takes up top space
        val topSpacer = android.view.View(requireContext()).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        contentWrapper.addView(topSpacer)

        // Main card at bottom - RAISED BY 180dp
        val mainCard = androidx.cardview.widget.CardView(requireContext()).apply {
            radius = 20.dpToPx().toFloat()
            cardElevation = 12.dpToPx().toFloat()
            setCardBackgroundColor(android.graphics.Color.parseColor("#E64A4A4A"))

            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(16.dpToPx(), 0, 16.dpToPx(), 180.dpToPx())
            }
        }

        // Store card for animation reference
        rootContainer.tag = mainCard

        val contentLayout = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(24.dpToPx(), 24.dpToPx(), 24.dpToPx(), 24.dpToPx())
        }

        // Title
        val titleText = android.widget.TextView(requireContext()).apply {
            text = "ADD TO STASH"
            textSize = 22f
            setTextColor(android.graphics.Color.parseColor("#98FB98"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
            letterSpacing = 0.15f
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 24.dpToPx()
            }
        }
        contentLayout.addView(titleText)

        // Tab container
        val tabContainer = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                48.dpToPx()
            ).apply {
                bottomMargin = 24.dpToPx()
            }
        }

        // My Stash Tab
        val myStashTab = androidx.cardview.widget.CardView(requireContext()).apply {
            radius = 12.dpToPx().toFloat()
            cardElevation = 0f
            setCardBackgroundColor(android.graphics.Color.parseColor("#98FB98"))
            layoutParams = android.widget.LinearLayout.LayoutParams(
                0,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                1f
            ).apply {
                marginEnd = 8.dpToPx()
            }
            isClickable = true
        }

        val myStashTabText = android.widget.TextView(requireContext()).apply {
            text = "My Stash"
            textSize = 14f
            setTextColor(android.graphics.Color.parseColor("#424242"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        myStashTab.addView(myStashTabText)

        // Their Stash Tab
        val theirStashTab = androidx.cardview.widget.CardView(requireContext()).apply {
            radius = 12.dpToPx().toFloat()
            cardElevation = 0f
            setCardBackgroundColor(android.graphics.Color.parseColor("#424242"))
            layoutParams = android.widget.LinearLayout.LayoutParams(
                0,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                1f
            ).apply {
                marginStart = 8.dpToPx()
            }
            isClickable = true
        }

        val theirStashTabText = android.widget.TextView(requireContext()).apply {
            text = "Their Stash"
            textSize = 14f
            setTextColor(android.graphics.Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        theirStashTab.addView(theirStashTabText)

        tabContainer.addView(myStashTab)
        tabContainer.addView(theirStashTab)
        contentLayout.addView(tabContainer)

        // Content containers for each tab
        val myStashContent = createAddStashContent(true)
        val theirStashContent = createAddStashContent(false)

        contentLayout.addView(myStashContent)
        contentLayout.addView(theirStashContent)

        // Initially show My Stash
        theirStashContent.visibility = android.view.View.GONE

        // Restore last used values
        val prefs = requireContext().getSharedPreferences("stash_prefs", Context.MODE_PRIVATE)
        val lastSelectedTab = prefs.getInt("last_add_tab", 0)

        // Setup tab selection
        fun selectTab(index: Int) {
            if (index == 0) {
                myStashTab.setCardBackgroundColor(android.graphics.Color.parseColor("#98FB98"))
                myStashTabText.setTextColor(android.graphics.Color.parseColor("#424242"))
                theirStashTab.setCardBackgroundColor(android.graphics.Color.parseColor("#424242"))
                theirStashTabText.setTextColor(android.graphics.Color.WHITE)
                myStashContent.visibility = android.view.View.VISIBLE
                theirStashContent.visibility = android.view.View.GONE
            } else {
                myStashTab.setCardBackgroundColor(android.graphics.Color.parseColor("#424242"))
                myStashTabText.setTextColor(android.graphics.Color.WHITE)
                theirStashTab.setCardBackgroundColor(android.graphics.Color.parseColor("#98FB98"))
                theirStashTabText.setTextColor(android.graphics.Color.parseColor("#424242"))
                myStashContent.visibility = android.view.View.GONE
                theirStashContent.visibility = android.view.View.VISIBLE
            }
            prefs.edit().putInt("last_add_tab", index).apply()
        }

        selectTab(lastSelectedTab)

        myStashTab.setOnClickListener { selectTab(0) }
        theirStashTab.setOnClickListener { selectTab(1) }

        // Neon separator with gradient - FULL OPACITY IN CENTER
        val separator = android.view.View(requireContext()).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                2.dpToPx()
            ).apply {
                bottomMargin = 16.dpToPx()
            }
            background = android.graphics.drawable.GradientDrawable().apply {
                colors = intArrayOf(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.parseColor("#98FB98"), // Full opacity in center
                    android.graphics.Color.parseColor("#98FB98"), // Full opacity in center
                    android.graphics.Color.TRANSPARENT
                )
                orientation = android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT
            }
        }
        contentLayout.addView(separator)

        // Button container
        val buttonContainer = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                48.dpToPx()
            )
        }

        // Cancel button (secondary)
        val cancelButton = createThemedStashButton("Cancel", false) {
            animateCardSelection(dialog, 1000L) {}
        }
        cancelButton.layoutParams = android.widget.LinearLayout.LayoutParams(
            0,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            1f
        ).apply {
            marginEnd = 8.dpToPx()
        }

        // Add button (primary with throbbing)
        val addButton = createThemedStashButton("Add", true) {
            val currentTab = if (myStashContent.visibility == android.view.View.VISIBLE) 0 else 1
            val content = if (currentTab == 0) myStashContent else theirStashContent

            val checkbox = content.findViewWithTag<android.widget.CheckBox>("checkbox")
            val editAmount = content.findViewWithTag<android.widget.EditText>("amount")
            val editPrice = content.findViewWithTag<android.widget.EditText>("price")
            val spinner = content.findViewWithTag<android.widget.Spinner>("spinner")

            val currentStash = stashViewModel.currentStash.value ?: Stash()

            animateCardSelection(dialog, 1000L) {
                handleAddToStash(
                    checkbox.isChecked,
                    editAmount.text.toString(),
                    editPrice.text.toString(),
                    spinner.selectedItemPosition,
                    currentStash.pricePerGram,
                    isMyStash = (currentTab == 0)
                )

                // Save values
                prefs.edit().apply {
                    if (currentTab == 0) {
                        putFloat("last_add_amount_my", editAmount.text.toString().toFloatOrNull() ?: 0f)
                        putFloat("last_add_price_my", editPrice.text.toString().toFloatOrNull() ?: 0f)
                        putInt("last_add_unit_my", spinner.selectedItemPosition)
                    } else {
                        putFloat("last_add_amount_their", editAmount.text.toString().toFloatOrNull() ?: 0f)
                        putFloat("last_add_price_their", editPrice.text.toString().toFloatOrNull() ?: 0f)
                        putInt("last_add_unit_their", spinner.selectedItemPosition)
                    }
                    apply()
                }
            }
        }
        addButton.layoutParams = android.widget.LinearLayout.LayoutParams(
            0,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            1f
        ).apply {
            marginStart = 8.dpToPx()
        }

        // Add throbbing to Add button
        addThrobbingAnimation(addButton as androidx.cardview.widget.CardView)

        buttonContainer.addView(cancelButton)
        buttonContainer.addView(addButton)
        contentLayout.addView(buttonContainer)

        mainCard.addView(contentLayout)
        contentWrapper.addView(mainCard)
        rootContainer.addView(contentWrapper)

        // Add click to dismiss on background
        rootContainer.setOnClickListener {
            if (it == rootContainer) {
                animateCardSelection(dialog, 1000L) {}
            }
        }

        dialog.setContentView(rootContainer)

        dialog.window?.apply {
            setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.parseColor("#80000000")))
            setFlags(
                android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            )
        }

        // Set initial alpha to 0 for fade-in
        rootContainer.alpha = 0f

        dialog.show()

        // Apply fade-in animation
        performManualFadeIn(rootContainer, 2000L)
    }

    private fun createAddStashContent(isMyStash: Boolean): android.widget.LinearLayout {
        Log.d(TAG, "🎯 createAddStashContent: Creating content for isMyStash=$isMyStash")

        val contentLayout = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
        }

        // Ratio checkbox
        val checkboxCard = androidx.cardview.widget.CardView(requireContext()).apply {
            radius = 12.dpToPx().toFloat()
            cardElevation = 0f
            setCardBackgroundColor(android.graphics.Color.parseColor("#424242"))
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 16.dpToPx()
            }
        }

        val checkboxLayout = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setPadding(16.dpToPx(), 12.dpToPx(), 16.dpToPx(), 12.dpToPx())

            // Make the entire layout clickable
            isClickable = true
            isFocusable = true
        }

        val checkbox = android.widget.CheckBox(requireContext()).apply {
            tag = "checkbox"
            isChecked = true
            buttonTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#98FB98"))

            // Prevent checkbox from handling its own clicks
            isClickable = false
            isFocusable = false
        }

        val checkboxText = android.widget.TextView(requireContext()).apply {
            text = "Use ratio for auto-calculation"
            textSize = 14f
            setTextColor(android.graphics.Color.parseColor("#98FB98"))
            layoutParams = android.widget.LinearLayout.LayoutParams(
                0,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginStart = 8.dpToPx()
            }
        }

        // Handle clicks on the entire layout to prevent dialog closing
        checkboxLayout.setOnClickListener {
            Log.d(TAG, "🎯 ADD: Ratio checkbox clicked, current state: ${checkbox.isChecked}")
            checkbox.isChecked = !checkbox.isChecked
            Log.d(TAG, "🎯 ADD: Ratio checkbox new state: ${checkbox.isChecked}")
            // Stop event propagation
        }

        checkboxLayout.addView(checkbox)
        checkboxLayout.addView(checkboxText)
        checkboxCard.addView(checkboxLayout)
        contentLayout.addView(checkboxCard)

        // Amount input with spinner
        val amountLayout = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 16.dpToPx()
            }
        }

        val amountCard = androidx.cardview.widget.CardView(requireContext()).apply {
            radius = 12.dpToPx().toFloat()
            cardElevation = 0f
            setCardBackgroundColor(android.graphics.Color.parseColor("#424242"))
            layoutParams = android.widget.LinearLayout.LayoutParams(
                0,
                48.dpToPx(),
                1f
            ).apply {
                marginEnd = 8.dpToPx()
            }
        }

        val editAmount = android.widget.EditText(requireContext()).apply {
            tag = "amount"
            hint = "Amount"
            setHintTextColor(android.graphics.Color.parseColor("#B0B0B0"))
            setTextColor(android.graphics.Color.WHITE)
            textSize = 16f
            background = null
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setPadding(16.dpToPx(), 0, 16.dpToPx(), 0)
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        amountCard.addView(editAmount)

        val spinnerCard = androidx.cardview.widget.CardView(requireContext()).apply {
            radius = 12.dpToPx().toFloat()
            cardElevation = 0f
            setCardBackgroundColor(android.graphics.Color.parseColor("#424242"))
            layoutParams = android.widget.LinearLayout.LayoutParams(
                100.dpToPx(),
                48.dpToPx()
            )
        }

        val spinner = android.widget.Spinner(requireContext()).apply {
            tag = "spinner"
            val units = arrayOf("grams", "ounces")
            adapter = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, units)
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        spinnerCard.addView(spinner)
        amountLayout.addView(amountCard)
        amountLayout.addView(spinnerCard)
        contentLayout.addView(amountLayout)

        // Price input
        val priceCard = androidx.cardview.widget.CardView(requireContext()).apply {
            radius = 12.dpToPx().toFloat()
            cardElevation = 0f
            setCardBackgroundColor(android.graphics.Color.parseColor("#424242"))
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                48.dpToPx()
            )
        }

        val editPrice = android.widget.EditText(requireContext()).apply {
            tag = "price"
            hint = "Total Price ($)"
            setHintTextColor(android.graphics.Color.parseColor("#B0B0B0"))
            setTextColor(android.graphics.Color.WHITE)
            textSize = 16f
            background = null
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setPadding(16.dpToPx(), 0, 16.dpToPx(), 0)
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        priceCard.addView(editPrice)
        contentLayout.addView(priceCard)

        // Setup auto-calculation
        val currentStash = stashViewModel.currentStash.value ?: Stash()
        val hasRatio = currentStash.pricePerGram > 0

        if (!hasRatio) {
            checkbox.isEnabled = false
            checkbox.isChecked = false
        }

        // Restore saved values
        val prefs = requireContext().getSharedPreferences("stash_prefs", Context.MODE_PRIVATE)
        if (isMyStash) {
            val lastAmount = prefs.getFloat("last_add_amount_my", 0f)
            val lastPrice = prefs.getFloat("last_add_price_my", 0f)
            val lastUnit = prefs.getInt("last_add_unit_my", 0)
            if (lastAmount > 0) editAmount.setText(decimalFormat.format(lastAmount))
            if (lastPrice > 0) editPrice.setText(decimalFormat.format(lastPrice))
            spinner.setSelection(lastUnit)
        } else {
            val lastAmount = prefs.getFloat("last_add_amount_their", 0f)
            val lastPrice = prefs.getFloat("last_add_price_their", 0f)
            val lastUnit = prefs.getInt("last_add_unit_their", 0)
            if (lastAmount > 0) editAmount.setText(decimalFormat.format(lastAmount))
            if (lastPrice > 0) editPrice.setText(decimalFormat.format(lastPrice))
            spinner.setSelection(lastUnit)
        }

        return contentLayout
    }

    private fun handleAddToStash(
        useRatio: Boolean,
        amountStr: String,
        priceStr: String,
        unitPosition: Int,
        pricePerGram: Double,
        isMyStash: Boolean
    ) {
        try {
            var grams: Double? = null
            var totalPrice: Double? = null

            if (useRatio && pricePerGram > 0) {
                // Checkbox checked - auto-calculate missing value
                when {
                    amountStr.isNotEmpty() && priceStr.isNotEmpty() -> {
                        // Both provided - use as is
                        grams = amountStr.toDouble()
                        if (unitPosition == 1) grams *= 28.3495
                        totalPrice = priceStr.toDouble()
                    }
                    amountStr.isNotEmpty() -> {
                        // Only amount provided - calculate price
                        grams = amountStr.toDouble()
                        if (unitPosition == 1) grams *= 28.3495
                        totalPrice = grams * pricePerGram
                    }
                    priceStr.isNotEmpty() -> {
                        // Only price provided - calculate grams
                        totalPrice = priceStr.toDouble()
                        grams = totalPrice / pricePerGram
                    }
                    else -> {
                        Toast.makeText(requireContext(), "Please enter at least one value", Toast.LENGTH_SHORT).show()
                        return
                    }
                }
            } else {
                // Checkbox unchecked or no ratio - single value entry
                when {
                    amountStr.isNotEmpty() -> {
                        grams = amountStr.toDouble()
                        if (unitPosition == 1) grams *= 28.3495
                        totalPrice = if (priceStr.isNotEmpty()) {
                            priceStr.toDouble()
                        } else {
                            grams * pricePerGram
                        }
                    }
                    priceStr.isNotEmpty() -> {
                        totalPrice = priceStr.toDouble()
                        grams = if (pricePerGram > 0) {
                            totalPrice / pricePerGram
                        } else {
                            Toast.makeText(requireContext(), "Cannot calculate grams without price per gram", Toast.LENGTH_SHORT).show()
                            return
                        }
                    }
                    else -> {
                        Toast.makeText(requireContext(), "Please enter at least one value", Toast.LENGTH_SHORT).show()
                        return
                    }
                }
            }

            val finalPricePerGram = if (grams > 0) totalPrice / grams else pricePerGram

            lifecycleScope.launch {
                if (isMyStash) {
                    stashViewModel.addToStash(grams, finalPricePerGram)
                    Toast.makeText(requireContext(), "Added ${decimalFormat.format(grams)}g to My Stash", Toast.LENGTH_SHORT).show()
                } else {
                    addToTheirStash(grams, totalPrice)
                    Toast.makeText(requireContext(), "Added ${decimalFormat.format(grams)}g to Their Stash", Toast.LENGTH_SHORT).show()
                }
                recalculateStats()
            }
        } catch (e: NumberFormatException) {
            Toast.makeText(requireContext(), "Invalid number format", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addToTheirStash(grams: Double, cost: Double) {
        lifecycleScope.launch(Dispatchers.IO) {
            theirStashTotalGrams += grams
            theirStashTotalCost += cost

            withContext(Dispatchers.Main) {
                binding.textTheirStashGrams.text = "${decimalFormat.format(theirStashTotalGrams)}g"
                binding.textTheirStashValue.text = currencyFormat.format(theirStashTotalCost)
                recalculateStats()
            }
        }
    }

    private fun showSetRatioDialog() {
        Log.d(TAG, "🎯 showSetRatioDialog: Starting")
        val dialog = Dialog(requireContext(), android.R.style.Theme_Translucent_NoTitleBar_Fullscreen)

        // Root container - full screen
        val rootContainer = android.widget.FrameLayout(requireContext()).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }

        // Create a vertical LinearLayout to hold spacer and card
        val contentWrapper = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // TOP SPACER - Minimum 3cm (approximately 120dp) from top
        val topSpacer = android.view.View(requireContext()).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                120.dpToPx() // Fixed 3cm spacing from top
            )
        }
        contentWrapper.addView(topSpacer)

        // Container for the card with weight to center it in remaining space
        val cardContainer = android.widget.FrameLayout(requireContext()).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f // Take remaining space
            )
        }

        // Main card - constrained height
        val mainCard = androidx.cardview.widget.CardView(requireContext()).apply {
            radius = 20.dpToPx().toFloat()
            cardElevation = 12.dpToPx().toFloat()
            setCardBackgroundColor(android.graphics.Color.parseColor("#E64A4A4A"))

            // Get screen height to calculate max height
            val displayMetrics = requireContext().resources.displayMetrics
            val screenHeight = displayMetrics.heightPixels
            val maxCardHeight = screenHeight - 300.dpToPx() // Leave 120dp top + 180dp bottom

            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(16.dpToPx(), 0, 16.dpToPx(), 0)
                gravity = android.view.Gravity.CENTER_VERTICAL
                // Limit maximum height
                if (maxCardHeight > 0) {
                    height = maxCardHeight
                }
            }
        }

        Log.d(TAG, "🎯 showSetRatioDialog: Card created")

        // Store card for animation reference
        rootContainer.tag = mainCard

        // Main content container (will hold scrollview and buttons)
        val mainContentContainer = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Scrollable content
        val scrollView = android.widget.ScrollView(requireContext()).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f // Take available space but leave room for buttons
            )
            isVerticalScrollBarEnabled = true
            scrollBarStyle = android.view.View.SCROLLBARS_INSIDE_OVERLAY
        }

        val contentLayout = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(24.dpToPx(), 24.dpToPx(), 24.dpToPx(), 16.dpToPx())
        }

        // Title
        val titleText = android.widget.TextView(requireContext()).apply {
            text = "SET CONSUMPTION RATIOS"
            textSize = 22f
            setTextColor(android.graphics.Color.parseColor("#98FB98"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
            letterSpacing = 0.15f
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 16.dpToPx()
            }
        }
        contentLayout.addView(titleText)

        // Helper text
        val helperText = android.widget.TextView(requireContext()).apply {
            text = "Set consumption ratios in grams"
            textSize = 14f
            setTextColor(android.graphics.Color.parseColor("#707070"))
            gravity = android.view.Gravity.CENTER
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 24.dpToPx()
            }
        }
        contentLayout.addView(helperText)

        // Bowl Input Section
        val bowlLabel = android.widget.TextView(requireContext()).apply {
            text = "Bowl (Grams per Bowl)"
            textSize = 14f
            setTextColor(android.graphics.Color.parseColor("#98FB98"))
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 8.dpToPx()
            }
        }
        contentLayout.addView(bowlLabel)

        val bowlCard = androidx.cardview.widget.CardView(requireContext()).apply {
            radius = 12.dpToPx().toFloat()
            cardElevation = 0f
            setCardBackgroundColor(android.graphics.Color.parseColor("#424242"))
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                48.dpToPx()
            ).apply {
                bottomMargin = 16.dpToPx()
            }
        }

        val editBowlGrams = android.widget.EditText(requireContext()).apply {
            hint = "e.g., 0.2"
            setHintTextColor(android.graphics.Color.parseColor("#707070"))
            setTextColor(android.graphics.Color.WHITE)
            textSize = 16f
            background = null
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setPadding(16.dpToPx(), 0, 16.dpToPx(), 0)
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        bowlCard.addView(editBowlGrams)
        contentLayout.addView(bowlCard)

        // Cone Input Section
        val coneLabel = android.widget.TextView(requireContext()).apply {
            text = "Cone (Grams per Cone)"
            textSize = 14f
            setTextColor(android.graphics.Color.parseColor("#98FB98"))
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 8.dpToPx()
            }
        }
        contentLayout.addView(coneLabel)

        val coneCard = androidx.cardview.widget.CardView(requireContext()).apply {
            radius = 12.dpToPx().toFloat()
            cardElevation = 0f
            setCardBackgroundColor(android.graphics.Color.parseColor("#424242"))
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                48.dpToPx()
            ).apply {
                bottomMargin = 8.dpToPx()
            }
        }

        val editConeGrams = android.widget.EditText(requireContext()).apply {
            hint = "(empty) Leave empty for auto-calculation"
            setHintTextColor(android.graphics.Color.parseColor("#707070"))
            setTextColor(android.graphics.Color.WHITE)
            textSize = 16f
            background = null
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setPadding(16.dpToPx(), 0, 16.dpToPx(), 0)
            setTypeface(typeface, android.graphics.Typeface.ITALIC)
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        coneCard.addView(editConeGrams)
        contentLayout.addView(coneCard)

        val textConeInfo = android.widget.TextView(requireContext()).apply {
            text = "Will be calculated based on your bowl activities"
            textSize = 12f
            setTextColor(android.graphics.Color.parseColor("#707070"))
            setTypeface(typeface, android.graphics.Typeface.ITALIC)
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 16.dpToPx()
            }
        }
        contentLayout.addView(textConeInfo)

        // Joint Input Section
        val jointLabel = android.widget.TextView(requireContext()).apply {
            text = "Joint (Grams per Joint)"
            textSize = 14f
            setTextColor(android.graphics.Color.parseColor("#98FB98"))
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 8.dpToPx()
            }
        }
        contentLayout.addView(jointLabel)

        val jointCard = androidx.cardview.widget.CardView(requireContext()).apply {
            radius = 12.dpToPx().toFloat()
            cardElevation = 0f
            setCardBackgroundColor(android.graphics.Color.parseColor("#424242"))
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                48.dpToPx()
            ).apply {
                bottomMargin = 24.dpToPx()
            }
        }

        val editJointGrams = android.widget.EditText(requireContext()).apply {
            hint = "e.g., 0.5"
            setHintTextColor(android.graphics.Color.parseColor("#707070"))
            setTextColor(android.graphics.Color.WHITE)
            textSize = 16f
            background = null
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setPadding(16.dpToPx(), 0, 16.dpToPx(), 0)
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        jointCard.addView(editJointGrams)
        contentLayout.addView(jointCard)

        // Neon separator with gradient - FULL OPACITY IN CENTER
        val separator = android.view.View(requireContext()).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                2.dpToPx()
            ).apply {
                bottomMargin = 16.dpToPx()
            }
            background = android.graphics.drawable.GradientDrawable().apply {
                colors = intArrayOf(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.parseColor("#98FB98"), // Full opacity in center
                    android.graphics.Color.parseColor("#98FB98"), // Full opacity in center
                    android.graphics.Color.TRANSPARENT
                )
                orientation = android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT
            }
        }
        contentLayout.addView(separator)

        // Stash Deduction Settings Section
        val deductionTitle = android.widget.TextView(requireContext()).apply {
            text = "STASH DEDUCTION SETTINGS"
            textSize = 16f
            setTextColor(android.graphics.Color.parseColor("#98FB98"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 8.dpToPx()
            }
        }
        contentLayout.addView(deductionTitle)

        val deductionHelperText = android.widget.TextView(requireContext()).apply {
            text = "Choose which activities deduct from your stash:"
            textSize = 12f
            setTextColor(android.graphics.Color.parseColor("#707070"))
            setTypeface(typeface, android.graphics.Typeface.ITALIC)
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 12.dpToPx()
            }
        }
        contentLayout.addView(deductionHelperText)

        // Checkboxes
        val checkboxDeductCones = createStashCheckbox("Deduct cones from stash", true)
        val checkboxDeductJoints = createStashCheckbox("Deduct joints from stash", true)
        val checkboxDeductBowls = createStashCheckbox("Deduct bowls from stash", false)

        contentLayout.addView(checkboxDeductCones)
        contentLayout.addView(checkboxDeductJoints)
        contentLayout.addView(checkboxDeductBowls)

        // Load current ratios
        val currentRatios = stashViewModel.ratios.value ?: ConsumptionRatio()
        editJointGrams.setText(decimalFormat.format(currentRatios.jointGrams))
        editBowlGrams.setText(decimalFormat.format(currentRatios.bowlGrams))

        // Set checkbox states
        (checkboxDeductCones as androidx.cardview.widget.CardView).findViewWithTag<android.widget.CheckBox>("checkbox").isChecked = currentRatios.deductConesFromStash
        (checkboxDeductJoints as androidx.cardview.widget.CardView).findViewWithTag<android.widget.CheckBox>("checkbox").isChecked = currentRatios.deductJointsFromStash
        (checkboxDeductBowls as androidx.cardview.widget.CardView).findViewWithTag<android.widget.CheckBox>("checkbox").isChecked = currentRatios.deductBowlsFromStash

        // Get current session cone count
        val sessionStats = sessionStatsViewModel.groupStats.value
        val currentConeCount = sessionStats?.totalCones ?: 0

        // Set initial cone value or hint
        if (currentRatios.userDefinedConeGrams != null) {
            editConeGrams.setText(decimalFormat.format(currentRatios.coneGrams))
            editConeGrams.setTypeface(editConeGrams.typeface, android.graphics.Typeface.NORMAL)
            textConeInfo.text = "User-defined ratio"
        }

        scrollView.addView(contentLayout)
        mainContentContainer.addView(scrollView)

        // Button container at bottom of card (INSIDE the card)
        val buttonContainer = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                48.dpToPx()
            ).apply {
                setMargins(24.dpToPx(), 0, 24.dpToPx(), 24.dpToPx())
            }
        }

        // Clear Cone Ratio button (secondary, no throbbing)
        val clearConeButton = createThemedStashButton("Clear Cone Ratio", false) {
            Log.d(TAG, "🎯 Clear Cone Ratio clicked")
            animateCardSelection(dialog, 1000L) {
                val jointGrams = editJointGrams.text.toString().toDoubleOrNull() ?: 0.5
                val bowlGrams = editBowlGrams.text.toString().toDoubleOrNull() ?: 0.2
                val deductCones = (checkboxDeductCones as androidx.cardview.widget.CardView).findViewWithTag<android.widget.CheckBox>("checkbox").isChecked
                val deductJoints = (checkboxDeductJoints as androidx.cardview.widget.CardView).findViewWithTag<android.widget.CheckBox>("checkbox").isChecked
                val deductBowls = (checkboxDeductBowls as androidx.cardview.widget.CardView).findViewWithTag<android.widget.CheckBox>("checkbox").isChecked

                stashViewModel.updateRatios(
                    null,
                    jointGrams,
                    bowlGrams,
                    deductCones,
                    deductJoints,
                    deductBowls
                )

                if (currentConeCount > 0 && bowlGrams > 0) {
                    val autoCalc = bowlGrams / currentConeCount
                    Toast.makeText(
                        requireContext(),
                        "Using auto-calculated cone ratio: ${decimalFormat.format(autoCalc)}g",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Cone ratio cleared - will auto-calculate when data available",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
        clearConeButton.layoutParams = android.widget.LinearLayout.LayoutParams(
            0,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            1f
        ).apply {
            marginEnd = 4.dpToPx()
        }

        // Cancel button (secondary)
        val cancelButton = createThemedStashButton("Cancel", false) {
            Log.d(TAG, "🎯 Cancel clicked")
            animateCardSelection(dialog, 1000L) {}
        }
        cancelButton.layoutParams = android.widget.LinearLayout.LayoutParams(
            0,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            1f
        ).apply {
            marginStart = 4.dpToPx()
            marginEnd = 4.dpToPx()
        }

        // Save button (primary with throbbing)
        val saveButton = createThemedStashButton("Save", true) {
            Log.d(TAG, "🎯 Save clicked")
            animateCardSelection(dialog, 1000L) {
                try {
                    val coneText = editConeGrams.text.toString()
                    val coneGrams = coneText.toDoubleOrNull()
                    val jointGrams = editJointGrams.text.toString().toDoubleOrNull() ?: 0.5
                    val bowlGrams = editBowlGrams.text.toString().toDoubleOrNull() ?: 0.2

                    val deductCones = (checkboxDeductCones as androidx.cardview.widget.CardView).findViewWithTag<android.widget.CheckBox>("checkbox").isChecked
                    val deductJoints = (checkboxDeductJoints as androidx.cardview.widget.CardView).findViewWithTag<android.widget.CheckBox>("checkbox").isChecked
                    val deductBowls = (checkboxDeductBowls as androidx.cardview.widget.CardView).findViewWithTag<android.widget.CheckBox>("checkbox").isChecked

                    stashViewModel.updateRatios(
                        coneGrams,
                        jointGrams,
                        bowlGrams,
                        deductCones,
                        deductJoints,
                        deductBowls
                    )

                    val deductionSettings = mutableListOf<String>()
                    if (!deductCones) deductionSettings.add("Cones OFF")
                    if (!deductJoints) deductionSettings.add("Joints OFF")
                    if (deductBowls) deductionSettings.add("Bowls ON")

                    val deductionMessage = if (deductionSettings.isNotEmpty()) {
                        " | Deduction: ${deductionSettings.joinToString(", ")}"
                    } else {
                        ""
                    }

                    Toast.makeText(requireContext(), "Ratios updated$deductionMessage", Toast.LENGTH_LONG).show()
                } catch (e: NumberFormatException) {
                    Toast.makeText(requireContext(), "Invalid number format", Toast.LENGTH_SHORT).show()
                }
            }
        }
        saveButton.layoutParams = android.widget.LinearLayout.LayoutParams(
            0,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            1f
        ).apply {
            marginStart = 4.dpToPx()
        }

        // Add throbbing to Save button
        addThrobbingAnimation(saveButton as androidx.cardview.widget.CardView)

        buttonContainer.addView(clearConeButton)
        buttonContainer.addView(cancelButton)
        buttonContainer.addView(saveButton)

        // Add button container to main content container (inside card)
        mainContentContainer.addView(buttonContainer)

        // Add the main content container to the card
        mainCard.addView(mainContentContainer)

        cardContainer.addView(mainCard)
        contentWrapper.addView(cardContainer)

        // Bottom spacer to push card away from bottom
        val bottomSpacer = android.view.View(requireContext()).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                180.dpToPx() // Same bottom spacing as other dialogs
            )
        }
        contentWrapper.addView(bottomSpacer)

        rootContainer.addView(contentWrapper)

        // Add click to dismiss on background
        rootContainer.setOnClickListener {
            if (it == rootContainer) {
                Log.d(TAG, "🎯 Background clicked - closing dialog")
                animateCardSelection(dialog, 1000L) {}
            }
        }

        dialog.setContentView(rootContainer)

        dialog.window?.apply {
            setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.parseColor("#80000000")))
            setFlags(
                android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            )
        }

        // Set initial alpha to 0 for fade-in
        rootContainer.alpha = 0f

        dialog.show()
        Log.d(TAG, "🎯 showSetRatioDialog: Dialog shown")

        // Apply fade-in animation
        performManualFadeIn(rootContainer, 2000L)
    }

    private fun createStashCheckbox(text: String, defaultChecked: Boolean): android.view.View {
        Log.d(TAG, "🎯 createStashCheckbox: Creating checkbox for '$text', defaultChecked=$defaultChecked")

        val checkboxCard = androidx.cardview.widget.CardView(requireContext()).apply {
            radius = 12.dpToPx().toFloat()
            cardElevation = 0f
            setCardBackgroundColor(android.graphics.Color.parseColor("#424242"))
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 8.dpToPx()
            }
        }

        val checkboxLayout = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setPadding(16.dpToPx(), 12.dpToPx(), 16.dpToPx(), 12.dpToPx())

            // Make the entire layout clickable
            isClickable = true
            isFocusable = true
        }

        val checkbox = android.widget.CheckBox(requireContext()).apply {
            tag = "checkbox"
            isChecked = defaultChecked
            buttonTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#98FB98"))

            // Prevent checkbox from handling its own clicks
            isClickable = false
            isFocusable = false
        }

        val checkboxText = android.widget.TextView(requireContext()).apply {
            this.text = text
            textSize = 14f
            setTextColor(android.graphics.Color.WHITE)
            layoutParams = android.widget.LinearLayout.LayoutParams(
                0,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginStart = 8.dpToPx()
            }
        }

        // Handle clicks on the entire layout
        checkboxLayout.setOnClickListener {
            Log.d(TAG, "🎯 Checkbox layout clicked for '$text', current state: ${checkbox.isChecked}")
            checkbox.isChecked = !checkbox.isChecked
            Log.d(TAG, "🎯 Checkbox new state: ${checkbox.isChecked}")
        }

        checkboxLayout.addView(checkbox)
        checkboxLayout.addView(checkboxText)
        checkboxCard.addView(checkboxLayout)

        return checkboxCard
    }

    private fun createThemedStashButton(text: String, isPrimary: Boolean, onClick: () -> Unit): android.view.View {
        val buttonCard = androidx.cardview.widget.CardView(requireContext()).apply {
            radius = 20.dpToPx().toFloat()
            cardElevation = if (isPrimary) 4.dpToPx().toFloat() else 0f
            setCardBackgroundColor(
                if (isPrimary) android.graphics.Color.parseColor("#98FB98")
                else android.graphics.Color.parseColor("#424242") // Changed from #33FFFFFF to dark grey
            )

            isClickable = true
            isFocusable = true
        }

        // Create frame for image background
        val frameLayout = android.widget.FrameLayout(requireContext()).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Image view for pressed state (initially hidden)
        val imageView = android.widget.ImageView(requireContext()).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            setImageResource(R.drawable.button_pressed_background)
            visibility = android.view.View.GONE
        }

        val buttonText = android.widget.TextView(requireContext()).apply {
            this.text = text
            textSize = 14f
            setTextColor(
                if (isPrimary) android.graphics.Color.parseColor("#424242")
                else android.graphics.Color.WHITE
            )
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        frameLayout.addView(imageView)
        frameLayout.addView(buttonText)
        buttonCard.addView(frameLayout)

        // Store original colors
        val originalBackgroundColor = if (isPrimary)
            android.graphics.Color.parseColor("#98FB98")
        else
            android.graphics.Color.parseColor("#424242") // Changed from #33FFFFFF
        val originalTextColor = if (isPrimary)
            android.graphics.Color.parseColor("#424242")
        else
            android.graphics.Color.WHITE

        // Handle touch events
        buttonCard.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    // Show image, hide solid color
                    buttonCard.setCardBackgroundColor(android.graphics.Color.TRANSPARENT)
                    imageView.visibility = android.view.View.VISIBLE

                    // Change text color to white for visibility on image
                    buttonText.setTextColor(android.graphics.Color.WHITE)
                    buttonText.setShadowLayer(4f, 2f, 2f, android.graphics.Color.BLACK)
                    true
                }
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> {
                    // Hide image, restore solid color
                    imageView.visibility = android.view.View.GONE

                    // Don't restore background color if button has throbbing animation
                    if (buttonCard.tag != "throbbing") {
                        buttonCard.setCardBackgroundColor(originalBackgroundColor)
                    }

                    // Restore original text color
                    buttonText.setTextColor(originalTextColor)
                    buttonText.setShadowLayer(0f, 0f, 0f, android.graphics.Color.TRANSPARENT)

                    if (event.action == android.view.MotionEvent.ACTION_UP) {
                        v.performClick()
                    }
                    true
                }
                else -> false
            }
        }

        buttonCard.setOnClickListener {
            onClick()
        }

        return buttonCard
    }

    private fun animateCardSelection(dialog: Dialog, durationMs: Long, onComplete: () -> Unit) {
        val contentView = dialog.window?.decorView?.findViewById<android.view.View>(android.R.id.content)
        val container = contentView as? android.view.ViewGroup
        val mainCard = container?.tag as? android.view.View ?: container?.getChildAt(0) ?: contentView

        val fadeOut = android.animation.ObjectAnimator.ofFloat(mainCard, "alpha", 1f, 0f)
        fadeOut.duration = durationMs
        fadeOut.interpolator = android.view.animation.AccelerateInterpolator()

        fadeOut.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                dialog.dismiss()

                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    onComplete()
                }, 100)
            }
        })

        fadeOut.start()
    }

    private fun performManualFadeIn(view: android.view.View, durationMs: Long) {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val startTime = System.currentTimeMillis()
        val endTime = startTime + durationMs

        val frameDelayMs = 16L // ~60 FPS

        val fadeRunnable = object : Runnable {
            override fun run() {
                val currentTime = System.currentTimeMillis()
                val elapsed = currentTime - startTime
                val progress = kotlin.math.min(elapsed.toFloat() / durationMs.toFloat(), 1f)

                // Apply easing (decelerate interpolation)
                val easedProgress = 1f - (1f - progress) * (1f - progress)

                view.alpha = easedProgress

                if (currentTime < endTime) {
                    // Continue animation
                    handler.postDelayed(this, frameDelayMs)
                } else {
                    // Animation complete - ensure final state
                    view.alpha = 1f
                }
            }
        }

        // Start the animation
        handler.post(fadeRunnable)
    }

    private fun addThrobbingAnimation(view: android.view.View) {
        val cardView = view as? androidx.cardview.widget.CardView ?: return

        // Mark the card as having throbbing animation
        cardView.tag = "throbbing"

        val colors = intArrayOf(
            android.graphics.Color.parseColor("#98FB98"),
            android.graphics.Color.parseColor("#7AD67A"), // Mid green
            android.graphics.Color.parseColor("#98FB98")
        )

        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        var animationProgress = 0f
        var increasing = true

        val animationRunnable = object : Runnable {
            override fun run() {
                // Update progress
                if (increasing) {
                    animationProgress += 0.02f
                    if (animationProgress >= 1f) {
                        animationProgress = 1f
                        increasing = false
                    }
                } else {
                    animationProgress -= 0.02f
                    if (animationProgress <= 0f) {
                        animationProgress = 0f
                        increasing = true
                    }
                }

                // Calculate color based on progress
                val color = if (animationProgress <= 0.5f) {
                    blendColors(colors[0], colors[1], animationProgress * 2)
                } else {
                    blendColors(colors[1], colors[2], (animationProgress - 0.5f) * 2)
                }

                cardView.setCardBackgroundColor(color)

                // Continue animation
                handler.postDelayed(this, 50) // Update every 50ms for smooth animation
            }
        }

        handler.post(animationRunnable)
    }

    private fun blendColors(from: Int, to: Int, ratio: Float): Int {
        val inverseRatio = 1f - ratio
        val a = (android.graphics.Color.alpha(from) * inverseRatio + android.graphics.Color.alpha(to) * ratio).toInt()
        val r = (android.graphics.Color.red(from) * inverseRatio + android.graphics.Color.red(to) * ratio).toInt()
        val g = (android.graphics.Color.green(from) * inverseRatio + android.graphics.Color.green(to) * ratio).toInt()
        val b = (android.graphics.Color.blue(from) * inverseRatio + android.graphics.Color.blue(to) * ratio).toInt()
        return android.graphics.Color.argb(a, r, g, b)
    }

    // Extension function for dp to px conversion
    private fun Int.dpToPx(): Int {
        return (this * requireContext().resources.displayMetrics.density).toInt()
    }


    private fun showRemoveStashDialog() {
        val dialog = Dialog(requireContext(), android.R.style.Theme_Translucent_NoTitleBar_Fullscreen)

        // Root container - full screen
        val rootContainer = android.widget.FrameLayout(requireContext()).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }

        // Create a vertical LinearLayout to hold spacer and card
        val contentWrapper = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // INVISIBLE SPACER
        val topSpacer = android.view.View(requireContext()).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        contentWrapper.addView(topSpacer)

        // Main card at bottom - RAISED BY 180dp
        val mainCard = androidx.cardview.widget.CardView(requireContext()).apply {
            radius = 20.dpToPx().toFloat()
            cardElevation = 12.dpToPx().toFloat()
            setCardBackgroundColor(android.graphics.Color.parseColor("#E64A4A4A"))

            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(16.dpToPx(), 0, 16.dpToPx(), 180.dpToPx())
            }
        }

        // Store card for animation reference
        rootContainer.tag = mainCard

        val contentLayout = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(24.dpToPx(), 24.dpToPx(), 24.dpToPx(), 24.dpToPx())
        }

        // Title
        val titleText = android.widget.TextView(requireContext()).apply {
            text = "REMOVE FROM STASH"
            textSize = 22f
            setTextColor(android.graphics.Color.parseColor("#98FB98"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
            letterSpacing = 0.15f
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 24.dpToPx()
            }
        }
        contentLayout.addView(titleText)

        // Tab container
        val tabContainer = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                48.dpToPx()
            ).apply {
                bottomMargin = 24.dpToPx()
            }
        }

        // My Stash Tab
        val myStashTab = androidx.cardview.widget.CardView(requireContext()).apply {
            radius = 12.dpToPx().toFloat()
            cardElevation = 0f
            setCardBackgroundColor(android.graphics.Color.parseColor("#98FB98"))
            layoutParams = android.widget.LinearLayout.LayoutParams(
                0,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                1f
            ).apply {
                marginEnd = 8.dpToPx()
            }
            isClickable = true
        }

        val myStashTabText = android.widget.TextView(requireContext()).apply {
            text = "My Stash"
            textSize = 14f
            setTextColor(android.graphics.Color.parseColor("#424242"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        myStashTab.addView(myStashTabText)

        // Their Stash Tab
        val theirStashTab = androidx.cardview.widget.CardView(requireContext()).apply {
            radius = 12.dpToPx().toFloat()
            cardElevation = 0f
            setCardBackgroundColor(android.graphics.Color.parseColor("#424242"))
            layoutParams = android.widget.LinearLayout.LayoutParams(
                0,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                1f
            ).apply {
                marginStart = 8.dpToPx()
            }
            isClickable = true
        }

        val theirStashTabText = android.widget.TextView(requireContext()).apply {
            text = "Their Stash"
            textSize = 14f
            setTextColor(android.graphics.Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        theirStashTab.addView(theirStashTabText)

        tabContainer.addView(myStashTab)
        tabContainer.addView(theirStashTab)
        contentLayout.addView(tabContainer)

        // Content containers for each tab
        val myStashContent = createRemoveStashContent(true)
        val theirStashContent = createRemoveStashContent(false)

        contentLayout.addView(myStashContent)
        contentLayout.addView(theirStashContent)

        // Initially show My Stash
        theirStashContent.visibility = android.view.View.GONE

        // Restore last used values
        val prefs = requireContext().getSharedPreferences("stash_prefs", Context.MODE_PRIVATE)
        val lastSelectedTab = prefs.getInt("last_remove_tab", 0)

        // Setup tab selection
        fun selectTab(index: Int) {
            if (index == 0) {
                myStashTab.setCardBackgroundColor(android.graphics.Color.parseColor("#98FB98"))
                myStashTabText.setTextColor(android.graphics.Color.parseColor("#424242"))
                theirStashTab.setCardBackgroundColor(android.graphics.Color.parseColor("#424242"))
                theirStashTabText.setTextColor(android.graphics.Color.WHITE)
                myStashContent.visibility = android.view.View.VISIBLE
                theirStashContent.visibility = android.view.View.GONE
            } else {
                myStashTab.setCardBackgroundColor(android.graphics.Color.parseColor("#424242"))
                myStashTabText.setTextColor(android.graphics.Color.WHITE)
                theirStashTab.setCardBackgroundColor(android.graphics.Color.parseColor("#98FB98"))
                theirStashTabText.setTextColor(android.graphics.Color.parseColor("#424242"))
                myStashContent.visibility = android.view.View.GONE
                theirStashContent.visibility = android.view.View.VISIBLE
            }
            prefs.edit().putInt("last_remove_tab", index).apply()
        }

        selectTab(lastSelectedTab)

        myStashTab.setOnClickListener { selectTab(0) }
        theirStashTab.setOnClickListener { selectTab(1) }

        // Neon separator with gradient
        val separator = android.view.View(requireContext()).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                2.dpToPx()
            ).apply {
                topMargin = 8.dpToPx()
                bottomMargin = 24.dpToPx()
            }
            background = android.graphics.drawable.GradientDrawable().apply {
                colors = intArrayOf(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.parseColor("#1A98FB98"),
                    android.graphics.Color.parseColor("#1A98FB98"),
                    android.graphics.Color.TRANSPARENT
                )
                orientation = android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT
            }
        }
        contentLayout.addView(separator)

        // Button container
        val buttonContainer = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                48.dpToPx()
            )
        }

        // Cancel button (secondary)
        val cancelButton = createThemedStashButton("Cancel", false) {
            animateCardSelection(dialog, 1000L) {}
        }
        cancelButton.layoutParams = android.widget.LinearLayout.LayoutParams(
            0,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            1f
        ).apply {
            marginEnd = 8.dpToPx()
        }

        // Remove button (primary with throbbing)
        val removeButton = createThemedStashButton("Remove", true) {
            val currentTab = if (myStashContent.visibility == android.view.View.VISIBLE) 0 else 1
            val content = if (currentTab == 0) myStashContent else theirStashContent

            val checkbox = content.findViewWithTag<android.widget.CheckBox>("checkbox")
            val editGrams = content.findViewWithTag<android.widget.EditText>("grams")
            val editCost = content.findViewWithTag<android.widget.EditText>("cost")

            val currentStash = stashViewModel.currentStash.value ?: Stash()

            animateCardSelection(dialog, 1000L) {
                handleRemoveFromStash(
                    checkbox.isChecked,
                    editGrams.text.toString(),
                    editCost.text.toString(),
                    currentStash.pricePerGram,
                    isMyStash = (currentTab == 0)
                )

                // Save values
                prefs.edit().apply {
                    if (currentTab == 0) {
                        putFloat("last_remove_my_grams", editGrams.text.toString().toFloatOrNull() ?: 0f)
                        putFloat("last_remove_my_cost", editCost.text.toString().toFloatOrNull() ?: 0f)
                    } else {
                        putFloat("last_remove_their_grams", editGrams.text.toString().toFloatOrNull() ?: 0f)
                        putFloat("last_remove_their_cost", editCost.text.toString().toFloatOrNull() ?: 0f)
                    }
                    apply()
                }
            }
        }
        removeButton.layoutParams = android.widget.LinearLayout.LayoutParams(
            0,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            1f
        ).apply {
            marginStart = 8.dpToPx()
        }

        // Add throbbing to Remove button
        addThrobbingAnimation(removeButton as androidx.cardview.widget.CardView)

        buttonContainer.addView(cancelButton)
        buttonContainer.addView(removeButton)
        contentLayout.addView(buttonContainer)

        mainCard.addView(contentLayout)
        contentWrapper.addView(mainCard)
        rootContainer.addView(contentWrapper)

        // Add click to dismiss on background
        rootContainer.setOnClickListener {
            if (it == rootContainer) {
                animateCardSelection(dialog, 1000L) {}
            }
        }

        dialog.setContentView(rootContainer)

        dialog.window?.apply {
            setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.parseColor("#80000000")))
            setFlags(
                android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            )
        }

        // Set initial alpha to 0 for fade-in
        rootContainer.alpha = 0f

        dialog.show()

        // Apply fade-in animation
        performManualFadeIn(rootContainer, 2000L)
    }

    private fun createRemoveStashContent(isMyStash: Boolean): android.widget.LinearLayout {
        Log.d(TAG, "🎯 createRemoveStashContent: Creating content for isMyStash=$isMyStash")

        val contentLayout = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
        }

        // Ratio checkbox
        val checkboxCard = androidx.cardview.widget.CardView(requireContext()).apply {
            radius = 12.dpToPx().toFloat()
            cardElevation = 0f
            setCardBackgroundColor(android.graphics.Color.parseColor("#424242"))
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 16.dpToPx()
            }
        }

        val checkboxLayout = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setPadding(16.dpToPx(), 12.dpToPx(), 16.dpToPx(), 12.dpToPx())

            // Make the entire layout clickable
            isClickable = true
            isFocusable = true
        }

        val checkbox = android.widget.CheckBox(requireContext()).apply {
            tag = "checkbox"
            isChecked = true
            buttonTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#98FB98"))

            // Prevent checkbox from handling its own clicks
            isClickable = false
            isFocusable = false
        }

        val checkboxText = android.widget.TextView(requireContext()).apply {
            text = "Use ratio for auto-calculation"
            textSize = 14f
            setTextColor(android.graphics.Color.parseColor("#98FB98"))
            layoutParams = android.widget.LinearLayout.LayoutParams(
                0,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginStart = 8.dpToPx()
            }
        }

        // Handle clicks on the entire layout to prevent dialog closing
        checkboxLayout.setOnClickListener {
            Log.d(TAG, "🎯 REMOVE: Ratio checkbox clicked, current state: ${checkbox.isChecked}")
            checkbox.isChecked = !checkbox.isChecked
            Log.d(TAG, "🎯 REMOVE: Ratio checkbox new state: ${checkbox.isChecked}")
            // Stop event propagation
        }

        checkboxLayout.addView(checkbox)
        checkboxLayout.addView(checkboxText)
        checkboxCard.addView(checkboxLayout)
        contentLayout.addView(checkboxCard)

        // Rest of the function remains the same...
        // Grams input
        val gramsCard = androidx.cardview.widget.CardView(requireContext()).apply {
            radius = 12.dpToPx().toFloat()
            cardElevation = 0f
            setCardBackgroundColor(android.graphics.Color.parseColor("#424242"))
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                48.dpToPx()
            ).apply {
                bottomMargin = 16.dpToPx()
            }
        }

        val editGrams = android.widget.EditText(requireContext()).apply {
            tag = "grams"
            hint = "Enter grams to remove here"
            setHintTextColor(android.graphics.Color.parseColor("#707070"))
            setTextColor(android.graphics.Color.WHITE)
            textSize = 16f
            background = null
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setPadding(16.dpToPx(), 0, 16.dpToPx(), 0)
            setTypeface(typeface, android.graphics.Typeface.ITALIC)
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        gramsCard.addView(editGrams)
        contentLayout.addView(gramsCard)

        // Cost input
        val costCard = androidx.cardview.widget.CardView(requireContext()).apply {
            radius = 12.dpToPx().toFloat()
            cardElevation = 0f
            setCardBackgroundColor(android.graphics.Color.parseColor("#424242"))
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                48.dpToPx()
            ).apply {
                bottomMargin = 16.dpToPx()
            }
        }

        val editCost = android.widget.EditText(requireContext()).apply {
            tag = "cost"
            hint = "Enter cost to remove here ($)"
            setHintTextColor(android.graphics.Color.parseColor("#707070"))
            setTextColor(android.graphics.Color.WHITE)
            textSize = 16f
            background = null
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setPadding(16.dpToPx(), 0, 16.dpToPx(), 0)
            setTypeface(typeface, android.graphics.Typeface.ITALIC)
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        costCard.addView(editCost)
        contentLayout.addView(costCard)

        // Remove All button (secondary, no throbbing)
        val removeAllButton = createThemedStashButton(
            if (isMyStash) "Remove All from My Stash" else "Remove All from Their Stash",
            false
        ) {
            if (isMyStash) {
                val currentStash = stashViewModel.currentStash.value
                if (currentStash != null && currentStash.currentGrams > 0) {
                    androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle("Remove All?")
                        .setMessage("Remove all ${decimalFormat.format(currentStash.currentGrams)} grams from My Stash?")
                        .setPositiveButton("Yes") { _, _ ->
                            stashViewModel.removeAllFromStash()
                            Toast.makeText(requireContext(), "Removed all from My Stash", Toast.LENGTH_SHORT).show()
                            recalculateStats()
                        }
                        .setNegativeButton("No", null)
                        .show()
                } else {
                    Toast.makeText(requireContext(), "My Stash is empty", Toast.LENGTH_SHORT).show()
                }
            } else {
                if (theirStashTotalGrams > 0) {
                    androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle("Remove All?")
                        .setMessage("Remove all ${decimalFormat.format(theirStashTotalGrams)} grams from Their Stash?")
                        .setPositiveButton("Yes") { _, _ ->
                            removeAllFromTheirStash()
                            Toast.makeText(requireContext(), "Removed all from Their Stash", Toast.LENGTH_SHORT).show()
                            recalculateStats()
                        }
                        .setNegativeButton("No", null)
                        .show()
                } else {
                    Toast.makeText(requireContext(), "Their Stash is empty", Toast.LENGTH_SHORT).show()
                }
            }
        }
        removeAllButton.layoutParams = android.widget.LinearLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            48.dpToPx()
        )

        contentLayout.addView(removeAllButton)

        // Setup auto-calculation
        val currentStash = stashViewModel.currentStash.value ?: Stash()
        val hasRatio = currentStash.pricePerGram > 0

        if (!hasRatio) {
            checkbox.isEnabled = false
            checkbox.isChecked = false
        }

        // Restore saved values
        val prefs = requireContext().getSharedPreferences("stash_prefs", Context.MODE_PRIVATE)
        if (isMyStash) {
            val lastGrams = prefs.getFloat("last_remove_my_grams", 0f)
            val lastCost = prefs.getFloat("last_remove_my_cost", 0f)
            if (lastGrams > 0) editGrams.setText(decimalFormat.format(lastGrams))
            if (lastCost > 0) editCost.setText(currencyFormat.format(lastCost).replace("$", ""))
        } else {
            val lastGrams = prefs.getFloat("last_remove_their_grams", 0f)
            val lastCost = prefs.getFloat("last_remove_their_cost", 0f)
            if (lastGrams > 0) editGrams.setText(decimalFormat.format(lastGrams))
            if (lastCost > 0) editCost.setText(currencyFormat.format(lastCost).replace("$", ""))
        }

        return contentLayout
    }

    private fun handleRemoveFromStash(
        useRatio: Boolean,
        gramsStr: String,
        costStr: String,
        pricePerGram: Double,
        isMyStash: Boolean
    ) {
        try {
            var grams: Double? = null
            var cost: Double? = null

            if (useRatio && pricePerGram > 0) {
                // Checkbox checked - auto-calculate missing value
                when {
                    gramsStr.isNotEmpty() && costStr.isNotEmpty() -> {
                        // Both provided - use as is
                        grams = gramsStr.toDouble()
                        cost = costStr.toDouble()
                    }
                    gramsStr.isNotEmpty() -> {
                        // Only grams provided - calculate cost
                        grams = gramsStr.toDouble()
                        cost = grams * pricePerGram
                    }
                    costStr.isNotEmpty() -> {
                        // Only cost provided - calculate grams
                        cost = costStr.toDouble()
                        grams = cost / pricePerGram
                    }
                    else -> {
                        Toast.makeText(requireContext(), "Please enter at least one value", Toast.LENGTH_SHORT).show()
                        return
                    }
                }
            } else {
                // Checkbox unchecked or no ratio - single value entry
                when {
                    gramsStr.isNotEmpty() -> {
                        grams = gramsStr.toDouble()
                        cost = if (costStr.isNotEmpty()) {
                            costStr.toDouble()
                        } else {
                            grams * pricePerGram
                        }
                    }
                    costStr.isNotEmpty() -> {
                        cost = costStr.toDouble()
                        grams = if (pricePerGram > 0) {
                            cost / pricePerGram
                        } else {
                            Toast.makeText(requireContext(), "Cannot calculate grams without price per gram", Toast.LENGTH_SHORT).show()
                            return
                        }
                    }
                    else -> {
                        Toast.makeText(requireContext(), "Please enter at least one value", Toast.LENGTH_SHORT).show()
                        return
                    }
                }
            }

            if (isMyStash) {
                stashViewModel.removeFromStash(grams, cost)
                Toast.makeText(requireContext(), "Removed ${decimalFormat.format(grams)}g from My Stash", Toast.LENGTH_SHORT).show()
            } else {
                removeFromTheirStash(grams, cost)
                Toast.makeText(requireContext(), "Removed ${decimalFormat.format(grams)}g from Their Stash", Toast.LENGTH_SHORT).show()
            }
            recalculateStats()
        } catch (e: NumberFormatException) {
            Toast.makeText(requireContext(), "Invalid number format", Toast.LENGTH_SHORT).show()
        }
    }

    private fun removeFromTheirStash(grams: Double, cost: Double) {
        lifecycleScope.launch(Dispatchers.IO) {
            theirStashTotalGrams = kotlin.math.max(0.0, theirStashTotalGrams - grams)
            theirStashTotalCost = kotlin.math.max(0.0, theirStashTotalCost - cost)

            withContext(Dispatchers.Main) {
                binding.textTheirStashGrams.text = "${decimalFormat.format(theirStashTotalGrams)}g"
                binding.textTheirStashValue.text = currencyFormat.format(theirStashTotalCost)
                recalculateStats()
            }
        }
    }

    private fun removeAllFromTheirStash() {
        lifecycleScope.launch(Dispatchers.IO) {
            theirStashTotalGrams = 0.0
            theirStashTotalCost = 0.0

            withContext(Dispatchers.Main) {
                binding.textTheirStashGrams.text = "0.0g"
                binding.textTheirStashValue.text = "$0.00"
                recalculateStats()
            }
        }
    }

    fun setAttributionRadioSilently(source: StashSource) {
        Log.d(TAG, "🎯 setAttributionRadioSilently called with source: $source")

        _binding?.let { validBinding ->
            // Temporarily remove the listener to avoid triggering changes
            validBinding.radioGroupAttribution.setOnCheckedChangeListener(null)

            // Set the radio button
            val radioId = when(source) {
                StashSource.MY_STASH -> R.id.radioMyStashAttribution
                StashSource.THEIR_STASH -> R.id.radioTheirStashAttribution
                StashSource.EACH_TO_OWN -> R.id.radioEachToOwnAttribution
            }

            Log.d(TAG, "🎯 Setting radio button to ID: $radioId (current: ${validBinding.radioGroupAttribution.checkedRadioButtonId})")
            validBinding.radioGroupAttribution.check(radioId)

            // Force the UI to update immediately
            validBinding.radioGroupAttribution.invalidate()
            validBinding.radioGroupAttribution.requestLayout()

            // Re-attach the listener after ensuring UI is updated
            validBinding.root.post {
                validBinding.radioGroupAttribution.setOnCheckedChangeListener { _, checkedId ->
                    val newSource = when (checkedId) {
                        R.id.radioMyStashAttribution -> StashSource.MY_STASH
                        R.id.radioTheirStashAttribution -> StashSource.THEIR_STASH
                        R.id.radioEachToOwnAttribution -> StashSource.EACH_TO_OWN
                        else -> StashSource.MY_STASH
                    }
                    Log.d(TAG, "🎯 Radio changed by user to: $newSource")
                    stashViewModel.updateStashSource(newSource)
                }

                // Verify the change took effect
                val finalChecked = validBinding.radioGroupAttribution.checkedRadioButtonId
                Log.d(TAG, "🎯 Final radio button ID after update: $finalChecked")
            }
        } ?: run {
            Log.w(TAG, "🎯 setAttributionRadioSilently: binding is null")
        }
    }

    fun syncRadioWithViewModel() {
        _binding?.let { validBinding ->
            val currentSource = stashViewModel.stashSource.value ?: StashSource.MY_STASH

            Log.d(TAG, "🎯 syncRadioWithViewModel: ViewModel source = $currentSource")

            // Temporarily remove listener
            validBinding.radioGroupAttribution.setOnCheckedChangeListener(null)

            val radioId = when(currentSource) {
                StashSource.MY_STASH -> R.id.radioMyStashAttribution
                StashSource.THEIR_STASH -> R.id.radioTheirStashAttribution
                StashSource.EACH_TO_OWN -> R.id.radioEachToOwnAttribution
            }

            if (validBinding.radioGroupAttribution.checkedRadioButtonId != radioId) {
                Log.d(TAG, "🎯 Radio mismatch detected! Fixing: current=${validBinding.radioGroupAttribution.checkedRadioButtonId}, should be=$radioId")
                validBinding.radioGroupAttribution.check(radioId)
            }

            // Re-attach listener
            validBinding.radioGroupAttribution.setOnCheckedChangeListener { _, checkedId ->
                val newSource = when (checkedId) {
                    R.id.radioMyStashAttribution -> StashSource.MY_STASH
                    R.id.radioTheirStashAttribution -> StashSource.THEIR_STASH
                    R.id.radioEachToOwnAttribution -> StashSource.EACH_TO_OWN
                    else -> StashSource.MY_STASH
                }
                stashViewModel.updateStashSource(newSource)
            }
        }
    }

    fun setAttributionRadio(source: StashSource) {
        val radioId = when(source) {
            StashSource.MY_STASH -> R.id.radioMyStashAttribution
            StashSource.THEIR_STASH -> R.id.radioTheirStashAttribution
            StashSource.EACH_TO_OWN -> R.id.radioEachToOwnAttribution
        }
        binding.radioGroupAttribution.check(radioId)
    }
}