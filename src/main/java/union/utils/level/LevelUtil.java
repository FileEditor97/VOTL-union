package union.utils.level;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
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
		.expireAfterWrite(30, TimeUnit.SECONDS)
		.build();

	private static final HashSet<PlayerObject> updateQueue = new LinkedHashSet<>();

	private static final long hardCap = Long.MAX_VALUE - 89;

	private static final int maxRandomExperience = 10;
	private static final int maxGuaranteeMessageExperience = 10;
	private static final int maxGuaranteeVoiceExperience = 5;

	private final double A = 1.75;
	private final int B = 20;
	private final int C = 100;

	public static long getHardCap() {
		return hardCap;
	}

	// Min experience to reach this level
	public long getExperienceFromLevel(int level) {
		if (level<=0) return 0;
		return (long) Math.pow(level, A) * B + C;
	}

	// Max level reachable with this exp
	public int getLevelFromExperience(long exp) {
		double level = Math.pow((exp-C)/ (double) B, 1.0/A);
		return level<0 ? 0 : (int) Math.floor(level);
	}

	private final Set<ChannelType> allowedChannelTypes = Set.of(ChannelType.TEXT, ChannelType.VOICE, ChannelType.CATEGORY, ChannelType.GUILD_PUBLIC_THREAD, ChannelType.STAGE);
	public void rewardMessagePlayer(@NotNull MessageReceivedEvent event) {
		// Check for length and if is allowed type
		if (event.isWebhookMessage() || event.getMessage().getContentRaw().length() < 10 || !allowedChannelTypes.contains(event.getChannel().getType())) {
			return;
		}

		LevelManager.LevelSettings settings = App.getInstance().getDBUtil().levels.getSettings(event.getGuild());
		// Check if enabled by settings and not exempt channel
		if (!settings.isEnabled() || settings.isExemptChannel(event.getChannel().getIdLong())) {
			return;
		}
		// Check if exempt category
		Long categoryId = Optional.ofNullable(event.getGuildChannel().asStandardGuildChannel().getParentCategory()).map(Category::getIdLong).orElse(null);
		if (categoryId != null && settings.isExemptChannel(categoryId)) {
			return;
		}

		// If in cache - skip, else give exp and add to it
		cache.get(asKey(event), (k)->{
			giveExperience(event.getMember(), RandomUtil.getInteger(maxRandomExperience)+maxGuaranteeMessageExperience);
			return true;
		});
	}

	public void rewardVoicePlayer(@NotNull Member member, @NotNull AudioChannelUnion channel) {
		LevelManager.LevelSettings settings = App.getInstance().getDBUtil().levels.getSettings(channel.getGuild());
		// Check if not exempt channel
		if (settings.isExemptChannel(channel.getIdLong())) {
			return;
		}
		// Check if exempt category
		Long categoryId = Optional.ofNullable(channel.getParentCategory()).map(Category::getIdLong).orElse(null);
		if (categoryId != null && settings.isExemptChannel(categoryId)) {
			return;
		}

		// If in cache - skip, else give exp and add to it
		cache.get(asKey(member.getGuild(), member), (k)->{
			giveExperience(member, RandomUtil.getInteger(maxRandomExperience)+maxGuaranteeVoiceExperience);
			return true;
		});
	}

	public void giveExperience(@NotNull Member member, int amount) {
		giveExperience(member, bot.getDBUtil().levels.getPlayer(member.getGuild().getIdLong(), member.getIdLong()), amount);
	}

	private void giveExperience(@NotNull Member member, @NotNull LevelManager.PlayerData player, int amount) {
		int level = getLevelFromExperience(player.getExperience());

		player.incrementExperienceBy(amount);

		if (player.getExperience() >= getHardCap()-(maxGuaranteeMessageExperience+maxRandomExperience) || player.getExperience() < -1) {
			player.setExperience(getHardCap());
		}

		System.out.println(updateQueue.add(new PlayerObject(member)));

		int newLevel = getLevelFromExperience(player.getExperience());
		if (newLevel > level) {
			// message
			bot.getLogger().level.onLevelUp(member, newLevel);
			// give role
			Set<Long> roleIds = bot.getDBUtil().levelRoles.getRoles(member.getGuild().getIdLong(), newLevel);
			if (roleIds.isEmpty()) return;

			Role role = member.getGuild().getRoleById(roleIds.stream().findFirst().orElseThrow());
			if (role == null) return;

			member.getGuild().addRoleToMember(member, role).reason("New level: "+newLevel).queue();
		}
	}

	public HashSet<PlayerObject> getUpdateQueue() {
		return updateQueue;
	}

	private String asKey(MessageReceivedEvent event) {
		return asKey(event.getGuild(), event.getAuthor());
	}

	private String asKey(Guild guild, UserSnowflake user) {
		return guild.getId()+":"+user.getId();
	}

}
