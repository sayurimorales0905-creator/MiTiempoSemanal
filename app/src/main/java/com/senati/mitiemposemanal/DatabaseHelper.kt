package com.senati.mitiemposemanal

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "mi_tiempo_semanal.db"
        private const val DATABASE_VERSION = 1

        // Tabla Categorías
        const val TABLE_CATEGORIES = "categories"
        const val COL_CAT_ID = "id"
        const val COL_CAT_NAME = "name"

        // Tabla Tareas
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

        // Criterio HU-01: Al instalar la app ya existen 4 categorías por defecto
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
            val values = ContentValues().apply {
                put(COL_CAT_NAME, cat)
            }
            db?.insert(TABLE_CATEGORIES, null, values)
        }
    }

    // --- MÉTODOS PARA CATEGORÍAS (HU-01) ---
    fun addCategory(name: String): Boolean {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COL_CAT_NAME, name)
        }
        val result = db.insert(TABLE_CATEGORIES, null, values)
        return result != -1L // Retorna falso si el nombre ya existe (por el UNIQUE)
    }

    fun deleteCategory(categoryId: Int): Boolean {
        val db = this.writableDatabase
        // Verificar si la categoría tiene tareas asociadas
        val cursor = db.rawQuery("SELECT * FROM $TABLE_TASKS WHERE $COL_TASK_CAT_ID = ?", arrayOf(categoryId.toString()))
        if (cursor.count > 0) {
            cursor.close()
            return false // Criterio: Si la categoría tiene tareas, no se permite eliminarla
        }
        cursor.close()
        return db.delete(TABLE_CATEGORIES, "$COL_CAT_ID = ?", arrayOf(categoryId.toString())) > 0
    }

    // --- MÉTODOS PARA TAREAS (HU-02) ---
    fun addTask(name: String, categoryId: Int): Boolean {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COL_TASK_NAME, name)
            put(COL_TASK_CAT_ID, categoryId)
        }
        val result = db.insert(TABLE_TASKS, null, values)
        return result != -1L // Retorna falso si el nombre de la tarea se repite
    }
}