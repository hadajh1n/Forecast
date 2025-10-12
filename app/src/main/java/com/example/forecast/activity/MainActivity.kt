package com.example.forecast.activity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.forecast.R
import com.example.forecast.WeatherRepository

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        WeatherRepository.init(this@MainActivity)
    }
}