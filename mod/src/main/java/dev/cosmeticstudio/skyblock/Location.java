package dev.cosmeticstudio.skyblock;

import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.DisplaySlot;

/**
 * Works out where the player is on Hypixel by <b>reading</b> the sidebar scoreboard and tab list once a second.
 * Nothing is ever sent to the server (no /locraw, no Mod API packets).
 */
public final class Location {
	public enum Area { NONE, SKYBLOCK, DUNGEON, KUUDRA }

	private static Area area = Area.NONE;
	private static int ticks;

	private Location() {}

	public static Area area() {
		return area;
	}

	public static boolean inSkyBlock() {
		return area != Area.NONE;
	}

	/** Called every client tick; re-reads the scoreboard once per second. */
	public static void tick(Minecraft mc) {
		if (++ticks % 20 != 0) return;
		area = detect(mc);
	}

	static Area detect(Minecraft mc) {
		if (mc.level == null) return Area.NONE;
		Scoreboard sb = mc.level.getScoreboard();
		Objective sidebar = sb.getDisplayObjective(DisplaySlot.SIDEBAR);
		if (sidebar == null) return Area.NONE;
		String title = plain(sidebar.getDisplayName()).toUpperCase(Locale.ROOT);
		if (!title.contains("SKYBLOCK") && !title.contains("SKIBLOCK")) return Area.NONE;

		StringBuilder text = new StringBuilder();
		for (PlayerScoreEntry e : sb.listPlayerScores(sidebar)) {
			PlayerTeam team = sb.getPlayersTeam(e.owner());
			Component line = PlayerTeam.formatNameForTeam(team, e.display() != null ? e.display() : Component.literal(e.owner()));
			text.append(plain(line)).append('\n');
		}
		ClientPacketListener conn = mc.getConnection();
		if (conn != null) {
			for (PlayerInfo info : conn.getListedOnlinePlayers()) {
				Component name = info.getTabListDisplayName();
				if (name != null) text.append(plain(name)).append('\n');
			}
		}
		return classify(text.toString());
	}

	/** Pure function so it can be unit-tested. */
	public static Area classify(String scoreboardAndTab) {
		String s = scoreboardAndTab.toLowerCase(Locale.ROOT);
		if (s.contains("the catacombs") || s.contains("area: dungeon") || s.contains("dungeon: catacombs")) return Area.DUNGEON;
		if (s.contains("kuudra's hollow") || s.contains("area: kuudra")) return Area.KUUDRA;
		return Area.SKYBLOCK;
	}

	private static String plain(Component c) {
		String s = ChatFormatting.stripFormatting(c.getString());
		return s == null ? "" : s;
	}
}
