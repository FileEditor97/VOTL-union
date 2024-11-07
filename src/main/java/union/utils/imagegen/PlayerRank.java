package union.utils.imagegen;

import union.objects.annotation.Nullable;
import union.utils.database.managers.UnionPlayerManager.PlayerInfo;

import java.util.ArrayList;
import java.util.List;

public class PlayerRank {
	public static List<String> getEmojiFromPlayerData(List<PlayerInfo> playerData) {
		if (playerData == null || playerData.isEmpty()) return List.of();
		List<String> rankBadges = new ArrayList<>();
		for (PlayerInfo playerInfo : playerData) {
			if (!playerInfo.exists()) continue;
			String emoji = parseRankToEmoji(playerInfo.getRank());
			if (emoji == null) continue;
			if (!rankBadges.contains(emoji)) rankBadges.add(emoji);
		}
		return rankBadges;
	}

	@Nullable
	public static String parseRankToEmoji(String rank) {
		return switch (rank) {
			case "content_creator" -> "⭐";
			case "vip", "adminpay", "moderator_d",
				 "admin_d", "d_admin", "d_moderator" -> "\uD83D\uDC8E";
			case "eventmaster", "builder", "internevent",
				 "senior_event" -> "\uD83C\uDF8A";
			case "admin", "watchingrp", "intern",
				 "junior_admin", "senior_admin", "moderator",
				 "operator" -> "⚖️";
			case "gladmin", "sudocurator", "assistant",
				 "sudo_curator" -> "";
			case "specadmin", "curator", "manager" -> "\uD83D\uDC51";
			case "superadmin" -> "\uD83D\uDEE0️";
			default -> null;
		};
	}
}
