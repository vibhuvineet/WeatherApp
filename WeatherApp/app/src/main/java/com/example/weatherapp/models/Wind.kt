package com.example.weatherapp.models

import java.io.Serializable
data class Wind(
    val speed: Double,
    val deg: Double,
    val gust: Double
):Serializable
