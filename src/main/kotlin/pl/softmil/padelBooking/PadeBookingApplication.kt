package pl.softmil.padelBooking

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class PadelCoachApplication

fun main(args: Array<String>) {
    runApplication<PadelCoachApplication>(*args)
}
