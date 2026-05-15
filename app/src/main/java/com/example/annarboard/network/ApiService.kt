package com.example.annarboard.network

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

data class MBusResponse(
    @SerializedName("bustime-response") val bustimeResponse: BustimeResponse?
)

data class BustimeResponse(
    @SerializedName("prd") val prd: List<Prd>?
)

data class Prd(
    @SerializedName("rt") val rt: String,
    @SerializedName("prdctdn") val prdctdn: String,
    @SerializedName("stpnm") val stpnm: String,
    @SerializedName("prdtm") val prdtm: String,
    @SerializedName("des") val des: String,
    @SerializedName("vid") val vid: String,
    @SerializedName("tatripid") val tatripid: String
)

interface ApiService {
    @GET("api/mbus/predictions")
    suspend fun getMBusPredictions(@Query("stpid") stopIds: String): MBusResponse
}
