package union.listeners;

import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import org.jetbrains.annotations.NotNull;
import union.App;
import union.objects.logs.LogType;
import union.utils.database.DBUtil;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceGuildDeafenEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceGuildMuteEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.requests.ErrorResponse;
import union.utils.database.managers.GuildVoiceManager;
import union.utils.level.PlayerObject;

public class VoiceListener extends ListenerAdapter {

	// cache users in voice for exp
	// userId and start time
	private final Cache<PlayerObject, Long> cache = Caffeine.newBuilder()
		.expireAfterAccess(10, TimeUnit.MINUTES)
		.build();
	
	private final DBUtil db;
	private final App bot;

	public VoiceListener(App bot, ScheduledExecutorService executor) {
		this.db = bot.getDBUtil();
		this.bot = bot;
		startRewardTask(executor);
	}

	@Override
	public void onGuildVoiceGuildMute(@NotNull GuildVoiceGuildMuteEvent event) {
		if (!db.getLogSettings(event.getGuild()).enabled(LogType.VOICE)) return;

		event.getGuild().retrieveAuditLogs()
			.type(ActionType.MEMBER_UPDATE)
			.limit(1)
			.queue(list -> {
				if (!list.isEmpty()) {
					AuditLogEntry entry = list.get(0);
					if (entry.getChangeByKey("mute")!=null && entry.getTargetIdLong() == event.getMember().getIdLong() && entry.getTimeCreated().isAfter(OffsetDateTime.now().minusSeconds(15))) {
						bot.getLogger().voice.onVoiceMute(event.getMember(), event.isGuildMuted(), entry.getUserIdLong());
						return;
					}
				}
				bot.getLogger().voice.onVoiceMute(event.getMember(), event.isGuildMuted(), null);
			});
	}

	@Override
    public void onGuildVoiceGuildDeafen(@NotNull GuildVoiceGuildDeafenEvent event) {
		if (!db.getLogSettings(event.getGuild()).enabled(LogType.VOICE)) return;

		event.getGuild().retrieveAuditLogs()
			.type(ActionType.MEMBER_UPDATE)
			.limit(1)
			.queue(list -> {
				if (!list.isEmpty()) {
					AuditLogEntry entry = list.get(0);
					if (entry.getChangeByKey("deaf")!=null && entry.getTargetIdLong() == event.getMember().getIdLong() && entry.getTimeCreated().isAfter(OffsetDateTime.now().minusSeconds(15))) {
						bot.getLogger().voice.onVoiceDeafen(event.getMember(), event.isGuildDeafened(), entry.getUserIdLong());
						return;
					}
				}
				bot.getLogger().voice.onVoiceDeafen(event.getMember(), event.isGuildDeafened(), null);
			});
	}

	@Override
	public void onGuildVoiceUpdate(@NotNull GuildVoiceUpdateEvent event) {
		Long masterVoiceId = db.getVoiceSettings(event.getGuild()).getChannelId();
		AudioChannelUnion channelJoined = event.getChannelJoined();
		if (channelJoined != null && masterVoiceId != null && channelJoined.getIdLong() == masterVoiceId) {
			handleVoiceCreate(event.getGuild(), event.getMember());
		}

		AudioChannelUnion channelLeft = event.getChannelLeft();
		if (channelLeft != null && db.voice.existsChannel(channelLeft.getIdLong()) && channelLeft.getMembers().isEmpty()) {
			channelLeft.delete().reason("Custom channel, empty").queueAfter(500, TimeUnit.MILLISECONDS, null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_CHANNEL));
			db.voice.remove(channelLeft.getIdLong());
		}

		if (db.levels.getSettings(event.getGuild()).isVoiceEnabled()) {
			if (channelJoined != null && channelLeft == null) {
				// Joined vc first time
				cache.put(new PlayerObject(event.getGuild().getIdLong(), event.getMember().getIdLong()), System.currentTimeMillis());
			} else if (channelJoined == null && channelLeft != null) {
				// left voice
				handleUserLeave(event.getMember());
			}
		}
	}

	private void handleVoiceCreate(Guild guild, Member member) {
		long userId = member.getIdLong();
		DiscordLocale guildLocale = guild.getLocale();

		if (db.voice.existsUser(userId)) {
			member.getUser().openPrivateChannel()
				.queue(channel -> channel.sendMessage(bot.getLocaleUtil().getLocalized(guildLocale, "bot.voice.listener.cooldown")).queue(null, new ErrorHandler().ignore(ErrorResponse.CANNOT_SEND_TO_USER)));
			return;
		}
		GuildVoiceManager.VoiceSettings voiceSettings = db.getVoiceSettings(guild);
		Long categoryId = voiceSettings.getCategoryId();
		if (categoryId == null) return;

		String channelName = Optional.ofNullable(db.user.getName(userId))
			.or(() -> Optional.ofNullable(voiceSettings.getDefaultName()))
			.orElse(bot.getLocaleUtil().getLocalized(guildLocale, "bot.voice.listener.default_name"))
			.replace("{user}", member.getEffectiveName());
		channelName = channelName.substring(0, Math.min(100, channelName.length()));

		Integer channelLimit = Optional.ofNullable(db.user.getLimit(userId))
			.or(() -> Optional.ofNullable(voiceSettings.getDefaultLimit()))
			.orElse(0);
		
		guild.createVoiceChannel(channelName, guild.getCategoryById(categoryId))
			.reason(member.getUser().getEffectiveName()+" private channel")
			.setUserlimit(channelLimit)
			.syncPermissionOverrides()
			.addPermissionOverride(member, EnumSet.of(Permission.MANAGE_CHANNEL, Permission.VOICE_SET_STATUS, Permission.VOICE_MOVE_OTHERS), null)
			.queue(
				channel -> {
					db.voice.add(userId, channel.getIdLong());
					guild.moveVoiceMember(member, channel).queueAfter(500, TimeUnit.MICROSECONDS, null, new ErrorHandler().ignore(ErrorResponse.USER_NOT_CONNECTED));
				},
				failure -> {
					member.getUser().openPrivateChannel()
						.queue(channel ->
							channel.sendMessage(bot.getLocaleUtil().getLocalized(guildLocale, "bot.voice.listener.failed").formatted(failure.getMessage()))
								.queue(null, new ErrorHandler().ignore(ErrorResponse.CANNOT_SEND_TO_USER)));
				}
			);
	}

	private boolean inVoice(GuildVoiceState state) {
		return state != null && state.inAudioChannel();
	}

	private boolean isEligbleForRewards(GuildVoiceState state) {
		return !state.isMuted() && !state.isDeafened() &&
			!state.isSuppressed();
	}

	private void startRewardTask(ScheduledExecutorService executor) {
		executor.scheduleAtFixedRate(() -> {
			cache.asMap().forEach((player, joinTime) -> {
				Member member = Optional.ofNullable(bot.JDA.getGuildById(player.guildId))
					.map(g -> g.getMemberById(player.userId))
					.orElse(null);

				if (member == null) {
					handleUserLeave(player);
					return;
				}

				GuildVoiceState state = member.getVoiceState();
				if (inVoice(state)) {
					// In voice - check if not muted/deafened/AFK
					if (isEligbleForRewards(state)) {
						bot.getLevelUtil().rewardVoicePlayer(member, state.getChannel());
					}
				} else {
					// Not in voice
					handleUserLeave(player);
				}
			});
		}, 3, 2, TimeUnit.MINUTES);
	}

	private void handleUserLeave(Member member) {
		handleUserLeave(new PlayerObject(member));
	}

	private void handleUserLeave(PlayerObject player) {
		Long timeJoined = cache.getIfPresent(player);

		if (timeJoined != null) {
			long duration = Math.round((System.currentTimeMillis()-timeJoined)/1000f); // to seconds
			if (duration > 0) {
				bot.getDBUtil().levels.addVoiceTime(player, duration);
			}
		}
		cache.invalidate(player);
	}
}
