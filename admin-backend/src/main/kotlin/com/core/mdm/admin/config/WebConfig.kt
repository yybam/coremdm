package com.core.mdm.admin.config

import com.core.mdm.admin.security.AuditInterceptor
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig(private val auditInterceptor: AuditInterceptor) : WebMvcConfigurer {

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(auditInterceptor)
            .addPathPatterns("/api/**")
    }
}
