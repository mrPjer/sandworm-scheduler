package hr.fer.zemris.sandworm

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication

@SpringBootApplication
class SchedulerApplication

fun main(args: Array<String>) {
    SpringApplication.run(SchedulerApplication::class.java, *args)
}
