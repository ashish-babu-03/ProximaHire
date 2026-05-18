package com.app.proximahire

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class ProximahireApplication

fun main(args: Array<String>) {
	runApplication<ProximahireApplication>(*args)
}
