package com.sam.cloudcounter

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.result.ActivityResult
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

class FirebaseAuthManager(private val context: Context) {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val googleSignInClient: GoogleSignInClient

    // Store the preferred display name
    private var preferredDisplayName: String? = null

    init {
        val webClientId = context.getString(R.string.default_web_client_id)
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(context, gso)

        // Load preferred name from SharedPreferences
        val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        preferredDisplayName = prefs.getString("preferred_display_name", null)
    }

    fun getCurrentUser(): FirebaseUser? {
        return auth.currentUser
    }

    val isSignedIn: Boolean
        get() = getCurrentUser() != null

    fun getSignInIntent(): Intent {
        return googleSignInClient.signInIntent
    }

    suspend fun firebaseAuthWithGoogle(idToken: String, onResult: (FirebaseUser?) -> Unit) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        try {
            val authResult = auth.signInWithCredential(credential).await()
            val user = authResult.user

            // Store the first part of display name as preferred
            user?.displayName?.let { fullName ->
                val firstName = fullName.split(" ").firstOrNull() ?: fullName
                setPreferredDisplayName(firstName)
            }

            onResult(user)
        } catch (e: Exception) {
            Log.w("FirebaseAuthManager", "signInWithCredential failed", e)
            onResult(null)
        }
    }

    suspend fun handleSignInResult(result: ActivityResult): Result<FirebaseUser> {
        return try {
            Log.d("FirebaseAuthManager", "🔐 handleSignInResult: resultCode=${result.resultCode}")

            if (result.resultCode != android.app.Activity.RESULT_OK) {
                // Try to get more details from the intent
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                val exception = task.exception

                if (exception != null) {
                    Log.e("FirebaseAuthManager", "🔐 Sign-in error details: ${exception.message}", exception)
                    if (exception is ApiException) {
                        val statusCode = exception.statusCode
                        val errorMessage = when (statusCode) {
                            7 -> "Network error: Please check your internet connection and try again"
                            8 -> "Internal error: Please try again"
                            10 -> "Developer error: App configuration issue (check SHA-1 and package name)"
                            12500 -> "Sign-in error: Please update Google Play Services"
                            12501 -> "Sign-in cancelled by user"
                            12502 -> "Sign-in already in progress"
                            else -> "Sign-in failed with code $statusCode"
                        }
                        return Result.failure(Exception(errorMessage))
                    }
                }
                return Result.failure(Exception("Sign-in cancelled or failed"))
            }

            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            val account: GoogleSignInAccount = task.getResult(ApiException::class.java)
                ?: return Result.failure(Exception("No account returned from sign-in"))

            Log.d("FirebaseAuthManager", "🔐 Got Google account: ${account.email}")

            val idToken = account.idToken
            if (idToken == null) {
                Log.e("FirebaseAuthManager", "🔐 ID token is null - OAuth client may be misconfigured")
                Log.e("FirebaseAuthManager", "🔐 Check: 1) SHA-1 in Firebase Console, 2) google-services.json is latest, 3) Package name matches")
                return Result.failure(Exception("Authentication configuration error - please contact support"))
            }

            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = auth.signInWithCredential(credential).await()
            val user = authResult.user ?: return Result.failure(Exception("Sign in failed: No user returned"))

            user.displayName?.let { fullName ->
                val firstName = fullName.split(" ").firstOrNull() ?: fullName
                setPreferredDisplayName(firstName)
            }

            Log.d("FirebaseAuthManager", "🔐 Sign-in successful: ${user.uid}")
            Result.success(user)
        } catch (e: ApiException) {
            val statusCode = e.statusCode
            val errorMessage = when (statusCode) {
                7 -> "Network error: Check internet connection"
                8 -> "Internal error: Please try again"
                10 -> "Configuration error: Invalid app setup"
                12500 -> "Sign-in error: Update Google Play Services"
                12501 -> "Sign-in cancelled"
                12502 -> "Sign-in already in progress"
                else -> "Google Sign-In failed (code $statusCode)"
            }
            Log.e("FirebaseAuthManager", "🔐 ApiException: $errorMessage (code: $statusCode)", e)
            Result.failure(Exception(errorMessage))
        } catch (e: Exception) {
            Log.e("FirebaseAuthManager", "🔐 Unexpected error", e)
            Result.failure(Exception("Sign-in error: ${e.message}"))
        }
    }

    fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        return capabilities != null && (
                capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
                        capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)
                )
    }

    suspend fun signOut() {
        try {
            auth.signOut()
            googleSignInClient.signOut().await()
        } catch (e: Exception) {
            Log.e("FirebaseAuthManager", "Error during sign out", e)
        }
    }

    fun getCurrentUserId(): String? {
        val user = getCurrentUser()
        val uid = user?.uid
        Log.d("FirebaseAuthManager", "🔍 DEBUG getCurrentUserId():")
        Log.d("FirebaseAuthManager", "🔍 DEBUG   - uid: '$uid'")
        Log.d("FirebaseAuthManager", "🔍 DEBUG   - email: '${user?.email}'")
        Log.d("FirebaseAuthManager", "🔍 DEBUG   - displayName: '${user?.displayName}'")
        Log.d("FirebaseAuthManager", "🔍 DEBUG   - isSignedIn: $isSignedIn")
        Log.d("FirebaseAuthManager", "🔍 DEBUG   - user object null?: ${user == null}")
        android.util.Log.d("FirebaseAuthManager", "🔑 getCurrentUserId: uid=$uid, email=${user?.email}, isSignedIn=$isSignedIn")
        return uid
    }

    fun getCurrentUserEmail(): String? {
        return getCurrentUser()?.email
    }

    fun getCurrentUserName(): String? {
        // Return preferred name if set, otherwise use first name from display name
        return preferredDisplayName ?: getCurrentUser()?.displayName?.split(" ")?.firstOrNull() ?: getCurrentUser()?.displayName
    }

    fun getCurrentFullName(): String? {
        // Return the full display name when needed
        return getCurrentUser()?.displayName
    }

    private fun setPreferredDisplayName(name: String) {
        preferredDisplayName = name
        val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("preferred_display_name", name).apply()
    }
}