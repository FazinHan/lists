package com.fazinhan.lists.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class ListsDatabase(context: Context) :
    SQLiteOpenHelper(context, NAME, null, VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE lists (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                position INTEGER NOT NULL,
                created_at INTEGER NOT NULL
            )
            """
        )
        db.execSQL(
            """
            CREATE TABLE items (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                list_id INTEGER NOT NULL REFERENCES lists(id) ON DELETE CASCADE,
                name TEXT NOT NULL,
                position INTEGER NOT NULL,
                checked INTEGER NOT NULL DEFAULT 0,
                checked_at INTEGER,
                archived_at INTEGER,
                overdue_notified INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL
            )
            """
        )
        db.execSQL("CREATE INDEX items_list ON items(list_id, archived_at)")
        // History outlives the items and lists it records, so it keeps a snapshot of the list title.
        db.execSQL(
            """
            CREATE TABLE history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                item_name TEXT NOT NULL,
                list_id INTEGER,
                list_title TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
            """
        )
        seed(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    private fun seed(db: SQLiteDatabase) {
        val now = System.currentTimeMillis()
        val seeds = listOf(
            "Shopping" to listOf("Milk", "Eggs", "Bread", "Coffee beans", "Bananas"),
            "Movies" to listOf("Dune: Part Two", "Past Lives", "The Grand Budapest Hotel", "Spirited Away"),
            "Reading" to listOf("Project Hail Mary", "The Pragmatic Programmer", "Sapiens", "Klara and the Sun"),
        )
        seeds.forEachIndexed { listPos, (title, names) ->
            val listId = db.insertOrThrow("lists", null, ContentValues().apply {
                put("title", title)
                put("position", listPos)
                put("created_at", now)
            })
            names.forEachIndexed { pos, name ->
                db.insertOrThrow("items", null, ContentValues().apply {
                    put("list_id", listId)
                    put("name", name)
                    put("position", pos)
                    put("created_at", now)
                })
                db.insertOrThrow("history", null, ContentValues().apply {
                    put("item_name", name)
                    put("list_id", listId)
                    put("list_title", title)
                    put("created_at", now)
                })
            }
        }
    }

    companion object {
        const val NAME = "lists.db"
        const val VERSION = 1
    }
}
