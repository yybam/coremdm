package com.core.mdm.ui.provisioning

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private const val PREFS          = "mdm_enrollment"
private const val KEY_PROV_DONE  = "provisioning_done"

data class ProvisioningState(
    val showDialog: Boolean = false,
    val token:      String  = "",
    val isLoading:  Boolean = false,
    val error:      String? = null,
)

class ProvisioningViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(ProvisioningState())
    val state: StateFlow<ProvisioningState> = _state.asStateFlow()

    init {
        val done = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_PROV_DONE, false)
        if (!done) _state.update { it.copy(showDialog = true) }
    }

    fun onTokenChange(v: String) = _state.update { it.copy(token = v, error = null) }

    fun verify() {
        val token = _state.value.token.trim()
        if (token.isBlank()) { _state.update { it.copy(error = "Enter the enrollment token") }; return }
        _state.update { it.copy(isLoading = true, error = null) }

        // Step 1: resolve token → IMEI via enrollment_tokens/{token}
        Firebase.firestore.collection("enrollment_tokens").document(token).get()
            .addOnSuccessListener { tokenSnap ->
                if (!tokenSnap.exists()) {
                    _state.update { it.copy(isLoading = false, error = "Invalid token — check it and try again") }
                    return@addOnSuccessListener
                }
                val imei = tokenSnap.getString("imei") ?: run {
                    _state.update { it.copy(isLoading = false, error = "Malformed token record") }
                    return@addOnSuccessListener
                }
                val expiresAt = tokenSnap.getTimestamp("expiresAt")?.toDate()
                if (expiresAt != null && java.util.Date().after(expiresAt)) {
                    _state.update { it.copy(isLoading = false, error = "Token has expired — ask your admin to renew it") }
                    return@addOnSuccessListener
                }

                // Step 2: cross-check token against device_inventory/{imei}
                val invRef = Firebase.firestore.collection("device_inventory").document(imei)
                invRef.get().addOnSuccessListener { invSnap ->
                    if (!invSnap.exists()) {
                        _state.update { it.copy(isLoading = false, error = "Device record not found") }
                        return@addOnSuccessListener
                    }
                    if (invSnap.getString("enrollmentStatus") != "PENDING") {
                        _state.update { it.copy(isLoading = false, error = "Device is already enrolled or blocked") }
                        return@addOnSuccessListener
                    }
                    if (invSnap.getString("enrollmentToken") != token) {
                        _state.update { it.copy(isLoading = false, error = "Token mismatch — may have been renewed") }
                        return@addOnSuccessListener
                    }

                    // Step 3: mark ENROLLED, consume token
                    invRef.update(
                        "enrollmentStatus",          "ENROLLED",
                        "enrolledAt",                FieldValue.serverTimestamp(),
                        "enrollmentToken",           null,
                        "enrollmentTokenExpiresAt",  null,
                    ).addOnSuccessListener {
                        Firebase.firestore.collection("enrollment_tokens").document(token).delete()
                        markDone()
                        _state.update { it.copy(isLoading = false, showDialog = false) }
                    }.addOnFailureListener { e ->
                        _state.update { it.copy(isLoading = false, error = "Could not confirm enrollment: ${e.message}") }
                    }
                }.addOnFailureListener { e ->
                    _state.update { it.copy(isLoading = false, error = "Connection error: ${e.message}") }
                }
            }
            .addOnFailureListener { e ->
                _state.update { it.copy(isLoading = false, error = "Connection error: ${e.message}") }
            }
    }

    fun skip() {
        markDone()
        _state.update { it.copy(showDialog = false) }
    }

    private fun markDone() {
        getApplication<Application>()
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_PROV_DONE, true).apply()
    }
}
