package xyz.neruxov.prodcontestbot

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ProdContestBotApplication

fun main(args: Array<String>) {
    runApplication<ProdContestBotApplication>(*args)
}
