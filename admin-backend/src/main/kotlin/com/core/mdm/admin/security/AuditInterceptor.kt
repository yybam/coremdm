package com.core.mdm.admin.security

import com.core.mdm.admin.service.AuditLogService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

@Component
class AuditInterceptor(private val auditLogService: AuditLogService) : HandlerInterceptor {

    override fun afterCompletion(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        ex: Exception?,
    ) {
        val userId    = request.getAttribute(FirebaseTokenFilter.ATTR_USER_ID)    as? String
        val userEmail = request.getAttribute(FirebaseTokenFilter.ATTR_USER_EMAIL) as? String
        val tenantId  = request.getAttribute(FirebaseTokenFilter.ATTR_TENANT_ID)  as? String

        // Skip health checks and unauthenticated static asset requests
        val path = request.requestURI
        if (path == "/api/health" || path == "/actuator/health") return
        if (userId == null && response.status == HttpServletResponse.SC_UNAUTHORIZED) return

        auditLogService.log(
            userId     = userId,
            userEmail  = userEmail,
            tenantId   = tenantId,
            ipAddress  = FirebaseTokenFilter.extractIp(request),
            userAgent  = request.getHeader("User-Agent"),
            endpoint   = path,
            httpMethod = request.method,
            statusCode = response.status,
            action     = deriveAction(request),
        )
    }

    private fun deriveAction(request: HttpServletRequest): String? {
        val path   = request.requestURI
        val method = request.method
        return when {
            path.contains("/login")                    -> "LOGIN"
            path.contains("/alarm")                    -> "ALARM_TOGGLE"
            path.contains("/command")                  -> "COMMAND_SEND"
            path.contains("/wipe")                     -> "WIPE"
            path.contains("/admin/users") && method == "GET"   -> "ADMIN_LIST_USERS"
            path.contains("/admin/devices") && method == "GET" -> "ADMIN_LIST_DEVICES"
            path.contains("/admin/audit")              -> "ADMIN_AUDIT_ACCESS"
            path.contains("/policies")                 -> "POLICY_CHANGE"
            else                                       -> null
        }
    }
}
