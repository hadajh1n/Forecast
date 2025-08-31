package com.example.forecast.activity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.forecast.R
import com.example.forecast.fragments.CityFragment

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.fragment_container, CityFragment())
                .commit()
        }
    }
}