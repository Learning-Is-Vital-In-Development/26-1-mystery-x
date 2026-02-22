package com.example.demo

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class HelloController {
    @GetMapping("/")
    fun hello(): Map<String, String> = mapOf("message" to "Hello from Spring Boot 4 + Kotlin")
}
