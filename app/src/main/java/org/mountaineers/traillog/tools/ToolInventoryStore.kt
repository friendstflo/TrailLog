package org.mountaineers.traillog.tools

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Local-only tool checkout counts (SharedPreferences — not synced to Firebase).
 */
object ToolInventoryStore {

    private const val PREFS = "traillog_tools"

    private val _counts = MutableStateFlow<Map<ToolType, Int>>(emptyMap())
    val counts: StateFlow<Map<ToolType, Int>> = _counts.asStateFlow()

    fun load(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val map = ToolType.all.associateWith { tool ->
            prefs.getInt(tool.prefsKey, 0).coerceAtLeast(0)
        }
        _counts.value = map
    }

    fun get(tool: ToolType): Int = _counts.value[tool] ?: 0

    fun set(context: Context, tool: ToolType, count: Int) {
        val safe = count.coerceAtLeast(0)
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit { putInt(tool.prefsKey, safe) }
        _counts.value = _counts.value.toMutableMap().apply { put(tool, safe) }
    }

    fun increment(context: Context, tool: ToolType) {
        set(context, tool, get(tool) + 1)
    }

    fun decrement(context: Context, tool: ToolType) {
        set(context, tool, get(tool) - 1)
    }

    /** Check in all tools — reset every count to 0. */
    fun checkInAll(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit {
            ToolType.all.forEach { putInt(it.prefsKey, 0) }
        }
        _counts.value = ToolType.all.associateWith { 0 }
    }

    fun totalCheckedOut(): Int = _counts.value.values.sum()
}
