package eu.ditas.ba

import com.github.kittinunf.fuel.core.Method
import kotlinx.coroutines.delay
import org.junit.Before
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.mockserver.client.server.MockServerClient
import org.mockserver.integration.ClientAndServer
import org.mockserver.model.HttpRequest
import org.mockserver.model.HttpResponse
import java.lang.Thread.sleep
import java.nio.file.Files
import kotlin.random.Random


internal fun randomFrom(from: Int = 1024, to: Int = 65535) : Int {
    return Random.nextInt(to - from) + from
}

abstract class HttpTestBase {
    private val port = randomFrom()
    var mockServer: MockServerClient = MockServerClient("localhost", port)
    val url = "http://localhost:$port"

    @BeforeEach
    fun prepare() {
        mockServer = ClientAndServer.startClientAndServer(port)
    }

    @AfterEach
    fun tearDown() {
        mockServer.close()
    }
}

internal class WorkerTest : HttpTestBase() {

    private fun makeDummyRequest(id:String,url:String): Requests {
        return Requests(id,Method.GET,url, mutableMapOf(),"")
    }

    private fun makeDummyRequests(num:Int,url:String): List<Requests> {
        return 1.rangeTo(num).map { makeDummyRequest("$it",url) }
    }

    @Test
    fun testBench() {
        var responseCounter = 0;
        //setup server
        mockServer.`when`(HttpRequest.request().withMethod("GET").withPath("/test"))
                .callback {
                    sleep(Random.nextInt(10,100).toLong())
                    responseCounter+=1
                    HttpResponse()
                }

        val numRequests = 10;
        val requests = listOf(makeDummyRequests(numRequests,"$url/test"))

        val payload = Payload(10,true,"test",2,requests,"sequential",2,"none",1500);
        val worker = Worker(payload)

        worker.prepare()
        worker.start()
        val result = worker.cleanup()

        val expectedResponses = numRequests*payload.iterations*payload.threads

        //the responseCounter is higher due to warmups (the number is not static as warmup happens for a fixed period not a fixed number of requests)

        Assertions.assertTrue(expectedResponses <= responseCounter)
        val results = result.rawResults.fold(initial = listOf(), operation = fun(acc: List<Worker.WorkloadResult>, list: List<Worker.WorkloadResult>): List<Worker.WorkloadResult> {
            return acc + list
        })
        Assertions.assertTrue(results.size <= responseCounter)


    }
}
fun getResourceAsText(path: String): String {
    return object {}.javaClass.getResource(path).readText()
}

internal class PayloadTest :HttpTestBase(){

    companion object {
        lateinit var validPayloadString:String;

        @BeforeAll
        @JvmStatic
        fun loadFiles(){
            validPayloadString  = getResourceAsText("/ValidPayload.json")
        }
    }

    @Test
    fun testNewPayloadFromDownload(){

        mockServer
                .`when`(HttpRequest.request("/validpayload").withMethod("GET"))
                .respond(HttpResponse.response().withHeader("Content-Type","application/json").withBody(validPayloadString).withStatusCode(200))
        mockServer
                .`when`(HttpRequest.request("/nopayload").withMethod("GET"))
                .respond(HttpResponse.response().withStatusCode(404))

        mockServer
                .`when`(HttpRequest.request("/unexpected").withMethod("GET"))
                .respond(HttpResponse.response().withStatusCode(204))

        try {
            val payload = Payload.newInstanceFrom("$url/validpayload")
            Assertions.assertNotNull(payload)
            Assertions.assertEquals("5cfa626a37df1044d2c0064f",payload!!.id)
            for (requests in payload.requests) {
                for (request in requests) {
                    Assertions.assertEquals(Method.GET,request.method)
                }
            }
        } catch (e:FailedToDownload){
            fail<Any>("Expected to get a valid payload",e)
        }

        try {
            val payload = Payload.newInstanceFrom("$url/nopayload")
            if(payload != null) {
                fail<Any>("Expected not to get a payload")
            }
        } catch (e:FailedToDownload){
            //success
        }

        try {
            val payload = Payload.newInstanceFrom("$url/unexpected")
            if(payload != null) {
                fail<Any>("Expected not to get a payload")
            }
        } catch (e:FailedToDownload){
            //success
        }

        mockServer.close()

        try {
            val payload = Payload.newInstanceFrom("$url/noop")
            if(payload != null) {
                fail<Any>("Expected not to get a payload")
            }
        } catch (e:FailedToDownload){
            //success
        }
    }

}