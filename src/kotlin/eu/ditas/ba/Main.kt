package eu.ditas.ba
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import mu.KotlinLogging
import kotlin.system.exitProcess

private val logger = KotlinLogging.logger {}


class BenchAgentCLI : CliktCommand() {
    private val payloadURL by option("-p","--payload",help="link to the benchmarking payload")

    override fun run() {
        if (payloadURL.isNullOrBlank()){
            logger.error { "no payload for agent - terminating!" }
            exitProcess(-1)
        }

        logger.info { "runner initialized with $payloadURL" }
        try {
            val payload = Payload.newInstanceFrom(payloadURL.toString())

            if (payload == null) {
                logger.error { "payload empty" }
                exitProcess(-1)
            }

            Worker(payload)
        } catch (e:FailedToDownload){
            logger.error { "payload unavailable" }
            exitProcess(-1)
        }


    }
}

fun main(args: Array<String>) = BenchAgentCLI().main(args)