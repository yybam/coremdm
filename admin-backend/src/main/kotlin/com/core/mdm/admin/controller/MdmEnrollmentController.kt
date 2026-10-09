package com.core.mdm.admin.controller

import com.core.mdm.admin.dto.EnrollRequest
import com.core.mdm.admin.dto.EnrollResponse
import com.core.mdm.admin.security.FirebaseTokenFilter
import com.core.mdm.admin.service.EnrollmentService
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Public (unauthenticated) endpoint called by the Android MDM app at first boot.
 * No Firebase Bearer token is required — authentication is performed via the
 * one-time enrollmentToken generated during pre-registration.
 */
@RestController
@RequestMapping("/api/v1/mdm")
class MdmEnrollmentController(private val enrollmentService: EnrollmentService) {

    // ── POST /api/v1/mdm/enroll ───────────────────────────────────────────────
    // Expected request body:
    // {
    //   "serialNumber":     "SN001234",
    //   "deviceIdentifier": "3f99c00e2cd057c8",
    //   "imei":             "356938035643809",   ← optional for Wi-Fi-only
    //   "enrollmentToken":  "<token from pre-register response>"
    // }
    @PostMapping("/enroll")
    fun enroll(
        @Valid @RequestBody request: EnrollRequest,
        httpRequest: HttpServletRequest,
    ): ResponseEntity<EnrollResponse> {
        val ip = FirebaseTokenFilter.extractIp(httpRequest)
        val response = enrollmentService.enroll(request, ip)
        return ResponseEntity.ok(response)
    }
}
