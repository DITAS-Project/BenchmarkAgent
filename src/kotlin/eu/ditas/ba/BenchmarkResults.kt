package eu.ditas.ba

import com.google.gson.annotations.SerializedName

data class BenchmarkResults (
        @SerializedName("id") val runnerID : String,
        @SerializedName("metadata") val metadata : Payload?,
        @SerializedName("vdcId") val vdcId : String,
        @SerializedName("wlId") val payloadId : String,
        @SerializedName("totalRuntime") val totalRuntime : Long,
        @SerializedName("responses") val responses: List<WorkloadResult>
);