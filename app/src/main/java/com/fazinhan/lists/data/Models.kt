package com.fazinhan.lists.data

const val HOUR_MS = 60L * 60L * 1000L
const val DAY_MS = 24L * HOUR_MS
const val WEEK_MS = 7L * DAY_MS

/** How long a checked item lingers at the bottom of its list before moving to the archive. */
const val ARCHIVE_AFTER_MS = DAY_MS

/** How long an item may stay unchecked before it is flagged as overdue. */
const val OVERDUE_AFTER_MS = WEEK_MS

data class ListSummary(
    val id: Long,
    val title: String,
    val position: Int,
    val openCount: Int,
    val checkedCount: Int,
    val archivedCount: Int,
) {
    val totalCount: Int get() = openCount + checkedCount + archivedCount
}

data class Item(
    val id: Long,
    val listId: Long,
    val name: String,
    val position: Int,
    val checked: Boolean,
    val checkedAt: Long?,
    val archivedAt: Long?,
    val createdAt: Long,
) {
    fun isOverdue(now: Long): Boolean = !checked && now - createdAt >= OVERDUE_AFTER_MS
}

/** Items of one list, tagged with the list they were loaded for. */
data class ListItems(val listId: Long, val items: List<Item>)

data class HistoryEntry(
    val id: Long,
    val itemName: String,
    val listTitle: String,
    val createdAt: Long,
)

data class OverdueItem(val id: Long, val name: String, val listTitle: String)
