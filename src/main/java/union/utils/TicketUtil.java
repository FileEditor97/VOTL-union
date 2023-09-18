package union.utils;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import union.App;
import union.objects.constants.Constants;
import union.utils.database.DBUtil;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.utils.FileUpload;

import me.ryzeon.transcripts.DiscordHtmlTranscripts;

public class TicketUtil {
	
	private App bot;
	private DBUtil db;

	public TicketUtil(App bot) {
		this.bot = bot;
		this.db = bot.getDBUtil();
	}

	public void closeTicket(@Nonnull String channelId, @Nullable User userClosed, @Nullable String reasonClosed, @Nonnull Consumer<? super Throwable> closeHandle) {
		GuildMessageChannel channel = bot.JDA.getChannelById(GuildMessageChannel.class, channelId);
		Guild guild = channel.getGuild();
		Instant now = Instant.now();

		bot.JDA.retrieveUserById(db.ticket.getUserId(channelId)).queue(author -> {
			DiscordHtmlTranscripts transcripts = DiscordHtmlTranscripts.getInstance();
			try {
				FileUpload file = transcripts.createTranscript(channel);

				GuildChannel guildChannel = guild.getGuildChannelById(channelId);
				if (guildChannel == null) return;

				guildChannel.delete().reason(reasonClosed).queue(done -> {
					db.ticket.closeTicket(now, channelId, reasonClosed);

					author.openPrivateChannel().queue(pm -> {
						MessageEmbed embed = bot.getLogUtil().getTicketClosedPmEmbed(guild.getLocale(), channel, now, userClosed, reasonClosed);
						pm.sendMessageEmbeds(embed).setFiles(file).queue(null, new ErrorHandler().ignore(ErrorResponse.CANNOT_SEND_TO_USER));
					});

					String logChannelId = db.guild.getTicketLogChannel(guild.getId());
					if (logChannelId == null) return;
					TextChannel logChannel = guild.getJDA().getTextChannelById(logChannelId);
					if (logChannel == null) return;
					try {
						MessageEmbed embed = bot.getLogUtil().getTicketClosedEmbed(guild.getLocale(), channel, userClosed, author, db.ticket.getClaimer(channelId));
						logChannel.sendMessageEmbeds(embed).setFiles(file).queue();
					} catch (InsufficientPermissionException ex) {}
				}, failure -> {
					closeHandle.accept(failure);
				});
			} catch (IOException ex) {
				bot.getLogger().error("Error while closing ticket, file error", ex);
			}
			
		});
	}

	public void createTicket(ButtonInteractionEvent event, GuildMessageChannel channel, String mentions, String message) {
		channel.sendMessage(mentions).queue(msg -> msg.delete().queueAfter(5, TimeUnit.SECONDS, null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_CHANNEL)));

		MessageEmbed embed = new EmbedBuilder().setColor(db.guild.getColor(event.getGuild().getId()))
			.setDescription(message)
			.build();
		Button close = Button.danger("ticket:close", bot.getLocaleUtil().getLocalized(event.getGuildLocale(), "ticket.close")).withEmoji(Emoji.fromUnicode("🔒")).asDisabled();
		Button claim = Button.primary("ticket:claim", bot.getLocaleUtil().getLocalized(event.getGuildLocale(), "ticket.claim"));
		channel.sendMessageEmbeds(embed).setAllowedMentions(Collections.emptyList()).addActionRow(close, claim).queue(msg -> {
			msg.editMessageComponents(ActionRow.of(close.asEnabled(), claim)).queueAfter(15, TimeUnit.SECONDS);
		});

		event.getHook().editOriginalEmbeds(new EmbedBuilder().setColor(Constants.COLOR_SUCCESS)
			.setDescription(bot.getLocaleUtil().getText(event, "bot.ticketing.listener.created").replace("{channel}", channel.getAsMention()))
			.build()
		).setComponents().queue();
	}

}
