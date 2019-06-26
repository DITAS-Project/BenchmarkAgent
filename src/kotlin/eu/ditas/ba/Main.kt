package eu.ditas.ba

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Response
import com.github.kittinunf.fuel.coroutines.awaitStringResult
import com.github.kittinunf.fuel.gson.jsonBody
import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.result.Result
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import kotlin.system.exitProcess

private val logger = KotlinLogging.logger {}


class BenchAgentCLI : CliktCommand() {
    private val payloadURL by option("-p", "--payload", help = "link to the benchmarking payload")

    @ExperimentalStdlibApi
    override fun run() {
        if (payloadURL.isNullOrBlank()) {
            logger.error { "no payload for agent - terminating!" }
            exitProcess(-1)
        }

        logger.info { "runner initialized with $payloadURL" }
        var worker: Worker;
        try {
            val payload = Payload.newInstanceFrom(payloadURL.toString())

            if (payload == null) {
                logger.error { "payload empty" }
                exitProcess(-1)
            }

            worker = Worker(payload)
        } catch (e: FailedToDownload) {
            logger.error { "payload unavailable" }
            exitProcess(-1)
        }
        logger.info { "agent ready" }
        worker.prepare()

        logger.info { "benchmark prepared, starting ..." }
        worker.start()

        logger.info { "benchmark finished, processing results" }
        val benchmarkResults = worker.cleanup()


        runBlocking {
            val cancellableRequest = payloadURL!!.httpPost().header("Content-Type", "application/json")
                    .jsonBody(benchmarkResults).useHttpCache(false).response { _, response, result ->
                        processResultResponse(response, result)
                    }
            cancellableRequest.join()

        }



        }

    private fun processResultResponse(response: Response, result: Result<ByteArray, FuelError>) {
        val (data, error) = result;
        if (error != null) {
            logger.error { "failed to send results to $payloadURL " }
        } else {
            if (response.statusCode == 200) {
                logger.info { "done" }
            } else {
                logger.error { "failed to send results to $payloadURL got ${response.statusCode}" }
            }
        }
    }
}



fun main(args: Array<String>) = BenchAgentCLI().main(args)