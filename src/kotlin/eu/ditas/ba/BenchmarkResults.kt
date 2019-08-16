package eu.ditas.ba

import com.google.gson.annotations.SerializedName

data class BenchmarkResults (
        @SerializedName("id") val runnerID : String,
        @SerializedName("metaData") val metadata : Payload?,
        @SerializedName("vdc_id") val vdcId : String,
        @SerializedName("workload_id") val payloadId : String,
        @SerializedName("totalRuntime") val totalRuntime : Long,
        @SerializedName("responses") val responses: List<WorkloadResult>
);