package com.senati.mitiemposemanal

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "mi_tiempo_semanal.db"
        private const val DATABASE_VERSION = 1

        const val TABLE_CATEGORIES = "categories"
        const val COL_CAT_ID = "id"
        const val COL_CAT_NAME = "name"

        const val TABLE_TASKS = "tasks"
        const val COL_TASK_ID = "id"
        const val COL_TASK_NAME = "name"
        const val COL_TASK_CAT_ID = "category_id"
    }

    override fun onCreate(db: SQLiteDatabase?) {
        val createCategoryTable = ("CREATE TABLE $TABLE_CATEGORIES (" +
                "$COL_CAT_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "$COL_CAT_NAME TEXT UNIQUE)")

        val createTaskTable = ("CREATE TABLE $TABLE_TASKS (" +
                "$COL_TASK_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "$COL_TASK_NAME TEXT UNIQUE, " +
                "$COL_TASK_CAT_ID INTEGER, " +
                "FOREIGN KEY($COL_TASK_CAT_ID) REFERENCES $TABLE_CATEGORIES($COL_CAT_ID))")

        db?.execSQL(createCategoryTable)
        db?.execSQL(createTaskTable)
        insertInitialCategories(db)
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        db?.execSQL("DROP TABLE IF EXISTS $TABLE_TASKS")
        db?.execSQL("DROP TABLE IF EXISTS $TABLE_CATEGORIES")
        onCreate(db)
    }

    private fun insertInitialCategories(db: SQLiteDatabase?) {
        val defaultCategories = arrayOf("Estudio", "Trabajo", "Descanso", "Ocio")
        for (cat in defaultCategories) {
            val values = ContentValues().apply { put(COL_CAT_NAME, cat) }
            db?.insert(TABLE_CATEGORIES, null, values)
        }
    }

    // --- HU-01: GESTIÓN DE CATEGORÍAS ---
    fun addCategory(name: String): Boolean {
        val db = this.writableDatabase
        val values = ContentValues().apply { put(COL_CAT_NAME, name) }
        return try {
            db.insertOrThrow(TABLE_CATEGORIES, null, values) != -1L
        } catch (e: Exception) {
            false // Retorna falso si el nombre ya existe (UNIQUE)
        }
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
        } catch (e: Exception) {
            false
        }
    }

    fun deleteCategory(categoryId: Int): Boolean {
        val db = this.writableDatabase
        // Verificar si tiene tareas asociadas
        val cursor = db.rawQuery("SELECT * FROM $TABLE_TASKS WHERE $COL_TASK_CAT_ID = ?", arrayOf(categoryId.toString()))
        if (cursor.count > 0) {
            cursor.close()
            return false // No se permite eliminar si tiene tareas
        }
        cursor.close()
        return db.delete(TABLE_CATEGORIES, "$COL_CAT_ID = ?", arrayOf(categoryId.toString())) > 0
    }

    // --- HU-02: GESTIÓN DE TAREAS ---
    fun addTask(name: String, categoryId: Int): Boolean {
        if (name.isBlank()) return false
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COL_TASK_NAME, name)
            put(COL_TASK_CAT_ID, categoryId)
        }
        return try {
            db.insertOrThrow(TABLE_TASKS, null, values) != -1L
        } catch (e: Exception) {
            false // Retorna falso si se repite el nombre
        }
    }

    fun getAllTasksWithCategory(): Cursor {
        val db = this.readableDatabase
        val query = "SELECT t.$COL_TASK_ID, t.$COL_TASK_NAME, c.$COL_CAT_NAME AS category_name, t.$COL_TASK_CAT_ID " +
                "FROM $TABLE_TASKS t INNER JOIN $TABLE_CATEGORIES c ON t.$COL_TASK_CAT_ID = c.$COL_CAT_ID"
        return db.rawQuery(query, null)
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
        } catch (e: Exception) {
            false
        }
    }

    fun deleteTask(taskId: Int): Boolean {
        val db = this.writableDatabase
        return db.delete(TABLE_TASKS, "$COL_TASK_ID = ?", arrayOf(taskId.toString())) > 0
    }
}