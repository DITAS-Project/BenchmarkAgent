package eu.ditas.ba

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Response
import com.github.kittinunf.fuel.coroutines.awaitByteArrayResponseResult
import com.github.kittinunf.fuel.coroutines.awaitStringResult
import com.github.kittinunf.fuel.gson.gsonDeserializerOf
import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.result.Result
import com.google.gson.Gson
import com.google.gson.JsonObject
import io.jsonwebtoken.Jwts
import kotlinx.coroutines.*
import mu.KotlinLogging
import java.io.IOException
import java.lang.System.currentTimeMillis
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue


private val logger = KotlinLogging.logger {}

class Worker(private val payload: Payload) {

    private val workers: MutableList<WorkerThread> = mutableListOf()
    private var experimentStart: Long = 0;

    class WorkerThread(val id: Int, private val requests: List<Requests>) {

        val results: ConcurrentLinkedQueue<WorkloadResult> = ConcurrentLinkedQueue()

        suspend fun warmup(warmup: Int) {
            withTimeoutOrNull(warmup.toLong()) {
                requests.forEach {
                    val (body, fuelError) = Fuel.request(it.method, it.path)
                            .header(it.requestHeader)
                            .useHttpCache(false)
                            .awaitStringResult()
                }
            }


        }

        suspend fun run(itter: Int) {
            requests.forEach {
                val launchTime = currentTimeMillis()
                it.rid = "$itter"
                val request = Fuel.request(it.method, it.path)
                        .header(it.requestHeader)
                        .useHttpCache(false)

                val (_, response, result) = request.awaitByteArrayResponseResult()
                processReply(it, itter, launchTime, response, result)

            }
        }

        private fun processReply(request: Requests, itter: Int, launchTime: Long, response: Response, result: Result<ByteArray, FuelError>) {
            val latancy = currentTimeMillis() - launchTime
            val statusCode = response.statusCode
            val (data, error) = result

            var body: String = ""
            if (error == null && (data != null && data.isNotEmpty())) {
                body = String(data)
            }

            results.add(WorkloadResult(request.id, id, itter, latancy, statusCode, body, response.headers, error != null))
        }

        override fun toString(): String {
            return results.toString()
        }
    }

    private fun getValidAccessToken(): String {

        //check if refresh token is present
        if (payload.token == null || payload.token == "") throw FailedTokenParsing("no token present", Exception(""));

        // delete signature since we don't need it but the library will not parse w/o key when token is singed
        val i = payload.token.lastIndexOf('.')
        val withoutSignature = payload.token.substring(0, i + 1);

        //parse token
        val jwtBody = Jwts.parser().parseClaimsJwt(withoutSignature).body
        val realmURL = jwtBody.get("iss").toString() + "/protocol/openid-connect"
        val tokenURL = "$realmURL/token"
        val clientId = jwtBody.get("azp").toString()

        // get the access token on the token endpoint
        val (request, response, result) = tokenURL.httpPost(listOf(Pair("client_id", clientId), Pair("grant_type", "refresh_token"), Pair("refresh_token", payload.token)))
                .responseObject(gsonDeserializerOf(JsonObject::class.java))
        val (body, error) = result
        var ret = "";

        // check if access token is present
        if (error != null) {
            throw FailedTokenParsing("unable to get access token due to ${error.message}", error.exception)
        } else {
            if (body == null) {
                throw FailedTokenParsing("unable to get access token due to empty response body", Exception(""))
            } else {
                ret = body.get("access_token").asString
            }
        }
        logger.info { "building request using access_token: [$ret]" }
        return ret;

    }

    fun prepare() {
        //refresh token
        var accessToken = ""
        try {
            accessToken = this.getValidAccessToken()
        } catch (e: Exception) {
            logger.error { "token refresh failed due to ${e.message}" }
            if (payload.strict) throw e

        }

        for (requests in payload.requests) {
            //injecting access token and header decoration
            for (request in requests) {
                request.requestHeader["X-DITAS-Benchmark"] = listOf(payload.id)
                request.requestHeader["Authorization"] = listOf("bearer $accessToken")
            }

        }
        //XXX: do this based on the strategy
        for (thread in 1.rangeTo(payload.threads)) {
            val requests = mutableListOf<Requests>()
            //adding all request sequentially
            for (reqs in payload.requests) {
                requests.addAll(reqs)
            }

            workers.add(WorkerThread(thread, requests))
        }

        //setup measurements
        experimentStart = currentTimeMillis()
    }

    fun start() {
        if (!payload.executable) {
            logger.info { "payload is not executable - done." }
            return
        }

        //current implementation always uses sequential
        assert(payload.strategy == "sequential")
        val results = mutableListOf<Deferred<Unit>>()
        runBlocking {
            for (worker in workers) {
                results += async {
                    worker.warmup(payload.warmup)
                    logger.info { "[${worker.id}] finished warm up" }

                    repeat(payload.iterations) {
                        delay(payload.delay.toLong())
                        logger.info { "[${worker.id}] [$it] sending payload" }
                        worker.run(it)
                        logger.info { "[${worker.id}] [$it] done payload" }
                    }
                }
            }
            results.forEach { it.await() }
        }
        //start threads
    }


    @ExperimentalStdlibApi
    fun cleanup(): BenchmarkResults {
        val totalExperimentTime = currentTimeMillis() - experimentStart
        logger.info { "totalTime : $totalExperimentTime" }

        val results = BenchmarkResults(
                runnerID = UUID.randomUUID().toString(),
                metadata = payload,
                payloadId = payload.id,
                totalRuntime = totalExperimentTime,
                vdcId = "",//TODO: how to get this?
                responses = workers.map { it.results.toList() }.flatten()
        );

        logger.info { "results: $results" }

        try {
            Files.write(Paths.get("/tmp/${results.runnerID}.json"), Gson().toJson(results).encodeToByteArray());
            logger.info { "wrtitten results to /tmp/${results.runnerID}.json" }
        } catch (e: IOException) {
            logger.error("failed to write backup", e)
        }

        return results
    }

    override fun toString(): String {
        return workers.toString()
    }

}