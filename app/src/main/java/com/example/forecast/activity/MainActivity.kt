package com.example.forecast.activity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.forecast.R
import com.example.forecast.databinding.ActivityMainBinding
import com.example.forecast.fragments.CityFragment

class MainActivity : AppCompatActivity() {
    private lateinit var binding : ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, CityFragment())
                .commit()
        }
    }
}