package com.core.mdm.admin.controller

import com.core.mdm.admin.service.DuplicateDeviceException
import com.core.mdm.admin.service.EnrollmentException
import com.core.mdm.admin.service.ImeiValidationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    // Bean Validation failures (@Valid on request body)
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<Map<String, Any>> {
        val fieldErrors = ex.bindingResult.fieldErrors.associate { it.field to (it.defaultMessage ?: "invalid") }
        return ResponseEntity.badRequest().body(mapOf(
            "error"  to "Validation failed",
            "fields" to fieldErrors,
        ))
    }

    // Duplicate serial / IMEI / deviceIdentifier in inventory
    @ExceptionHandler(DuplicateDeviceException::class)
    fun handleDuplicate(ex: DuplicateDeviceException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf("error" to (ex.message ?: "Duplicate device")))

    // IMEI Luhn check failure
    @ExceptionHandler(ImeiValidationException::class)
    fun handleImei(ex: ImeiValidationException): ResponseEntity<Map<String, String>> =
        ResponseEntity.badRequest().body(mapOf("error" to (ex.message ?: "Invalid IMEI")))

    // Enrollment handshake failures (bad token, expired, unrecognized hardware)
    @ExceptionHandler(EnrollmentException::class)
    fun handleEnrollment(ex: EnrollmentException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to (ex.message ?: "Enrollment rejected")))

    // CSV parsing and other general bad input
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadArgument(ex: IllegalArgumentException): ResponseEntity<Map<String, String>> =
        ResponseEntity.badRequest().body(mapOf("error" to (ex.message ?: "Bad request")))
}
