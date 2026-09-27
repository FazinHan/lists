package com.fazinhan.lists.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * Single access point to the SQLite store. Every write bumps [version], which re-runs
 * the queries behind all observed flows.
 */
class Repository private constructor(context: Context) {

    private val helper = ListsDatabase(context)
    private val db: SQLiteDatabase get() = helper.writableDatabase
    private val version = MutableStateFlow(0L)

    private fun <T> observe(query: (SQLiteDatabase) -> T): Flow<T> =
        version.map { query(db) }.flowOn(Dispatchers.IO).conflate()

    private suspend fun <T> write(block: (SQLiteDatabase) -> T): T = withContext(Dispatchers.IO) {
        val db = db
        db.beginTransaction()
        try {
            block(db).also { db.setTransactionSuccessful() }
        } finally {
            db.endTransaction()
        }
    }.also { version.update { it + 1 } }

    // region Lists

    fun lists(): Flow<List<ListSummary>> = observe { db ->
        db.rawQuery(
            """
            SELECT l.id, l.title, l.position,
                SUM(CASE WHEN i.id IS NOT NULL AND i.archived_at IS NULL AND i.checked = 0 THEN 1 ELSE 0 END),
                SUM(CASE WHEN i.id IS NOT NULL AND i.archived_at IS NULL AND i.checked = 1 THEN 1 ELSE 0 END),
                SUM(CASE WHEN i.id IS NOT NULL AND i.archived_at IS NOT NULL THEN 1 ELSE 0 END)
            FROM lists l LEFT JOIN items i ON i.list_id = l.id
            GROUP BY l.id
            ORDER BY l.position, l.id
            """,
            null,
        ).readAll {
            ListSummary(
                id = getLong(0),
                title = getString(1),
                position = getInt(2),
                openCount = getInt(3),
                checkedCount = getInt(4),
                archivedCount = getInt(5),
            )
        }
    }

    suspend fun addList(title: String): Long = write { db ->
        db.insertOrThrow("lists", null, ContentValues().apply {
            put("title", title)
            put("position", db.nextPosition("SELECT MAX(position) FROM lists", emptyArray()))
            put("created_at", System.currentTimeMillis())
        })
    }

    suspend fun renameList(id: Long, title: String) = write { db ->
        db.update("lists", ContentValues().apply { put("title", title) }, "id = ?", arrayOf("$id"))
    }

    /** Deletes the list together with all of its items, archived ones included. */
    suspend fun deleteList(id: Long) = write { db ->
        db.delete("lists", "id = ?", arrayOf("$id"))
    }

    suspend fun reorderLists(orderedIds: List<Long>) = write { db ->
        db.applyOrder("lists", orderedIds)
    }

    // endregion

    // region Items

    fun items(listId: Long): Flow<ListItems> = observe { db ->
        val items = db.rawQuery(
            "SELECT $ITEM_COLUMNS FROM items WHERE list_id = ? AND archived_at IS NULL ORDER BY position, id",
            arrayOf("$listId"),
        ).readAll { toItem() }
        ListItems(listId, items)
    }

    fun archivedItems(listId: Long): Flow<List<Item>> = observe { db ->
        db.rawQuery(
            "SELECT $ITEM_COLUMNS FROM items WHERE list_id = ? AND archived_at IS NOT NULL ORDER BY checked_at DESC, id DESC",
            arrayOf("$listId"),
        ).readAll { toItem() }
    }

    suspend fun addItem(listId: Long, name: String) = write { db ->
        val now = System.currentTimeMillis()
        db.insertOrThrow("items", null, ContentValues().apply {
            put("list_id", listId)
            put("name", name)
            put("position", db.nextPosition("SELECT MAX(position) FROM items WHERE list_id = ?", arrayOf("$listId")))
            put("created_at", now)
        })
        val listTitle = db.rawQuery("SELECT title FROM lists WHERE id = ?", arrayOf("$listId"))
            .use { if (it.moveToFirst()) it.getString(0) else "" }
        db.insertOrThrow("history", null, ContentValues().apply {
            put("item_name", name)
            put("list_id", listId)
            put("list_title", listTitle)
            put("created_at", now)
        })
    }

    suspend fun setChecked(id: Long, checked: Boolean) = write { db ->
        db.update("items", ContentValues().apply {
            put("checked", if (checked) 1 else 0)
            if (checked) put("checked_at", System.currentTimeMillis()) else putNull("checked_at")
        }, "id = ?", arrayOf("$id"))
    }

    suspend fun deleteItem(id: Long) = write { db ->
        db.delete("items", "id = ?", arrayOf("$id"))
    }

    suspend fun reorderItems(orderedIds: List<Long>) = write { db ->
        db.applyOrder("items", orderedIds)
    }

    // endregion

    fun history(): Flow<List<HistoryEntry>> = observe { db ->
        db.rawQuery(
            """
            SELECT h.id, h.item_name, COALESCE(l.title, h.list_title), h.created_at
            FROM history h LEFT JOIN lists l ON l.id = h.list_id
            ORDER BY h.created_at DESC, h.id DESC
            """,
            null,
        ).readAll { HistoryEntry(getLong(0), getString(1), getString(2), getLong(3)) }
    }

    /** Moves items checked more than [ARCHIVE_AFTER_MS] ago into the archive. */
    suspend fun archiveCheckedItems(now: Long): Int = write { db ->
        db.update(
            "items",
            ContentValues().apply { put("archived_at", now) },
            "checked = 1 AND archived_at IS NULL AND checked_at <= ?",
            arrayOf("${now - ARCHIVE_AFTER_MS}"),
        )
    }

    /** Returns overdue items that have not been notified yet and marks them as notified. */
    suspend fun claimOverdueItems(now: Long): List<OverdueItem> = write { db ->
        val overdue = db.rawQuery(
            """
            SELECT i.id, i.name, l.title FROM items i JOIN lists l ON l.id = i.list_id
            WHERE i.checked = 0 AND i.archived_at IS NULL AND i.overdue_notified = 0 AND i.created_at <= ?
            """,
            arrayOf("${now - OVERDUE_AFTER_MS}"),
        ).readAll { OverdueItem(getLong(0), getString(1), getString(2)) }
        overdue.forEach {
            db.update("items", ContentValues().apply { put("overdue_notified", 1) }, "id = ?", arrayOf("${it.id}"))
        }
        overdue
    }

    private fun SQLiteDatabase.nextPosition(sql: String, args: Array<String>): Int =
        rawQuery(sql, args).use { if (it.moveToFirst() && !it.isNull(0)) it.getInt(0) + 1 else 0 }

    private fun SQLiteDatabase.applyOrder(table: String, orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id ->
            update(table, ContentValues().apply { put("position", index) }, "id = ?", arrayOf("$id"))
        }
    }

    private fun Cursor.toItem() = Item(
        id = getLong(0),
        listId = getLong(1),
        name = getString(2),
        position = getInt(3),
        checked = getInt(4) != 0,
        checkedAt = if (isNull(5)) null else getLong(5),
        archivedAt = if (isNull(6)) null else getLong(6),
        createdAt = getLong(7),
    )

    private inline fun <T> Cursor.readAll(row: Cursor.() -> T): List<T> = use {
        buildList { while (moveToNext()) add(row()) }
    }

    companion object {
        private const val ITEM_COLUMNS = "id, list_id, name, position, checked, checked_at, archived_at, created_at"

        @Volatile private var instance: Repository? = null

        fun get(context: Context): Repository = instance ?: synchronized(this) {
            instance ?: Repository(context.applicationContext).also { instance = it }
        }
    }
}
