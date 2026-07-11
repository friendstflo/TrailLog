package org.mountaineers.traillog.ui.tools

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import org.mountaineers.traillog.R
import org.mountaineers.traillog.tools.ToolInventoryStore
import org.mountaineers.traillog.tools.ToolType

/**
 * Local tool checkout tracker — not synced to Firebase.
 */
class ToolsFragment : Fragment() {

    private lateinit var recycler: RecyclerView
    private lateinit var adapter: ToolsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_tools, container, false)

        ToolInventoryStore.load(requireContext())

        recycler = view.findViewById(R.id.recycler_tools)
        adapter = ToolsAdapter(
            onIncrement = { tool ->
                ToolInventoryStore.increment(requireContext(), tool)
            },
            onDecrement = { tool ->
                ToolInventoryStore.decrement(requireContext(), tool)
            }
        )
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        view.findViewById<MaterialButton>(R.id.btn_check_in_all).setOnClickListener {
            confirmCheckInAll()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                ToolInventoryStore.counts.collect { counts ->
                    adapter.submit(ToolType.all.map { tool ->
                        ToolRow(tool, counts[tool] ?: 0)
                    })
                }
            }
        }

        return view
    }

    private fun confirmCheckInAll() {
        val total = ToolInventoryStore.totalCheckedOut()
        if (total == 0) {
            Toast.makeText(requireContext(), R.string.tools_already_checked_in, Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.tools_check_in_all)
            .setMessage(getString(R.string.tools_check_in_confirm, total))
            .setPositiveButton(R.string.tools_check_in_all) { _, _ ->
                ToolInventoryStore.checkInAll(requireContext())
                Toast.makeText(requireContext(), R.string.tools_checked_in, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}

private data class ToolRow(val tool: ToolType, val count: Int)

private class ToolsAdapter(
    private val onIncrement: (ToolType) -> Unit,
    private val onDecrement: (ToolType) -> Unit
) : RecyclerView.Adapter<ToolsAdapter.VH>() {

    private var rows: List<ToolRow> = emptyList()

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.iv_tool_icon)
        val name: TextView = view.findViewById(R.id.tv_tool_name)
        val count: TextView = view.findViewById(R.id.tv_tool_count)
        val minus: ImageButton = view.findViewById(R.id.btn_minus)
        val plus: ImageButton = view.findViewById(R.id.btn_plus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_tool_row, parent, false)
        return VH(view)
    }

    override fun getItemCount(): Int = rows.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val row = rows[position]
        holder.icon.setImageResource(row.tool.iconRes)
        holder.icon.contentDescription = row.tool.displayName
        holder.name.text = row.tool.displayName
        holder.count.text = row.count.toString()
        // Keep − and + the same dark green; only dim the whole control when disabled
        holder.minus.isEnabled = row.count > 0
        holder.minus.alpha = if (row.count > 0) 1f else 0.4f
        holder.plus.alpha = 1f
        holder.minus.setOnClickListener { onDecrement(row.tool) }
        holder.plus.setOnClickListener { onIncrement(row.tool) }
    }

    fun submit(newRows: List<ToolRow>) {
        rows = newRows
        notifyDataSetChanged()
    }
}
