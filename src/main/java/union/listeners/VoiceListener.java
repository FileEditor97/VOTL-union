package union.listeners;

import java.util.EnumSet;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.Nonnull;

import union.App;
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
	
	private final App bot;
	private final LocaleUtil lu;

	public VoiceListener(App bot) {
		this.bot = bot;
		this.lu = bot.getLocaleUtil();
	}

	public void onGuildVoiceUpdate(@Nonnull GuildVoiceUpdateEvent event) {
		String masterVoiceID = bot.getDBUtil().guildVoice.getChannel(event.getGuild().getId());
		AudioChannelUnion channelJoined = event.getChannelJoined();
		if (channelJoined != null && channelJoined.getId().equals(masterVoiceID)) {
			handleVoiceCreate(event.getGuild(), event.getMember());
		}

		AudioChannelUnion channelLeft = event.getChannelLeft();
		if (channelLeft != null && bot.getDBUtil().voice.existsChannel(channelLeft.getId()) && channelLeft.getMembers().isEmpty()) {
			channelLeft.delete().reason("Custom channel, empty").queueAfter(500, TimeUnit.MILLISECONDS, null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_CHANNEL));
			bot.getDBUtil().voice.remove(channelLeft.getId());
		}
	}

	private void handleVoiceCreate(Guild guild, Member member) {
		String guildId = guild.getId();
		String userId = member.getId();
		DiscordLocale guildLocale = guild.getLocale();

		if (bot.getDBUtil().voice.existsUser(userId)) {
			member.getUser().openPrivateChannel()
				.queue(channel -> channel.sendMessage(lu.getLocalized(guildLocale, "bot.voice.listener.cooldown")).queue());
			return;
		}
		String categoryId = bot.getDBUtil().guildVoice.getCategory(guildId);
		if (categoryId == null) return;

		String channelName = Optional.ofNullable(bot.getDBUtil().user.getName(userId))
			.or(() -> Optional.ofNullable(bot.getDBUtil().guildVoice.getName(guildId)))
			.orElse(lu.getLocalized(guildLocale, "bot.voice.listener.default_name"))
			.replace("{user}", member.getEffectiveName());
		channelName = channelName.substring(0, Math.min(100, channelName.length()));

		Integer channelLimit = Optional.ofNullable(bot.getDBUtil().user.getLimit(userId))
			.or(() -> Optional.ofNullable(bot.getDBUtil().guildVoice.getLimit(guildId)))
			.orElse(0);
		
		guild.createVoiceChannel(channelName, guild.getCategoryById(categoryId))
			.reason(member.getUser().getEffectiveName()+" private channel")
			.setUserlimit(channelLimit)
			.syncPermissionOverrides()
			.addPermissionOverride(member, EnumSet.of(Permission.MANAGE_CHANNEL), null)
			.queue(
				channel -> {
					bot.getDBUtil().voice.add(userId, channel.getId());
					guild.moveVoiceMember(member, channel).queueAfter(500, TimeUnit.MICROSECONDS);
				}
			);
	}
}
