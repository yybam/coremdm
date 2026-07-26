package com.core.mdm.admin.config

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import java.io.ByteArrayInputStream

@Configuration
class FirebaseConfig {

    @Value("\${firebase.project-id}")
    private lateinit var projectId: String

    // Optionally supply the full service account JSON via env var FIREBASE_SERVICE_ACCOUNT_JSON.
    // If absent, Google Application Default Credentials are used (works on GCP / Cloud Run automatically).
    @Value("\${firebase.service-account-json:}")
    private lateinit var serviceAccountJson: String

    @PostConstruct
    fun init() {
        if (FirebaseApp.getApps().isNotEmpty()) return

        val credentials = if (serviceAccountJson.isNotBlank()) {
            GoogleCredentials.fromStream(ByteArrayInputStream(serviceAccountJson.toByteArray()))
        } else {
            GoogleCredentials.getApplicationDefault()
        }

        FirebaseApp.initializeApp(
            FirebaseOptions.builder()
                .setCredentials(credentials)
                .setProjectId(projectId)
                .build()
        )
    }
}
