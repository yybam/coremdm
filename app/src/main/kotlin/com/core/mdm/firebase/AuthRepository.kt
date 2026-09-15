package com.core.mdm.firebase

import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

object AuthRepository {
    private val auth get() = Firebase.auth

    private const val AUTH_TIMEOUT_MS = 15_000L
    private const val TIMEOUT_MESSAGE =
        "Request timed out. Check your internet connection and that Google Play Services is installed and up to date, then try again."

    val currentUser: FirebaseUser? get() = auth.currentUser
    val currentUid: String? get() = auth.currentUser?.uid

    val authState: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun signIn(email: String, password: String): Result<FirebaseUser> = withAuthTimeout {
        auth.signInWithEmailAndPassword(email, password).await().user!!
    }

    suspend fun signUp(email: String, password: String): Result<FirebaseUser> = withAuthTimeout {
        auth.createUserWithEmailAndPassword(email, password).await().user!!
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> = withAuthTimeout {
        auth.sendPasswordResetEmail(email).await()
    }

    fun signOut() = auth.signOut()

    /**
     * Firebase Auth's underlying Task never completes on some devices (e.g. Google Play
     * Services present but not passing its own certification check) instead of failing fast,
     * which used to leave the login/sign-up screen spinning forever with no error shown.
     * Bound every auth call with a timeout so a stuck request always surfaces as a visible,
     * actionable error instead of an infinite loading spinner.
     */
    private suspend fun <T> withAuthTimeout(block: suspend () -> T): Result<T> =
        try {
            Result.success(withTimeout(AUTH_TIMEOUT_MS) { block() })
        } catch (e: TimeoutCancellationException) {
            Result.failure(Exception(TIMEOUT_MESSAGE))
        } catch (e: Exception) {
            Result.failure(e)
        }
}
