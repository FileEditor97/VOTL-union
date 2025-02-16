package union.utils.level;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.jetbrains.annotations.NotNull;
import union.App;
import union.objects.ExpType;
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

	private static final HashSet<PlayerObject> updateQueue = new LinkedHashSet<>();

	private static final long hardCap = (long) Integer.MAX_VALUE*4L;

	private static final int maxRandomExperience = 5;
	private static final int maxGuaranteeMessageExperience = 10;
	private static final int maxGuaranteeVoiceExperience = 5;

	private final double A = 12;
	private final int B = 80;
	private final int C = 0;

	public static long getHardCap() {
		return hardCap;
	}

	// Min experience to reach this level
	public long getExperienceFromLevel(int level) {
		if (level<=0) return 0;
		return (long) Math.floor( A*Math.pow(level,2) + B*level + C );
	}

	// Max level reachable with this exp
	public int getLevelFromExperience(long exp) {
		double level = ( -B + Math.sqrt(Math.pow(B,2) - 4*A*(C-exp)) )/(2*A);
		return level<0 ? 0 : (int) Math.floor(level);
	}

	private final Set<ChannelType> allowedChannelTypes = Set.of(ChannelType.TEXT, ChannelType.VOICE, ChannelType.GUILD_PUBLIC_THREAD, ChannelType.STAGE);
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
		long categoryId = switch (event.getChannelType()) {
			case TEXT, VOICE, STAGE -> event.getGuildChannel().asStandardGuildChannel().getParentCategoryIdLong();
			case GUILD_PUBLIC_THREAD -> event.getChannel().asThreadChannel().getParentChannel()
				.asStandardGuildChannel().getParentCategoryIdLong();
			default -> 0;
		};
		if (categoryId != 0 && settings.isExemptChannel(categoryId)) {
			return;
		}

		// If in cache - skip, else give exp and add to it
		cache.get(asKey(event), (k)->{
			giveExperience(event.getMember(), RandomUtil.getInteger(maxRandomExperience)+maxGuaranteeMessageExperience, ExpType.TEXT);
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
		long categoryId = channel.getParentCategoryIdLong();
		if (categoryId != 0 && settings.isExemptChannel(categoryId)) {
			return;
		}

		giveExperience(member, RandomUtil.getInteger(maxRandomExperience)+maxGuaranteeVoiceExperience, ExpType.VOICE);
	}

	public void giveExperience(@NotNull Member member, int amount, ExpType expType) {
		giveExperience(member, bot.getDBUtil().levels.getPlayer(member.getGuild().getIdLong(), member.getIdLong()), amount, expType);
	}

	private void giveExperience(@NotNull Member member, @NotNull LevelManager.PlayerData player, int amount, ExpType expType) {
		int level = getLevelFromExperience(player.getExperience(expType));

		player.incrementExperienceBy(amount, expType);

		if (player.getExperience(expType) >= getHardCap()-(maxGuaranteeMessageExperience+maxRandomExperience) || player.getExperience(expType) < -1) {
			player.setExperience(getHardCap(), expType);
		}

		updateQueue.add(new PlayerObject(member)); // Add to update queue

		int newLevel = getLevelFromExperience(player.getExperience(expType));
		if (newLevel > level) {
			// message
			bot.getLogger().level.onLevelUp(member, newLevel, expType);
			// give role
			Set<Long> roleIds = bot.getDBUtil().levelRoles.getRoles(member.getGuild().getIdLong(), newLevel, expType);
			if (roleIds.isEmpty()) return;

			Set<Role> addRoles = new HashSet<>();
			roleIds.forEach(roleId -> {
				Role role = member.getGuild().getRoleById(roleId);
				if (role == null) return;
				addRoles.add(role);
			});

			member.getGuild().modifyMemberRoles(member, addRoles, null).reason("New level: "+newLevel).queue();
		}
	}

	public void removeExperience(@NotNull Member member, int amount, ExpType expType) {
		removeExperience(member, bot.getDBUtil().levels.getPlayer(member.getGuild().getIdLong(), member.getIdLong()), amount, expType);
	}

	private void removeExperience(@NotNull Member member, @NotNull LevelManager.PlayerData player, int amount, ExpType expType) {
		player.decreaseExperienceBy(amount, expType);

		if (player.getExperience(expType) < 0) {
			player.setExperience(0, expType);
		}

		updateQueue.add(new PlayerObject(member)); // Add to update queue
	}

	public void clearExperience(@NotNull Member member) {
		clearExperience(member, bot.getDBUtil().levels.getPlayer(member.getGuild().getIdLong(), member.getIdLong()));
	}

	private void clearExperience(@NotNull Member member, @NotNull LevelManager.PlayerData player) {
		player.clearExperience();

		updateQueue.add(new PlayerObject(member)); // Add to update queue
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
