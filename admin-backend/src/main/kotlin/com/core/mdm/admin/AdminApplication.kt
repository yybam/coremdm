package com.core.mdm.admin

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity

@SpringBootApplication
@EnableMethodSecurity(prePostEnabled = true)
class AdminApplication

fun main(args: Array<String>) {
    runApplication<AdminApplication>(*args)
}
