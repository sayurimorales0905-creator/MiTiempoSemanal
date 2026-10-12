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
                val success = dbHelper.addCategory(name)
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
            categoryList.add(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_CAT_NAME)))
        }
        cursor.close()

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, categoryList)
        lvCategories.adapter = adapter

        // Al hacer clic, permite editar o eliminar con confirmación
        lvCategories.setOnItemClickListener { _, _, position, _ ->
            val catId = categoryIds[position]
            val catName = categoryList[position]
            showCategoryOptionsDialog(catId, catName)
        }
    }

    private fun showCategoryOptionsDialog(id: Int, currentName: String) {
        val options = arrayOf("Editar", "Eliminar")
        AlertDialog.Builder(this)
            .setTitle("Opciones para: $currentName")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showEditDialog(id, currentName)
                    1 -> confirmDeleteCategory(id, currentName)
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