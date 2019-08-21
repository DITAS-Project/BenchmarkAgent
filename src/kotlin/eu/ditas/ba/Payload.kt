/*
Copyright (c) 2019 Kotlin Data Classes Generated from JSON powered by http://www.json2kotlin.com

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

For support, please feel free to contact me at https://www.linkedin.com/in/syedabsar */
package eu.ditas.ba
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.Method
import com.github.kittinunf.fuel.core.ResponseDeserializable
import com.github.kittinunf.fuel.core.ResponseResultOf
import com.github.kittinunf.fuel.gson.gsonDeserializerOf
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

data class Payload (

		@SerializedName("delay") val delay : Int, //in milliseconds
		@SerializedName("executable") val executable : Boolean,
		@SerializedName("id") val id : String,
		@SerializedName("iterations") val iterations : Int,
		@SerializedName("requests") val requests : List<List<Requests>>,
		@SerializedName("strategy") val strategy : String,
		@SerializedName("threads") val threads : Int,
		@SerializedName("token") val token : String, //refresh token
		@SerializedName("warmup") val warmup : Int, //in milliseconds
		@SerializedName("strict")val strict : Boolean = false
){
	companion object {
		fun newInstanceFrom(path:String):Payload? {
			logger.debug { "getting payload from $path" }

			var fuleResult:ResponseResultOf<Payload>
			try {
				fuleResult = Fuel.get(path)
						.responseObject(gsonDeserializerOf(Payload::class.java))
			} catch (e:Throwable){
				throw FailedToDownload("download client failed for $path",e)
			}

			val (_, response, result) = fuleResult

			logger.info { "finished payload request ${response.statusCode}" }

			val (payload,error) = result

			if(error != null){
				logger.error("failed to get payload ${error.response.statusCode} - ${error.message}")
				throw FailedToDownload("failed to download $path",error.exception)
			}

			return payload
		}

	}
}

data class Requests (
		@SerializedName("id") val id : String,
		@SerializedName("method") val method : Method,
		@SerializedName("path") val path : String,
		@SerializedName("requestHeader") val requestHeader : MutableMap<String,List<String>>,
		@SerializedName("rid") var rid : String = ""
)

