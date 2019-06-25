package eu.ditas.ba

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Response
import com.github.kittinunf.fuel.coroutines.awaitByteArrayResponseResult
import com.github.kittinunf.fuel.coroutines.awaitObjectResponse
import com.github.kittinunf.fuel.coroutines.awaitStringResult
import kotlinx.coroutines.*
import mu.KotlinLogging
import java.lang.System.currentTimeMillis
import java.util.concurrent.ConcurrentLinkedQueue
import com.github.kittinunf.result.Result

private val logger = KotlinLogging.logger {}

class Worker(private val payload: Payload) {

    private val workers: MutableList<WorkerThread> = mutableListOf()
    private var experimentStart: Long = 0;

    data class WorkloadResult(val id: String, val rid: String, val time: Long, val code: Int, val data: String?, val error: Boolean)

    class WorkerThread(val id:Int,private val requests: List<Requests>) {

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
                processReply(it, launchTime, response, result)

            }
        }

        private  fun processReply(request:Requests, launchTime:Long, response:Response,result:Result<ByteArray,FuelError>){
            val time = currentTimeMillis() - launchTime
            val code = response.statusCode
            val (data, error) = result

            var body: String = ""
            if (error == null && (data != null && data.isNotEmpty())) {
                body = String(data)
            }

            results.add(WorkloadResult(request.id, request.rid, time, code, body, (error != null)))
        }

        override fun toString(): String {
            return results.toString()
        }
    }

    private fun getValidAccessToken(): String {
        return payload.token
    }

    fun prepare() {
        //refresh token
        val accessToken = this.getValidAccessToken()

        for (requests in payload.requests) {
            //injecting access token and header decoration
            for (request in requests) {
                request.requestHeader["X-DITAS-Benchmark"] = listOf(payload.id)
                request.requestHeader["Authorization"] = listOf("bearer $accessToken")
            }

        }
        //XXX: do this based on the strategy
        for (thread in 1.rangeTo(payload.threads)){
            val requests = mutableListOf<Requests>()
            //adding all request sequentially
            for (reqs in payload.requests) {
                requests.addAll(reqs)
            }

            workers.add(WorkerThread(thread,requests))
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
            for(worker in workers){
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

    data class BenchmarkResult(val totalTime:Long, val rawResults:List<List<WorkloadResult>>)

    fun cleanup() : BenchmarkResult{
        val totalExperimentTime = currentTimeMillis() - experimentStart
        logger.info { "totalTime : $totalExperimentTime" }

        val result = BenchmarkResult(totalTime = totalExperimentTime, rawResults = workers.map { it.results.toList() })
        logger.info { "results: $result" }

        return result
    }

    override fun toString(): String {
        return workers.toString()
    }

}