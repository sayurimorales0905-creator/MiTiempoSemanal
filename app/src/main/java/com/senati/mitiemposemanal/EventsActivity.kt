package com.senati.mitiemposemanal

import android.database.Cursor
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class EventsActivity : AppCompatActivity() {
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var autoCompleteTask: AutoCompleteTextView
    private lateinit var spinnerEventCategory: Spinner
    private lateinit var btnStartEvent: Button
    private lateinit var btnEndEvent: Button
    private lateinit var tvActiveEventStatus: TextView
    private lateinit var lvEventsHistory: ListView

    private var activeEventId: Int = -1
    private var selectedCategoryId: Int = -1
    private val categoryIdsList = ArrayList<Int>()
    private val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_events)

        dbHelper = DatabaseHelper(this)
        autoCompleteTask = findViewById(R.id.autoCompleteTask)
        spinnerEventCategory = findViewById(R.id.spinnerEventCategory)
        btnStartEvent = findViewById(R.id.btnStartEvent)
        btnEndEvent = findViewById(R.id.btnEndEvent)
        tvActiveEventStatus = findViewById(R.id.tvActiveEventStatus)
        lvEventsHistory = findViewById(R.id.lvEventsHistory)

        setupAutoComplete()
        setupCategorySpinner()
        checkActiveEvent()
        loadEventsHistory()

        btnStartEvent.setOnClickListener {
            val taskName = autoCompleteTask.text.toString().trim()
            if (taskName.isEmpty()) {
                Toast.makeText(this, "Escribe el nombre de una tarea", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            var taskId = dbHelper.getTaskIdByName(taskName)
            if (taskId == -1) {
                // HU-04: Si no existe, se crea con la categoría seleccionada del Spinner
                if (spinnerEventCategory.visibility != View.VISIBLE) {
                    spinnerEventCategory.visibility = View.VISIBLE
                    Toast.makeText(this, "La tarea no existe. Elige una categoría y pulsa Iniciar nuevamente.", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                val newId = dbHelper.addTask(taskName, selectedCategoryId)
                if (newId != -1L) {
                    taskId = newId.toInt()
                } else {
                    Toast.makeText(this, "Error al crear la tarea", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }

            // HU-05: Iniciar evento con fecha y hora del celular
            val startTime = LocalDateTime.now().format(formatter)
            val eventId = dbHelper.startEvent(taskId, startTime)
            if (eventId != -1L) {
                Toast.makeText(this, "Evento iniciado", Toast.LENGTH_SHORT).show()
                spinnerEventCategory.visibility = View.GONE
                autoCompleteTask.text.clear()
                checkActiveEvent()
                loadEventsHistory()
            }
        }

        btnEndEvent.setOnClickListener {
            if (activeEventId != -1) {
                val endTime = LocalDateTime.now().format(formatter)
                // Calcular duración usando LocalDateTime (requiere Desugaring)
                val cursor = dbHelper.getActiveEvent()
                var startTimeStr = ""
                if (cursor.moveToFirst()) {
                    startTimeStr = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_EVENT_START))
                }
                cursor.close()

                var durationFormatted = "0 min"
                try {
                    val startDateTime = LocalDateTime.parse(startTimeStr, formatter)
                    val endDateTime = LocalDateTime.parse(endTime, formatter)
                    val duration = Duration.between(startDateTime, endDateTime)
                    val hours = duration.toHours()
                    val minutes = duration.toMinutes() % 60
                    durationFormatted = if (hours > 0) "$hours h $minutes min" else "$minutes min"
                } catch (e: Exception) {
                    durationFormatted = "N/D"
                }

                if (dbHelper.endEvent(activeEventId, endTime, durationFormatted)) {
                    Toast.makeText(this, "Evento finalizado. Duración: $durationFormatted", Toast.LENGTH_LONG).show()
                    activeEventId = -1
                    checkActiveEvent()
                    loadEventsHistory()
                }
            }
        }
    }

    private fun setupAutoComplete() {
        val cursor = dbHelper.getAllTasksWithCategory()
        val taskNames = ArrayList<String>()
        while (cursor.moveToNext()) {
            taskNames.add(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TASK_NAME)))
        }
        cursor.close()

        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, taskNames)
        autoCompleteTask.setAdapter(adapter)

        autoCompleteTask.setOnItemClickListener { _, _, _, _ ->
            spinnerEventCategory.visibility = View.GONE
        }
    }

    private fun setupCategorySpinner() {
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
        spinnerEventCategory.adapter = adapter

        spinnerEventCategory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedCategoryId = categoryIdsList[position]
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun checkActiveEvent() {
        val cursor = dbHelper.getActiveEvent()
        if (cursor.moveToFirst()) {
            activeEventId = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_EVENT_ID))
            val taskName = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TASK_NAME))
            val catName = cursor.getString(cursor.getColumnIndexOrThrow("category_name"))
            val startTime = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_EVENT_START))

            tvActiveEventStatus.text = "En curso: $taskName ($catName)\nIniciado: $startTime"
            btnStartEvent.isEnabled = false
            btnEndEvent.isEnabled = true
            autoCompleteTask.isEnabled = false
        } else {
            activeEventId = -1
            tvActiveEventStatus.text = "No hay evento en curso"
            btnStartEvent.isEnabled = true
            btnEndEvent.isEnabled = false
            autoCompleteTask.isEnabled = true
            setupAutoComplete() // Actualizar lista de autocompletado
        }
        cursor.close()
    }

    private fun loadEventsHistory() {
        val cursor = dbHelper.getAllEvents()
        val historyList = ArrayList<String>()

        while (cursor.moveToNext()) {
            val tName = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TASK_NAME))
            val cName = cursor.getString(cursor.getColumnIndexOrThrow("category_name"))
            val start = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_EVENT_START))
            val end = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_EVENT_END))
            val duration = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_EVENT_DURATION))

            val statusText = if (end.isNullOrEmpty()) "En curso..." else "Fin: $end (Duración: $duration)"
            historyList.add("• $tName [$cName]\n  Inicio: $start\n  $statusText")
        }
        cursor.close()

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, historyList)
        lvEventsHistory.adapter = adapter
    }
}