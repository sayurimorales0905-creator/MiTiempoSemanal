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

class WeekReportRange(
    val monday: LocalDate,
    val sunday: LocalDate
) {
    val label: String
        get() = "${monday.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))} – ${sunday.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}"
}

class ReportsActivity : AppCompatActivity() {
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var spinnerReportFilter: Spinner
    private lateinit var tvNoReports: TextView
    private lateinit var layoutReportContent: LinearLayout
    private lateinit var tvTotalTime: TextView
    private lateinit var containerCategoryStats: LinearLayout
    private lateinit var lvWeekEvents: ListView

    private val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val weekList = ArrayList<WeekReportRange>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reports)

        dbHelper = DatabaseHelper(this)
        spinnerReportFilter = findViewById(R.id.spinnerReportFilter)
        tvNoReports = findViewById(R.id.tvNoReports)
        layoutReportContent = findViewById(R.id.layoutReportContent)
        tvTotalTime = findViewById(R.id.tvTotalTime)
        containerCategoryStats = findViewById(R.id.containerCategoryStats)
        lvWeekEvents = findViewById(R.id.lvWeekEvents)

        setupWeeksSpinner()
    }

    override fun onResume() {
        super.onResume()
        setupWeeksSpinner()
    }

    private fun setupWeeksSpinner() {
        weekList.clear()
        
        // Incluir siempre la semana actual y la próxima semana para que aparezcan en el Spinner inmediatamente
        val today = LocalDate.now()
        val currentMonday = today.with(DayOfWeek.MONDAY)
        val nextMonday = currentMonday.plusWeeks(1)

        val uniqueWeeksSet = HashSet<LocalDate>()
        uniqueWeeksSet.add(currentMonday)
        uniqueWeeksSet.add(nextMonday)

        // También agregar semanas que tengan eventos en la base de datos
        val cursor = dbHelper.getAllEvents()
        while (cursor.moveToNext()) {
            val endTime = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_EVENT_END))
            if (endTime.isNullOrEmpty()) continue

            val startTimeStr = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_EVENT_START))
            try {
                val startDateTime = LocalDateTime.parse(startTimeStr, formatter)
                val eventDate = startDateTime.toLocalDate()
                val monday = eventDate.with(DayOfWeek.MONDAY)
                uniqueWeeksSet.add(monday)
            } catch (_: Exception) {
            }
        }
        cursor.close()

        // Ordenar de la más reciente a la más antigua (Próxima semana primero, luego semana actual, etc.)
        val sortedMondays = uniqueWeeksSet.sortedDescending()
        for (monday in sortedMondays) {
            val sunday = monday.plusDays(6)
            weekList.add(WeekReportRange(monday, sunday))
        }

        if (weekList.isEmpty()) {
            tvNoReports.visibility = View.VISIBLE
            layoutReportContent.visibility = View.GONE
            spinnerReportFilter.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, arrayOf("No hay semanas"))
            return
        }

        tvNoReports.visibility = View.GONE
        layoutReportContent.visibility = View.VISIBLE

        val labels = weekList.map { it.label }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, labels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerReportFilter.adapter = adapter

        spinnerReportFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position < weekList.size) {
                    loadReportForWeek(weekList[position])
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun loadReportForWeek(week: WeekReportRange) {
        // 1. Obtener categorías con sus metas
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

        // 2. Procesar eventos de esa semana específica y lista de eventos
        val cursor = dbHelper.getAllEvents()
        var totalMinutesGlobal = 0L
        val weekEventsList = ArrayList<String>()

        while (cursor.moveToNext()) {
            val endTime = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_EVENT_END))
            if (endTime.isNullOrEmpty()) continue

            val taskName = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TASK_NAME))
            val categoryName = cursor.getString(cursor.getColumnIndexOrThrow("category_name"))
            val startTimeStr = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_EVENT_START))
            val endTimeStr = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_EVENT_END))
            val durationStr = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_EVENT_DURATION))

            try {
                val startDateTime = LocalDateTime.parse(startTimeStr, formatter)
                val eventDate = startDateTime.toLocalDate()

                if (eventDate.isBefore(week.monday) || eventDate.isAfter(week.sunday)) {
                    continue
                }

                weekEventsList.add("• Tarea: $taskName\n  Materia: [$categoryName]\n  Inicio: $startTimeStr\n  Fin: $endTimeStr\n  Duración: $durationStr")

                val minutes = Duration.between(startDateTime, LocalDateTime.parse(endTimeStr, formatter)).toMinutes()
                if (minutes > 0) {
                    totalMinutesGlobal += minutes
                    val currentMins = categoryMinutesMap[categoryName] ?: 0L
                    categoryMinutesMap[categoryName] = currentMins + minutes
                }
            } catch (_: Exception) {
            }
        }
        cursor.close()

        val totalHours = totalMinutesGlobal / 60
        val totalMinsRemainder = totalMinutesGlobal % 60
        tvTotalTime.text = "Total Registrado: ${totalHours}h ${totalMinsRemainder}m (sobre 168h)"

        // Renderizar estadísticas de categorías (sobre 168 horas, con 1 decimal)
        containerCategoryStats.removeAllViews()
        val inflater = LayoutInflater.from(this)
        var sumCategoryMinutes = 0L
        val totalWeekMinutes = 168 * 60L

        for ((cat, goalHours) in categoryGoalMap) {
            val mins = categoryMinutesMap[cat] ?: 0L
            sumCategoryMinutes += mins

            val percentageDouble = (mins.toDouble() / totalWeekMinutes.toDouble()) * 100.0
            val percentageStr = String.format(Locale.US, "%.1f %%", percentageDouble)
            val progressVal = percentageDouble.toInt().coerceIn(0, 100)

            val rowView = inflater.inflate(R.layout.item_stat_row, containerCategoryStats, false)
            val tvCategory = rowView.findViewById<TextView>(R.id.tvStatCategory)
            val tvDuration = rowView.findViewById<TextView>(R.id.tvStatDuration)
            val progressBar = rowView.findViewById<ProgressBar>(R.id.progressStat)
            val tvPercentage = rowView.findViewById<TextView>(R.id.tvStatPercentage)

            tvCategory.text = "$cat (Meta: ${goalHours}h)"
            val hours = mins / 60
            val mRem = mins % 60
            tvDuration.text = if (hours > 0) "${hours}h ${mRem}m" else "${mRem}m"
            progressBar.progress = progressVal
            tvPercentage.text = "$percentageStr de las 168h semanales"

            containerCategoryStats.addView(rowView)
        }

        // Fila "Sin registrar" (168h)
        val unregisteredMinutes = (totalWeekMinutes - sumCategoryMinutes).coerceAtLeast(0L)
        val unregisteredPercentageDouble = (unregisteredMinutes.toDouble() / totalWeekMinutes.toDouble()) * 100.0
        val unregisteredPercentageStr = String.format(Locale.US, "%.1f %%", unregisteredPercentageDouble)
        val unregisteredProgressVal = unregisteredPercentageDouble.toInt().coerceIn(0, 100)

        val unregView = inflater.inflate(R.layout.item_stat_row, containerCategoryStats, false)
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

        containerCategoryStats.addView(unregView)

        // Renderizar lista de eventos de la semana
        if (weekEventsList.isEmpty()) {
            weekEventsList.add("No hay eventos registrados en esta semana.")
        }
        val eventAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, weekEventsList)
        lvWeekEvents.adapter = eventAdapter
    }
}
