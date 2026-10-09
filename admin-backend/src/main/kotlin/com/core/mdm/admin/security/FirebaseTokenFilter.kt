package com.core.mdm.admin.security

import com.core.mdm.admin.model.AppUser
import com.core.mdm.admin.service.UserService
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class FirebaseTokenFilter(
    private val userService: UserService,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        val bearer = request.getHeader("Authorization")
            ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
            ?.substring(7)

        if (bearer != null) {
            try {
                val decoded = FirebaseAuth.getInstance().verifyIdToken(bearer)
                val ip      = extractIp(request)

                val user: AppUser = userService.findOrCreate(
                    uid         = decoded.uid,
                    email       = decoded.email ?: "",
                    displayName = decoded.name,
                    tenantId    = decoded.claims["tenantId"] as? String,
                )
                userService.recordLogin(user.id, ip)

                // Stamp request attributes for the audit interceptor
                request.setAttribute(ATTR_USER_ID,    user.id)
                request.setAttribute(ATTR_USER_EMAIL, user.email)
                request.setAttribute(ATTR_TENANT_ID,  user.tenantId)
                request.setAttribute(ATTR_USER_ROLE,  user.role.name)

                val auth = UsernamePasswordAuthenticationToken(
                    user.id,
                    null,
                    listOf(SimpleGrantedAuthority("ROLE_${user.role.name}")),
                )
                SecurityContextHolder.getContext().authentication = auth

            } catch (_: FirebaseAuthException) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired token")
                return
            }
        }

        chain.doFilter(request, response)
    }

    companion object {
        const val ATTR_USER_ID    = "mdm.userId"
        const val ATTR_USER_EMAIL = "mdm.userEmail"
        const val ATTR_TENANT_ID  = "mdm.tenantId"
        const val ATTR_USER_ROLE  = "mdm.userRole"

        fun extractIp(request: HttpServletRequest): String {
            val xff = request.getHeader("X-Forwarded-For")
            if (!xff.isNullOrBlank()) return xff.split(",").first().trim()
            val realIp = request.getHeader("X-Real-IP")
            if (!realIp.isNullOrBlank()) return realIp.trim()
            return request.remoteAddr
        }
    }
}
