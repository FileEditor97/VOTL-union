package union.utils.level;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.jetbrains.annotations.NotNull;
import union.App;
import union.utils.RandomUtil;
import union.utils.database.managers.LevelManager;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class LevelUtil {

	private final App bot;
	public LevelUtil(final App bot) {
		this.bot = bot;
	}

	// Cache
	public static final Cache<String, Boolean> cache = Caffeine.newBuilder()
		.expireAfterWrite(60, TimeUnit.SECONDS)
		.build();

	private static final LinkedHashSet<PlayerObject> updateQueue = new LinkedHashSet<>();

	private static final long hardCap = Long.MAX_VALUE - 89;

	private static final int maxRandomExperience = 5;
	private static final int maxGuaranteeExperience = 10;

	private final double A = 1.75;
	private final int B = 30;
	private final int C = 150;

	public static long getHardCap() {
		return hardCap;
	}

	// Min experience to reach this level
	public long getExperienceFromLevel(long level) {
		return (long) Math.pow(level, A) * B + C;
	}

	// Max level reachable with this exp
	public long getLevelFromExperience(long exp) {
		double level = Math.pow((exp-C)/ (double) B, 1.0/A);
		return level<0 ? 0 : (long) Math.floor(level);
	}

	public void rewardPlayer(@NotNull MessageReceivedEvent event) {
		if (event.isWebhookMessage()) {
			return;
		}

		LevelManager.LevelSettings levelSettings = App.getInstance().getDBUtil().levels.getSettings(event.getGuild());
		if (!levelSettings.isEnabled() || levelSettings.isExemptChannel(event.getChannel().getIdLong())) {
			return;
		}

		// If in cache - skip, else give exp and add to it
		cache.get(asKey(event), (k)->{
			giveExperience(event.getMember(), RandomUtil.getInteger(maxRandomExperience)+maxGuaranteeExperience);
			return true;
		});
	}

	public void giveExperience(@NotNull Member member, int amount) {
		giveExperience(member, bot.getDBUtil().levels.getPlayer(member.getGuild().getIdLong(), member.getIdLong()), amount);
	}

	private void giveExperience(@NotNull Member member, @NotNull LevelManager.PlayerData player, int amount) {
		long lvl = getLevelFromExperience(player.getExperience());

		player.incrementExperienceBy(amount);

		boolean exclude = player.getExperience() >= getHardCap()-(maxGuaranteeExperience+maxRandomExperience) || player.getExperience() < -1;
		if (exclude) {
			player.setExperience(getHardCap());
		}

		updateQueue.add(new PlayerObject(member));

		long newLvl = getLevelFromExperience(player.getExperience());
		if (newLvl > lvl) {
			// message
			// TODO
			// give role
			Long roleId = bot.getDBUtil().levels.getSettings(member.getGuild()).getLevelRole(newLvl);
			if (roleId == null) return;

			Role role = member.getGuild().getRoleById(roleId);
			if (role == null) return;

			member.getGuild().addRoleToMember(member, role).reason("New level: "+newLvl).queue();
			bot.getLogger().level.onLevelUp(member, newLvl);
		}
	}

	public LinkedHashSet<PlayerObject> getUpdateQueue() {
		return updateQueue;
	}

	private String asKey(MessageReceivedEvent event) {
		return event.getGuild().getId()+":"+event.getAuthor().getId();
	}

}
