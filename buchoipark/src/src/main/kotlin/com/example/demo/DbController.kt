package com.example.demo

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class DbController(
    private val jdbcTemplate: JdbcTemplate,
) {
    @GetMapping("/db/ping")
    fun ping(): Map<String, String> {
        val version = jdbcTemplate.queryForObject("SELECT sqlite_version()", String::class.java) ?: "unknown"
        return mapOf("database" to "sqlite", "status" to "ok", "version" to version)
    }
}
