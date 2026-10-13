package com.senati.mitiemposemanal

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class StatItem(
    val categoryName: String,
    val minutes: Long,
    val percentageText: String,
    val progressInt: Int
)

class StatsAdapter(private val items: List<StatItem>) : BaseAdapter() {
    override fun getCount(): Int = items.size
    override fun getItem(position: Int): Any = items[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val context = parent?.context ?: return View(parent?.context)
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_stat_row, parent, false)
        val item = items[position]

        val tvCategory = view.findViewById<TextView>(R.id.tvStatCategory)
        val tvDuration = view.findViewById<TextView>(R.id.tvStatDuration)
        val progressBar = view.findViewById<ProgressBar>(R.id.progressStat)
        val tvPercentage = view.findViewById<TextView>(R.id.tvStatPercentage)

        tvCategory.text = item.categoryName
        val hours = item.minutes / 60
        val mins = item.minutes % 60
        tvDuration.text = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
        
        progressBar.progress = item.progressInt
        tvPercentage.text = "${item.percentageText} de las 168h semanales"

        return view
    }
}

class ReportsActivity : AppCompatActivity() {
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var tvWeekRange: TextView
    private lateinit var tvTotalTime: TextView
    private lateinit var lvCategorySummary: ListView
    private lateinit var spinnerReportFilter: Spinner

    private val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val displayDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reports)

        dbHelper = DatabaseHelper(this)
        tvWeekRange = findViewById(R.id.tvWeekRange)
        tvTotalTime = findViewById(R.id.tvTotalTime)
        lvCategorySummary = findViewById(R.id.lvCategorySummary)
        spinnerReportFilter = findViewById(R.id.spinnerReportFilter)

        setupWeekRangeHeader()
        setupFilterSpinner()
    }

    override fun onResume() {
        super.onResume()
        setupWeekRangeHeader()
        val isCurrentWeek = spinnerReportFilter.selectedItemPosition == 0
        loadWeeklyReport(isCurrentWeek)
    }

    private fun setupWeekRangeHeader() {
        val today = LocalDate.now()
        val monday = today.with(DayOfWeek.MONDAY)
        val sunday = today.with(DayOfWeek.SUNDAY)
        tvWeekRange.text = "Semana ${monday.format(displayDateFormatter)} – ${sunday.format(displayDateFormatter)}"
    }

    private fun setupFilterSpinner() {
        val filterOptions = arrayOf("Semana Actual (Lunes a Domingo)", "Todo el Historial Acumulado")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, filterOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerReportFilter.adapter = adapter

        spinnerReportFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                loadWeeklyReport(position == 0)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun loadWeeklyReport(isCurrentWeekOnly: Boolean) {
        // 1. Obtener todas las categorías registradas
        val categoryMap = LinkedHashMap<String, Long>()
        val catCursor = dbHelper.getAllCategories()
        while (catCursor.moveToNext()) {
            val catName = catCursor.getString(catCursor.getColumnIndexOrThrow(DatabaseHelper.COL_CAT_NAME))
            categoryMap[catName] = 0L
        }
        catCursor.close()

        // 2. Procesar eventos finalizados
        val cursor = dbHelper.getAllEvents()
        var totalMinutesGlobalForSummary = 0L

        val today = LocalDate.now()
        val monday = today.with(DayOfWeek.MONDAY)
        val sunday = today.with(DayOfWeek.SUNDAY)

        while (cursor.moveToNext()) {
            val endTime = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_EVENT_END))
            if (endTime.isNullOrEmpty()) continue // Ignorar eventos en curso

            val categoryName = cursor.getString(cursor.getColumnIndexOrThrow("category_name"))
            val startTimeStr = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_EVENT_START))
            val endTimeStr = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_EVENT_END))

            try {
                val startDateTime = LocalDateTime.parse(startTimeStr, formatter)
                val endDateTime = LocalDateTime.parse(endTimeStr, formatter)

                // Aplicar filtro de semana actual si está activado
                if (isCurrentWeekOnly) {
                    val eventDate = startDateTime.toLocalDate()
                    if (eventDate.isBefore(monday) || eventDate.isAfter(sunday)) {
                        continue
                    }
                }

                val minutes = Duration.between(startDateTime, endDateTime).toMinutes()
                if (minutes > 0) {
                    totalMinutesGlobalForSummary += minutes
                    val currentMins = categoryMap[categoryName] ?: 0L
                    categoryMap[categoryName] = currentMins + minutes
                }
            } catch (_: Exception) {
            }
        }
        cursor.close()

        // Criterio HU-06: 168 horas totales en la semana = 10080 minutos
        val totalWeekMinutes = 168 * 60L

        val totalHours = totalMinutesGlobalForSummary / 60
        val totalMinsRemainder = totalMinutesGlobalForSummary % 60
        tvTotalTime.text = "Total Acumulado: ${totalHours}h ${totalMinsRemainder}m (Registradas)"

        val statItems = ArrayList<StatItem>()
        var sumCategoryMinutes = 0L

        for ((cat, mins) in categoryMap) {
            sumCategoryMinutes += mins
            // Porcentaje calculado sobre 168 horas con un decimal (ej. 12.5 %)
            val percentageDouble = (mins.toDouble() / totalWeekMinutes.toDouble()) * 100.0
            val percentageStr = String.format(Locale.US, "%.1f %%", percentageDouble)
            val progressVal = percentageDouble.toInt().coerceIn(0, 100)
            statItems.add(StatItem(cat, mins, percentageStr, progressVal))
        }

        // Fila adicional "Sin registrar" (horas de la semana que no tienen eventos, calculadas sobre 168h)
        val unregisteredMinutes = (totalWeekMinutes - sumCategoryMinutes).coerceAtLeast(0L)
        val unregisteredPercentageDouble = (unregisteredMinutes.toDouble() / totalWeekMinutes.toDouble()) * 100.0
        val unregisteredPercentageStr = String.format(Locale.US, "%.1f %%", unregisteredPercentageDouble)
        val unregisteredProgressVal = unregisteredPercentageDouble.toInt().coerceIn(0, 100)
        statItems.add(StatItem("Sin registrar", unregisteredMinutes, unregisteredPercentageStr, unregisteredProgressVal))

        val adapter = StatsAdapter(statItems)
        lvCategorySummary.adapter = adapter
    }
}
