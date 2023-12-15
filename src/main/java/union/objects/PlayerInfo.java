package union.objects;

import java.util.Objects;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public class PlayerInfo {
	
		@Nonnull
		private final String steamId;
		@Nullable
		private String rank;
		@Nullable
		private Long playedHours; // in hours

		public PlayerInfo(String steamId) {
			this.steamId = steamId;
		}

		public PlayerInfo(String steamId, String rank, Long playTimeSeconds) {
			this.steamId = steamId;
			this.rank = rank;
			this.playedHours = Math.floorDiv(playTimeSeconds, 3600);
		}

		public void setInfo(String rank, Long playTimeSeconds) {
			this.rank = rank;
			this.playedHours = Math.floorDiv(playTimeSeconds, 3600);
		}

		public String getSteamId() {
			return steamId;
		}

		public String getRank() {
			return Objects.requireNonNullElse(rank, "-");
		}

		public Long getPlayTime() {
			return Objects.requireNonNullElse(playedHours, 0L);
		}
		
	}
