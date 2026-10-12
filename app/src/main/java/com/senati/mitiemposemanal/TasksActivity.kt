package com.senati.mitiemposemanal

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class TasksActivity : AppCompatActivity() {
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var etTaskName: EditText
    private lateinit var spinnerCategories: Spinner
    private lateinit var btnAddTask: Button
    private lateinit var lvTasks: ListView

    private var selectedCategoryId: Int = -1
    private val categoryIdsList = ArrayList<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tasks)

        dbHelper = DatabaseHelper(this)
        etTaskName = findViewById(R.id.etTaskName)
        spinnerCategories = findViewById(R.id.spinnerCategories)
        btnAddTask = findViewById(R.id.btnAddTask)
        lvTasks = findViewById(R.id.lvTasks)

        loadCategorySpinner()
        loadTasksList()

        spinnerCategories.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                selectedCategoryId = categoryIdsList[position]
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // No se requiere ninguna acción
            }
        }

        btnAddTask.setOnClickListener {
            val taskName = etTaskName.text.toString().trim()
            if (taskName.isNotEmpty() && selectedCategoryId != -1) {
                val success = dbHelper.addTask(taskName, selectedCategoryId)
                if (success) {
                    etTaskName.text.clear()
                    loadTasksList()
                    Toast.makeText(this, "Tarea guardada", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Error: La tarea ya existe o está vacía", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Complete todos los campos", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadCategorySpinner() {
        val cursor = dbHelper.getAllCategories()
        val categoryNames = ArrayList<String>()
        categoryIdsList.clear()

        while (cursor.moveToNext()) {
            categoryIdsList.add(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_CAT_ID)))
            categoryNames.add(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_CAT_NAME)))
        }
        cursor.close()

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categoryNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCategories.adapter = adapter
    }

    private fun loadTasksList() {
        val cursor = dbHelper.getAllTasksWithCategory()
        val tasksDisplayList = ArrayList<String>()
        val taskIds = ArrayList<Int>()

        while (cursor.moveToNext()) {
            taskIds.add(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TASK_ID)))
            val tName = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TASK_NAME))
            val cName = cursor.getString(cursor.getColumnIndexOrThrow("category_name"))
            // Criterio HU-02: Formato "Tarea · Categoría"
            tasksDisplayList.add("$tName · $cName")
        }
        cursor.close()

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, tasksDisplayList)
        lvTasks.adapter = adapter

        lvTasks.setOnItemClickListener { _, _, position, _ ->
            val taskId = taskIds[position]
            confirmDeleteTask(taskId)
        }
    }

    private fun confirmDeleteTask(taskId: Int) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar Tarea")
            .setMessage("¿Deseas eliminar esta tarea?")
            .setPositiveButton("Sí") { _, _ ->
                if (dbHelper.deleteTask(taskId)) {
                    loadTasksList()
                    Toast.makeText(this, "Tarea eliminada", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}