package eu.ditas.ba

import com.google.gson.annotations.SerializedName

data class WorkloadResult(
        @SerializedName("reqId") val id: String,
        @SerializedName("thread") val thread: Int,
        @SerializedName("iteration") val iteration: Int,
        @SerializedName("latency") val latency: Long,
        @SerializedName("statusCode") val statusCode: Int,
        @SerializedName("body") val body: String?,
        @SerializedName("headers") val headers : Map<String,Collection<String>>,
        @SerializedName("error") val error: Boolean
)