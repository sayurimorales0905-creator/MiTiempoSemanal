package com.senati.mitiemposemanal

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "mi_tiempo_semanal.db"
        private const val DATABASE_VERSION = 3 // Versión 3 con datos de prueba para próxima semana

        const val TABLE_CATEGORIES = "categories"
        const val COL_CAT_ID = "id"
        const val COL_CAT_NAME = "name"
        const val COL_CAT_GOAL = "goal_hours"

        const val TABLE_TASKS = "tasks"
        const val COL_TASK_ID = "id"
        const val COL_TASK_NAME = "name"
        const val COL_TASK_CAT_ID = "category_id"

        const val TABLE_EVENTS = "events"
        const val COL_EVENT_ID = "id"
        const val COL_EVENT_TASK_ID = "task_id"
        const val COL_EVENT_START = "start_time"
        const val COL_EVENT_END = "end_time"
        const val COL_EVENT_DURATION = "duration"
    }

    override fun onCreate(db: SQLiteDatabase?) {
        val createCategoryTable = ("CREATE TABLE $TABLE_CATEGORIES (" +
                "$COL_CAT_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "$COL_CAT_NAME TEXT UNIQUE, " +
                "$COL_CAT_GOAL INTEGER DEFAULT 5)")

        val createTaskTable = ("CREATE TABLE $TABLE_TASKS (" +
                "$COL_TASK_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "$COL_TASK_NAME TEXT UNIQUE, " +
                "$COL_TASK_CAT_ID INTEGER, " +
                "FOREIGN KEY($COL_TASK_CAT_ID) REFERENCES $TABLE_CATEGORIES($COL_CAT_ID))")

        val createEventTable = ("CREATE TABLE $TABLE_EVENTS (" +
                "$COL_EVENT_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "$COL_EVENT_TASK_ID INTEGER, " +
                "$COL_EVENT_START TEXT, " +
                "$COL_EVENT_END TEXT, " +
                "$COL_EVENT_DURATION TEXT, " +
                "FOREIGN KEY($COL_EVENT_TASK_ID) REFERENCES $TABLE_TASKS($COL_TASK_ID))")

        db?.execSQL(createCategoryTable)
        db?.execSQL(createTaskTable)
        db?.execSQL(createEventTable)
        insertInitialCategories(db)
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            val createEventTable = ("CREATE TABLE IF NOT EXISTS $TABLE_EVENTS (" +
                    "$COL_EVENT_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "$COL_EVENT_TASK_ID INTEGER, " +
                    "$COL_EVENT_START TEXT, " +
                    "$COL_EVENT_END TEXT, " +
                    "$COL_EVENT_DURATION TEXT, " +
                    "FOREIGN KEY($COL_EVENT_TASK_ID) REFERENCES $TABLE_TASKS($COL_TASK_ID))")
            db?.execSQL(createEventTable)
        }
        if (oldVersion < 3) {
            try {
                db?.execSQL("ALTER TABLE $TABLE_CATEGORIES ADD COLUMN $COL_CAT_GOAL INTEGER DEFAULT 5")
            } catch (_: Exception) {}
        }
    }

    private fun insertInitialCategories(db: SQLiteDatabase?) {
        val defaultCategories = arrayOf("Estudio", "Trabajo", "Descanso", "Ocio")
        for (cat in defaultCategories) {
            val values = ContentValues().apply {
                put(COL_CAT_NAME, cat)
                put(COL_CAT_GOAL, 5)
            }
            db?.insert(TABLE_CATEGORIES, null, values)
        }

        // Tarea inicial de prueba
        val taskValues = ContentValues().apply {
            put(COL_TASK_NAME, "Estudio de Programación")
            put(COL_TASK_CAT_ID, 1)
        }
        val taskId = db?.insert(TABLE_TASKS, null, taskValues)

        // Evento de prueba para la próxima semana (para que aparezca en el Spinner de Reportes)
        try {
            val today = LocalDate.now()
            val nextMonday = today.with(DayOfWeek.MONDAY).plusWeeks(1)
            val startTime = nextMonday.atTime(10, 0).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            val endTime = nextMonday.atTime(13, 0).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

            val eventValues = ContentValues().apply {
                put(COL_EVENT_TASK_ID, taskId ?: 1L)
                put(COL_EVENT_START, startTime)
                put(COL_EVENT_END, endTime)
                put(COL_EVENT_DURATION, "3 h 0 min")
            }
            db?.insert(TABLE_EVENTS, null, eventValues)
        } catch (_: Exception) {}
    }

    // --- MÉTODOS DE CATEGORÍAS ---
    fun addCategory(name: String, goalHours: Int = 5): Boolean {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COL_CAT_NAME, name)
            put(COL_CAT_GOAL, goalHours)
        }
        return try {
            db.insertOrThrow(TABLE_CATEGORIES, null, values) != -1L
        } catch (e: Exception) { false }
    }

    fun getAllCategories(): Cursor {
        val db = this.readableDatabase
        return db.rawQuery("SELECT * FROM $TABLE_CATEGORIES", null)
    }

    fun updateCategory(id: Int, newName: String): Boolean {
        val db = this.writableDatabase
        val values = ContentValues().apply { put(COL_CAT_NAME, newName) }
        return try {
            db.update(TABLE_CATEGORIES, values, "$COL_CAT_ID = ?", arrayOf(id.toString())) > 0
        } catch (e: Exception) { false }
    }

    fun updateCategoryGoal(categoryId: Int, goalHours: Int): Boolean {
        val db = this.writableDatabase
        val values = ContentValues().apply { put(COL_CAT_GOAL, goalHours) }
        return try {
            db.update(TABLE_CATEGORIES, values, "$COL_CAT_ID = ?", arrayOf(categoryId.toString())) > 0
        } catch (e: Exception) { false }
    }

    fun deleteCategory(categoryId: Int): Boolean {
        val db = this.writableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_TASKS WHERE $COL_TASK_CAT_ID = ?", arrayOf(categoryId.toString()))
        if (cursor.count > 0) {
            cursor.close()
            return false
        }
        cursor.close()
        return db.delete(TABLE_CATEGORIES, "$COL_CAT_ID = ?", arrayOf(categoryId.toString())) > 0
    }

    // --- MÉTODOS DE TAREAS ---
    fun addTask(name: String, categoryId: Int): Long {
        if (name.isBlank()) return -1L
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COL_TASK_NAME, name)
            put(COL_TASK_CAT_ID, categoryId)
        }
        return try {
            db.insert(TABLE_TASKS, null, values)
        } catch (e: Exception) { -1L }
    }

    fun getAllTasksWithCategory(): Cursor {
        val db = this.readableDatabase
        val query = "SELECT t.$COL_TASK_ID, t.$COL_TASK_NAME, c.$COL_CAT_NAME AS category_name, t.$COL_TASK_CAT_ID, c.$COL_CAT_GOAL " +
                "FROM $TABLE_TASKS t INNER JOIN $TABLE_CATEGORIES c ON t.$COL_TASK_CAT_ID = c.$COL_CAT_ID"
        return db.rawQuery(query, null)
    }

    fun searchTasks(keyword: String): Cursor {
        val db = this.readableDatabase
        val query = "SELECT t.$COL_TASK_ID, t.$COL_TASK_NAME, c.$COL_CAT_NAME AS category_name, t.$COL_TASK_CAT_ID " +
                "FROM $TABLE_TASKS t INNER JOIN $TABLE_CATEGORIES c ON t.$COL_TASK_CAT_ID = c.$COL_CAT_ID " +
                "WHERE t.$COL_TASK_NAME LIKE ?"
        return db.rawQuery(query, arrayOf("%$keyword%"))
    }

    fun getTaskIdByName(name: String): Int {
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT $COL_TASK_ID FROM $TABLE_TASKS WHERE $COL_TASK_NAME = ?", arrayOf(name))
        var id = -1
        if (cursor.moveToFirst()) {
            id = cursor.getInt(cursor.getColumnIndexOrThrow(COL_TASK_ID))
        }
        cursor.close()
        return id
    }

    fun updateTask(id: Int, newName: String, categoryId: Int): Boolean {
        if (newName.isBlank()) return false
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COL_TASK_NAME, newName)
            put(COL_TASK_CAT_ID, categoryId)
        }
        return try {
            db.update(TABLE_TASKS, values, "$COL_TASK_ID = ?", arrayOf(id.toString())) > 0
        } catch (e: Exception) { false }
    }

    fun deleteTask(taskId: Int): Boolean {
        val db = this.writableDatabase
        return db.delete(TABLE_TASKS, "$COL_TASK_ID = ?", arrayOf(taskId.toString())) > 0
    }

    // --- MÉTODOS DE EVENTOS ---
    fun startEvent(taskId: Int, startTime: String): Long {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COL_EVENT_TASK_ID, taskId)
            put(COL_EVENT_START, startTime)
            put(COL_EVENT_END, "")
            put(COL_EVENT_DURATION, "")
        }
        return db.insert(TABLE_EVENTS, null, values)
    }

    fun getActiveEvent(): Cursor {
        val db = this.readableDatabase
        val query = "SELECT e.$COL_EVENT_ID, e.$COL_EVENT_TASK_ID, e.$COL_EVENT_START, t.$COL_TASK_NAME, c.$COL_CAT_NAME AS category_name " +
                "FROM $TABLE_EVENTS e " +
                "INNER JOIN $TABLE_TASKS t ON e.$COL_EVENT_TASK_ID = t.$COL_TASK_ID " +
                "INNER JOIN $TABLE_CATEGORIES c ON t.$COL_TASK_CAT_ID = c.$COL_CAT_ID " +
                "WHERE e.$COL_EVENT_END = '' OR e.$COL_EVENT_END IS NULL"
        return db.rawQuery(query, null)
    }

    fun endEvent(eventId: Int, endTime: String, duration: String): Boolean {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COL_EVENT_END, endTime)
            put(COL_EVENT_DURATION, duration)
        }
        return db.update(TABLE_EVENTS, values, "$COL_EVENT_ID = ?", arrayOf(eventId.toString())) > 0
    }

    fun getAllEvents(): Cursor {
        val db = this.readableDatabase
        val query = "SELECT e.$COL_EVENT_ID, t.$COL_TASK_NAME, c.$COL_CAT_NAME AS category_name, e.$COL_EVENT_START, e.$COL_EVENT_END, e.$COL_EVENT_DURATION, c.$COL_CAT_GOAL " +
                "FROM $TABLE_EVENTS e " +
                "INNER JOIN $TABLE_TASKS t ON e.$COL_EVENT_TASK_ID = t.$COL_TASK_ID " +
                "INNER JOIN $TABLE_CATEGORIES c ON t.$COL_TASK_CAT_ID = c.$COL_CAT_ID " +
                "ORDER BY e.$COL_EVENT_ID DESC"
        return db.rawQuery(query, null)
    }
}
