package eu.ditas.ba
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.coroutines.awaitStringResult
import com.github.kittinunf.fuel.gson.jsonBody
import mu.KotlinLogging
import kotlin.system.exitProcess

private val logger = KotlinLogging.logger {}


class BenchAgentCLI : CliktCommand() {
    private val payloadURL by option("-p","--payload",help="link to the benchmarking payload")

    @ExperimentalStdlibApi
    override fun run() {
        if (payloadURL.isNullOrBlank()){
            logger.error { "no payload for agent - terminating!" }
            exitProcess(-1)
        }

        logger.info { "runner initialized with $payloadURL" }
        var worker:Worker;
        try {
            val payload = Payload.newInstanceFrom(payloadURL.toString())

            if (payload == null) {
                logger.error { "payload empty" }
                exitProcess(-1)
            }

            worker = Worker(payload)
        } catch (e:FailedToDownload){
            logger.error { "payload unavailable" }
            exitProcess(-1)
        }
        logger.info { "agent ready" }
        worker.prepare()

        logger.info { "benchmark prepared, starting ..." }
        worker.start()

        logger.info { "benchmark finished, processing results" }
        val benchmarkResults = worker.cleanup()

        var tries = 0;
        var done = false;

        while(tries < 5 && !done) Fuel.post(payloadURL!!).jsonBody(benchmarkResults).responseString { request, response, result -> run{
            tries++;
            val (data,error) = result;
            if(error != null){
                logger.error { "failed to send results to $payloadURL " }
            } else {
                if(response.statusCode == 200){
                    logger.info { "done" }
                    done = true
                    exitProcess(-1)
                } else {
                    logger.error { "failed to send results to $payloadURL got ${response.statusCode}" }
                }
            }
        }}

    }
}

fun main(args: Array<String>) = BenchAgentCLI().main(args)