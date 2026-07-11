package org.mountaineers.traillog.tools

import org.mountaineers.traillog.R

/**
 * Trail maintenance tools tracked locally (no cloud sync).
 */
enum class ToolType(
    val displayName: String,
    val prefsKey: String,
    val iconRes: Int
) {
    SHOVEL("Shovels", "tool_shovels", R.drawable.ic_tool_shovel),
    PULASKI("Pulaskis", "tool_pulaskis", R.drawable.ic_tool_pulaski),
    MCLEOD("McLeods", "tool_mcleods", R.drawable.ic_tool_mcleod),
    GRUB_HOE("Grub Hoes", "tool_grub_hoes", R.drawable.ic_tool_grub_hoe),
    HAND_SAW("Hand Saws", "tool_hand_saws", R.drawable.ic_tool_hand_saw),
    LOPPERS("Loppers", "tool_loppers", R.drawable.ic_tool_loppers),
    PRUNERS("Pruners", "tool_pruners", R.drawable.ic_tool_pruners),
    RAKE("Rakes", "tool_rakes", R.drawable.ic_tool_rake),
    WEED_WHIP("Weed Whips", "tool_weed_whips", R.drawable.ic_tool_weed_whip);

    companion object {
        val all: List<ToolType> = entries.toList()
    }
}
