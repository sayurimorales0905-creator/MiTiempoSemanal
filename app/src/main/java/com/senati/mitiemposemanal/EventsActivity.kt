package com.senati.mitiemposemanal

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class EventsActivity : AppCompatActivity() {
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var autoCompleteTask: AutoCompleteTextView
    private lateinit var spinnerEventCategory: Spinner
    private lateinit var btnStartEvent: Button
    private lateinit var btnEndEvent: Button
    private lateinit var tvActiveEventStatus: TextView
    private lateinit var tvWeekRange: TextView
    private lateinit var tvTotalTime: TextView
    private lateinit var containerStats: LinearLayout

    private var activeEventId: Int = -1
    private var selectedCategoryId: Int = -1
    private val categoryIdsList = ArrayList<Int>()
    private val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val displayDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_events)

        dbHelper = DatabaseHelper(this)
        autoCompleteTask = findViewById(R.id.autoCompleteTask)
        spinnerEventCategory = findViewById(R.id.spinnerEventCategory)
        btnStartEvent = findViewById(R.id.btnStartEvent)
        btnEndEvent = findViewById(R.id.btnEndEvent)
        tvActiveEventStatus = findViewById(R.id.tvActiveEventStatus)
        tvWeekRange = findViewById(R.id.tvWeekRange)
        tvTotalTime = findViewById(R.id.tvTotalTime)
        containerStats = findViewById(R.id.containerStats)

        setupAutoComplete()
        setupCategorySpinner()
        checkActiveEvent()
        loadWeeklyStats()

        btnStartEvent.setOnClickListener {
            val taskName = autoCompleteTask.text.toString().trim()
            if (taskName.isEmpty()) {
                Toast.makeText(this, "Escribe el nombre de una tarea", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            var taskId = dbHelper.getTaskIdByName(taskName)
            if (taskId == -1) {
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

            val startTime = LocalDateTime.now().format(formatter)
            val eventId = dbHelper.startEvent(taskId, startTime)
            if (eventId != -1L) {
                Toast.makeText(this, "Evento iniciado", Toast.LENGTH_SHORT).show()
                spinnerEventCategory.visibility = View.GONE
                autoCompleteTask.text.clear()
                checkActiveEvent()
                loadWeeklyStats()
            }
        }

        btnEndEvent.setOnClickListener {
            if (activeEventId != -1) {
                val endTime = LocalDateTime.now().format(formatter)
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
                } catch (_: Exception) {
                    durationFormatted = "N/D"
                }

                if (dbHelper.endEvent(activeEventId, endTime, durationFormatted)) {
                    Toast.makeText(this, "Evento finalizado. Duración: $durationFormatted", Toast.LENGTH_LONG).show()
                    activeEventId = -1
                    checkActiveEvent()
                    loadWeeklyStats()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkActiveEvent()
        loadWeeklyStats()
        setupCategorySpinner()
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
            setupAutoComplete()
        }
        cursor.close()
    }

    private fun loadWeeklyStats() {
        val today = LocalDate.now()
        val monday = today.with(DayOfWeek.MONDAY)
        val sunday = today.with(DayOfWeek.SUNDAY)
        tvWeekRange.text = "Semana ${monday.format(displayDateFormatter)} – ${sunday.format(displayDateFormatter)}"

        // 1. Obtener categorías con sus metas semanales (Sprint 4)
        val categoryGoalMap = LinkedHashMap<String, Int>()
        val categoryMinutesMap = HashMap<String, Long>()
        val catCursor = dbHelper.getAllCategories()
        while (catCursor.moveToNext()) {
            val catName = catCursor.getString(catCursor.getColumnIndexOrThrow(DatabaseHelper.COL_CAT_NAME))
            val goalHours = catCursor.getInt(catCursor.getColumnIndexOrThrow(DatabaseHelper.COL_CAT_GOAL))
            categoryGoalMap[catName] = goalHours
            categoryMinutesMap[catName] = 0L
        }
        catCursor.close()

        // 2. Procesar eventos de la semana actual
        val cursor = dbHelper.getAllEvents()
        var totalMinutesGlobalForSummary = 0L

        while (cursor.moveToNext()) {
            val endTime = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_EVENT_END))
            if (endTime.isNullOrEmpty()) continue

            val categoryName = cursor.getString(cursor.getColumnIndexOrThrow("category_name"))
            val startTimeStr = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_EVENT_START))
            val endTimeStr = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_EVENT_END))

            try {
                val startDateTime = LocalDateTime.parse(startTimeStr, formatter)
                val endDateTime = LocalDateTime.parse(endTimeStr, formatter)

                val eventDate = startDateTime.toLocalDate()
                if (eventDate.isBefore(monday) || eventDate.isAfter(sunday)) {
                    continue
                }

                val minutes = Duration.between(startDateTime, endDateTime).toMinutes()
                if (minutes > 0) {
                    totalMinutesGlobalForSummary += minutes
                    val currentMins = categoryMinutesMap[categoryName] ?: 0L
                    categoryMinutesMap[categoryName] = currentMins + minutes
                }
            } catch (_: Exception) {
            }
        }
        cursor.close()

        val totalHours = totalMinutesGlobalForSummary / 60
        val totalMinsRemainder = totalMinutesGlobalForSummary % 60
        tvTotalTime.text = "Total Registrado esta semana: ${totalHours}h ${totalMinsRemainder}m"

        containerStats.removeAllViews()
        val inflater = LayoutInflater.from(this)
        var sumCategoryMinutes = 0L

        for ((cat, goalHours) in categoryGoalMap) {
            val mins = categoryMinutesMap[cat] ?: 0L
            sumCategoryMinutes += mins

            val goalMinutes = goalHours * 60L
            val progressPercentage = if (goalMinutes > 0) ((mins.toDouble() / goalMinutes.toDouble()) * 100.0) else 0.0
            val progressVal = progressPercentage.toInt().coerceIn(0, 100)
            val percentageStr = String.format(Locale.US, "%.1f %%", progressPercentage)

            val rowView = inflater.inflate(R.layout.item_stat_row, containerStats, false)
            val tvCategory = rowView.findViewById<TextView>(R.id.tvStatCategory)
            val tvDuration = rowView.findViewById<TextView>(R.id.tvStatDuration)
            val progressBar = rowView.findViewById<ProgressBar>(R.id.progressStat)
            val tvPercentage = rowView.findViewById<TextView>(R.id.tvStatPercentage)

            tvCategory.text = "$cat (Meta: ${goalHours}h)"
            val hours = mins / 60
            val mRem = mins % 60
            tvDuration.text = if (hours > 0) "${hours}h ${mRem}m" else "${mRem}m"
            progressBar.progress = progressVal
            tvPercentage.text = "$percentageStr de la meta semanal"

            containerStats.addView(rowView)
        }

        // Fila adicional "Sin registrar" (basada en 168 horas semanales)
        val totalWeekMinutes = 168 * 60L
        val unregisteredMinutes = (totalWeekMinutes - sumCategoryMinutes).coerceAtLeast(0L)
        val unregisteredPercentageDouble = (unregisteredMinutes.toDouble() / totalWeekMinutes.toDouble()) * 100.0
        val unregisteredPercentageStr = String.format(Locale.US, "%.1f %%", unregisteredPercentageDouble)
        val unregisteredProgressVal = unregisteredPercentageDouble.toInt().coerceIn(0, 100)

        val unregView = inflater.inflate(R.layout.item_stat_row, containerStats, false)
        val tvUnregCategory = unregView.findViewById<TextView>(R.id.tvStatCategory)
        val tvUnregDuration = unregView.findViewById<TextView>(R.id.tvStatDuration)
        val unregProgressBar = unregView.findViewById<ProgressBar>(R.id.progressStat)
        val tvUnregPercentage = unregView.findViewById<TextView>(R.id.tvStatPercentage)

        tvUnregCategory.text = "Sin registrar (168h)"
        val unregHours = unregisteredMinutes / 60
        val unregMinsRem = unregisteredMinutes % 60
        tvUnregDuration.text = if (unregHours > 0) "${unregHours}h ${unregMinsRem}m" else "${unregMinsRem}m"
        unregProgressBar.progress = unregisteredProgressVal
        tvUnregPercentage.text = "$unregisteredPercentageStr de las 168h semanales"

        containerStats.addView(unregView)
    }
}
