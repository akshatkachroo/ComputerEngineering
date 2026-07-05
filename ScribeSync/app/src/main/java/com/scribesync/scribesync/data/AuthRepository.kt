package com.scribesync.scribesync.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

class AuthRepository(
    private val firebaseAuth: FirebaseAuth?,
    private val firestore: FirebaseFirestore?,
    private val userProfileDao: UserProfileDao
) {
    private val _currentUser = MutableStateFlow(firebaseAuth?.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    private val _currentUsername = MutableStateFlow<String?>(null)
    val currentUsername: StateFlow<String?> = _currentUsername.asStateFlow()

    init {
        firebaseAuth?.addAuthStateListener { auth ->
            _currentUser.value = auth.currentUser
            if (auth.currentUser == null) _currentUsername.value = null
        }
    }

    suspend fun loadCachedUsername(uid: String) {
        val cached = userProfileDao.getProfile(uid)
        if (cached != null) {
            _currentUsername.value = cached.username
            return
        }
        try {
            val doc = firestore?.collection("users")?.document(uid)?.get()?.await()
            val username = doc?.getString("username")
            val email = doc?.getString("email") ?: firebaseAuth?.currentUser?.email ?: ""
            if (username != null) {
                userProfileDao.insertProfile(UserProfile(uid, username, email))
                _currentUsername.value = username
            }
        } catch (e: Exception) {
            Log.e("AuthRepository", "Failed to load profile for $uid", e)
        }
    }

    suspend fun signUp(email: String, password: String, username: String): Result<Unit> = runCatching {
        val auth = firebaseAuth ?: error("Firebase Auth is not available")
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        val uid = result.user?.uid ?: error("Sign up did not return a user")
        val profile = UserProfile(uid = uid, username = username, email = email)
        userProfileDao.insertProfile(profile)
        try {
            firestore?.collection("users")?.document(uid)?.set(profile)?.await()
        } catch (e: Exception) {
            Log.e("AuthRepository", "Failed to sync profile to Firestore", e)
        }
        _currentUser.value = auth.currentUser
        _currentUsername.value = username
    }

    suspend fun signIn(email: String, password: String): Result<Unit> = runCatching {
        val auth = firebaseAuth ?: error("Firebase Auth is not available")
        val result = auth.signInWithEmailAndPassword(email, password).await()
        val uid = result.user?.uid ?: error("Sign in did not return a user")
        _currentUser.value = auth.currentUser
        loadCachedUsername(uid)
    }

    // Requires the "users" collection to be readable-by-email-query in
    // Firestore security rules - the default rule of "read only your own
    // profile" won't allow this lookup across other users' docs.
    suspend fun findUserByEmail(email: String): UserProfile? {
        val db = firestore ?: return null
        return try {
            val snapshot = db.collection("users")
                .whereEqualTo("email", email)
                .limit(1)
                .get()
                .await()
            val doc = snapshot.documents.firstOrNull() ?: return null
            val username = doc.getString("username") ?: return null
            val foundEmail = doc.getString("email") ?: email
            UserProfile(uid = doc.id, username = username, email = foundEmail)
        } catch (e: Exception) {
            Log.e("AuthRepository", "Failed to find user by email", e)
            null
        }
    }

    fun signOut() {
        firebaseAuth?.signOut()
        _currentUser.value = null
        _currentUsername.value = null
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> = runCatching {
        val auth = firebaseAuth ?: error("Firebase Auth is not available")
        auth.sendPasswordResetEmail(email).await()
    }
}
