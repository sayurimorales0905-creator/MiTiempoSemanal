package com.senati.mitiemposemanal

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class CategoriesActivity : AppCompatActivity() {
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var lvCategories: ListView
    private lateinit var etCategoryName: EditText
    private lateinit var btnAddCategory: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_categories)

        dbHelper = DatabaseHelper(this)
        lvCategories = findViewById(R.id.lvCategories)
        etCategoryName = findViewById(R.id.etCategoryName)
        btnAddCategory = findViewById(R.id.btnAddCategory)

        loadCategoriesList()

        btnAddCategory.setOnClickListener {
            val name = etCategoryName.text.toString().trim()
            if (name.isNotEmpty()) {
                val success = dbHelper.addCategory(name, 5) // Meta por defecto de 5 horas
                if (success) {
                    etCategoryName.text.clear()
                    loadCategoriesList()
                    Toast.makeText(this, "Categoría creada con éxito", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Error: La categoría ya existe", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Ingrese un nombre válido", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadCategoriesList() {
        val cursor = dbHelper.getAllCategories()
        val categoryList = ArrayList<String>()
        val categoryIds = ArrayList<Int>()

        while (cursor.moveToNext()) {
            categoryIds.add(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_CAT_ID)))
            val name = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_CAT_NAME))
            val goal = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_CAT_GOAL))
            categoryList.add("$name · Meta: ${goal}h semanales")
        }
        cursor.close()

        val adapter = object : ArrayAdapter<String>(this, R.layout.item_row, R.id.tvItemTitle, categoryList) {}
        lvCategories.adapter = adapter

        lvCategories.setOnItemClickListener { _, _, position, _ ->
            val catId = categoryIds[position]
            val fullText = categoryList[position]
            val catName = fullText.split(" · ")[0]
            showCategoryOptionsDialog(catId, catName)
        }
    }

    private fun showCategoryOptionsDialog(id: Int, currentName: String) {
        val options = arrayOf("Editar Nombre", "Definir Meta Semanal (Horas)", "Eliminar")
        AlertDialog.Builder(this)
            .setTitle("Opciones para: $currentName")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showEditDialog(id, currentName)
                    1 -> showGoalDialog(id, currentName)
                    2 -> confirmDeleteCategory(id, currentName)
                }
            }.show()
    }

    private fun showEditDialog(id: Int, currentName: String) {
        val input = EditText(this)
        input.setText(currentName)
        AlertDialog.Builder(this)
            .setTitle("Editar Categoría")
            .setView(input)
            .setPositiveButton("Actualizar") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    if (dbHelper.updateCategory(id, newName)) {
                        loadCategoriesList()
                        Toast.makeText(this, "Categoría actualizada", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "El nombre ya existe", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showGoalDialog(id: Int, currentName: String) {
        val input = EditText(this)
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        input.hint = "Ej. 10 (horas semanales)"
        AlertDialog.Builder(this)
            .setTitle("Meta Semanal para: $currentName")
            .setView(input)
            .setPositiveButton("Guardar Meta") { _, _ ->
                val goalStr = input.text.toString().trim()
                if (goalStr.isNotEmpty()) {
                    val goalHours = goalStr.toIntOrNull() ?: 5
                    if (dbHelper.updateCategoryGoal(id, goalHours)) {
                        loadCategoriesList()
                        Toast.makeText(this, "Meta actualizada a ${goalHours}h", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Error al actualizar meta", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun confirmDeleteCategory(id: Int, name: String) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar Categoría")
            .setMessage("¿Estás segura de eliminar '$name'?")
            .setPositiveButton("Sí, eliminar") { _, _ ->
                val deleted = dbHelper.deleteCategory(id)
                if (deleted) {
                    loadCategoriesList()
                    Toast.makeText(this, "Categoría eliminada", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "No se puede eliminar: La categoría tiene tareas asociadas", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
