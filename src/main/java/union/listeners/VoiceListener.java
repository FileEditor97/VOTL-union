package union.listeners;

import java.util.EnumSet;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import union.App;
import union.objects.annotation.NotNull;
import union.utils.database.DBUtil;
import union.utils.message.LocaleUtil;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.requests.ErrorResponse;

public class VoiceListener extends ListenerAdapter {
	
	private final DBUtil dbUtil;
	private final LocaleUtil lu;

	public VoiceListener(App bot) {
		this.dbUtil = bot.getDBUtil();
		this.lu = bot.getLocaleUtil();
	}

	public void onGuildVoiceUpdate(@NotNull GuildVoiceUpdateEvent event) {
		Long masterVoiceID = dbUtil.guildVoice.getChannelId(event.getGuild().getIdLong());
		AudioChannelUnion channelJoined = event.getChannelJoined();
		if (channelJoined != null && channelJoined.getIdLong() == masterVoiceID) {
			handleVoiceCreate(event.getGuild(), event.getMember());
		}

		AudioChannelUnion channelLeft = event.getChannelLeft();
		if (channelLeft != null && dbUtil.voice.existsChannel(channelLeft.getIdLong()) && channelLeft.getMembers().isEmpty()) {
			channelLeft.delete().reason("Custom channel, empty").queueAfter(500, TimeUnit.MILLISECONDS, null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_CHANNEL));
			dbUtil.voice.remove(channelLeft.getIdLong());
		}
	}

	private void handleVoiceCreate(Guild guild, Member member) {
		long guildId = guild.getIdLong();
		long userId = member.getIdLong();
		DiscordLocale guildLocale = guild.getLocale();

		if (dbUtil.voice.existsUser(userId)) {
			member.getUser().openPrivateChannel()
				.queue(channel -> channel.sendMessage(lu.getLocalized(guildLocale, "bot.voice.listener.cooldown")).queue());
			return;
		}
		Long categoryId = dbUtil.guildVoice.getCategoryId(guildId);
		if (categoryId == null) return;

		String channelName = Optional.ofNullable(dbUtil.user.getName(userId))
			.or(() -> Optional.ofNullable(dbUtil.guildVoice.getName(guildId)))
			.orElse(lu.getLocalized(guildLocale, "bot.voice.listener.default_name"))
			.replace("{user}", member.getEffectiveName());
		channelName = channelName.substring(0, Math.min(100, channelName.length()));

		Integer channelLimit = Optional.ofNullable(dbUtil.user.getLimit(userId))
			.or(() -> Optional.ofNullable(dbUtil.guildVoice.getLimit(guildId)))
			.orElse(0);
		
		guild.createVoiceChannel(channelName, guild.getCategoryById(categoryId))
			.reason(member.getUser().getEffectiveName()+" private channel")
			.setUserlimit(channelLimit)
			.syncPermissionOverrides()
			.addPermissionOverride(member, EnumSet.of(Permission.MANAGE_CHANNEL), null)
			.queue(
				channel -> {
					dbUtil.voice.add(userId, channel.getId());
					guild.moveVoiceMember(member, channel).queueAfter(500, TimeUnit.MICROSECONDS);
				}
			);
	}
}
