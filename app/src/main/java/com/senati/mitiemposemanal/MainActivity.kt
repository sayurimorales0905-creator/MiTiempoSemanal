package com.senati.mitiemposemanal

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inicializar SQLite
        val dbHelper = DatabaseHelper(this)
        dbHelper.writableDatabase

        val btnCategories = findViewById<Button>(R.id.btnManageCategories)
        val btnTasks = findViewById<Button>(R.id.btnManageTasks)
        val btnEvents = findViewById<Button>(R.id.btnManageEvents)

        btnCategories.setOnClickListener {
            startActivity(Intent(this, CategoriesActivity::class.java))
        }

        btnTasks.setOnClickListener {
            startActivity(Intent(this, TasksActivity::class.java))
        }

        btnEvents.setOnClickListener {
            startActivity(Intent(this, EventsActivity::class.java))
        }
    }
}
