package union.listeners;

import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import union.App;
import union.objects.annotation.NotNull;
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

public class VoiceListener extends ListenerAdapter {
	
	private final DBUtil db;
	private final App bot;

	public VoiceListener(App bot) {
		this.db = bot.getDBUtil();
		this.bot = bot;
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
		// TODO log join/leave/switch
		Long masterVoiceId = db.guildVoice.getChannelId(event.getGuild().getIdLong());
		AudioChannelUnion channelJoined = event.getChannelJoined();
		if (channelJoined != null && masterVoiceId != null && channelJoined.getIdLong() == masterVoiceId) {
			handleVoiceCreate(event.getGuild(), event.getMember());
		}

		AudioChannelUnion channelLeft = event.getChannelLeft();
		if (channelLeft != null && db.voice.existsChannel(channelLeft.getIdLong()) && channelLeft.getMembers().isEmpty()) {
			channelLeft.delete().reason("Custom channel, empty").queueAfter(500, TimeUnit.MILLISECONDS, null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_CHANNEL));
			db.voice.remove(channelLeft.getIdLong());
		}
	}

	private void handleVoiceCreate(Guild guild, Member member) {
		long guildId = guild.getIdLong();
		long userId = member.getIdLong();
		DiscordLocale guildLocale = guild.getLocale();

		if (db.voice.existsUser(userId)) {
			member.getUser().openPrivateChannel()
				.queue(channel -> channel.sendMessage(bot.getLocaleUtil().getLocalized(guildLocale, "bot.voice.listener.cooldown")).queue());
			return;
		}
		Long categoryId = db.guildVoice.getCategoryId(guildId);
		if (categoryId == null) return;

		String channelName = Optional.ofNullable(db.user.getName(userId))
			.or(() -> Optional.ofNullable(db.guildVoice.getName(guildId)))
			.orElse(bot.getLocaleUtil().getLocalized(guildLocale, "bot.voice.listener.default_name"))
			.replace("{user}", member.getEffectiveName());
		channelName = channelName.substring(0, Math.min(100, channelName.length()));

		Integer channelLimit = Optional.ofNullable(db.user.getLimit(userId))
			.or(() -> Optional.ofNullable(db.guildVoice.getLimit(guildId)))
			.orElse(0);
		
		guild.createVoiceChannel(channelName, guild.getCategoryById(categoryId))
			.reason(member.getUser().getEffectiveName()+" private channel")
			.setUserlimit(channelLimit)
			.syncPermissionOverrides()
			.addPermissionOverride(member, EnumSet.of(Permission.MANAGE_CHANNEL), null)
			.queue(
				channel -> {
					db.voice.add(userId, channel.getIdLong());
					guild.moveVoiceMember(member, channel).queueAfter(500, TimeUnit.MICROSECONDS);
				}
			);
	}
}
