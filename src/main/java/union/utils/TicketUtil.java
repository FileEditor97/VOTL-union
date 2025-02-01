package union.utils;

import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import net.dv8tion.jda.api.utils.FileUpload;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import union.App;
import union.objects.constants.Constants;
import union.utils.database.DBUtil;
import union.utils.database.managers.TicketSettingsManager;
import union.utils.transcripts.DiscordHtmlTranscripts;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.ErrorResponse;

public class TicketUtil {
	
	private final App bot;
	private final DBUtil db;

	public TicketUtil(App bot) {
		this.bot = bot;
		this.db = bot.getDBUtil();
	}

	public void closeTicket(long channelId, @Nullable User userClosed, @Nullable String reasonClosed, @NotNull Consumer<? super Throwable> failureHandler) {
		GuildMessageChannel channel = bot.JDA.getChannelById(GuildMessageChannel.class, channelId);
		if (channel == null) return; // already gone :(

		TicketSettingsManager.TranscriptsMode transcriptsMode = bot.getDBUtil().getTicketSettings(channel.getGuild()).getTranscriptsMode();
		if (db.ticket.isRoleTicket(channelId)) {
			// Role request ticket
			if (transcriptsMode.equals(TicketSettingsManager.TranscriptsMode.ALL)) {
				// With transcript
				DiscordHtmlTranscripts transcripts = DiscordHtmlTranscripts.getInstance();
				transcripts.queueCreateTranscript(channel,
					file -> {
						closeTicketRole(channel, userClosed, reasonClosed, failureHandler, file);
					},
					failureHandler
				);
			} else {
				// Without transcript
				closeTicketRole(channel, userClosed, reasonClosed, failureHandler, null);
			}
		} else {
			// Standard ticket
			if (transcriptsMode.equals(TicketSettingsManager.TranscriptsMode.NONE)) {
				// Without transcript
				closeTicketStandard(channel, userClosed, reasonClosed, failureHandler, null);
			} else {
				// With transcript
				DiscordHtmlTranscripts transcripts = DiscordHtmlTranscripts.getInstance();
				transcripts.queueCreateTranscript(channel,
					file -> {
						closeTicketStandard(channel, userClosed, reasonClosed, failureHandler, file);
					},
					failureHandler
				);
			}
		}
	}

	private void closeTicketRole(@NotNull GuildMessageChannel channel, @Nullable User userClosed, String reasonClosed, @NotNull Consumer<? super Throwable> failureHandler, @Nullable FileUpload file) {
		final Instant now = Instant.now();

		channel.delete().reason(reasonClosed).queueAfter(4, TimeUnit.SECONDS, done -> {
			db.ticket.closeTicket(now, channel.getIdLong(), reasonClosed);

			Long authorId = db.ticket.getUserId(channel.getIdLong());

			bot.getLogger().ticket.onClose(channel.getGuild(), channel, userClosed, authorId, file);
		}, failureHandler);
	}

	private void closeTicketStandard(@NotNull GuildMessageChannel channel, @Nullable User userClosed, String reasonClosed, @NotNull Consumer<? super Throwable> failureHandler, @Nullable FileUpload file) {
		final Instant now = Instant.now();
		final Guild guild = channel.getGuild();
		final String finalReason = reasonClosed==null ? "-" : (
			reasonClosed.equals("activity") || reasonClosed.equals("time")
				? bot.getLocaleUtil().getLocalized(guild.getLocale(), "logger.tickets.autoclosed")
				: reasonClosed
		);

		channel.delete().reason(finalReason).queueAfter(4, TimeUnit.SECONDS, done -> {
			db.ticket.closeTicket(now, channel.getIdLong(), finalReason);

			Long authorId = db.ticket.getUserId(channel.getIdLong());

			bot.JDA.retrieveUserById(authorId).queue(user -> {
				user.openPrivateChannel().queue(pm -> {
					MessageEmbed embed = bot.getLogEmbedUtil().ticketClosedPmEmbed(guild.getLocale(), channel, now, userClosed, finalReason);
					if (file == null) {
						pm.sendMessageEmbeds(embed).queue(null, new ErrorHandler().ignore(ErrorResponse.CANNOT_SEND_TO_USER));
					} else {
						pm.sendMessageEmbeds(embed).setFiles(file).queue(null, new ErrorHandler().ignore(ErrorResponse.CANNOT_SEND_TO_USER));
					}
				});
			});

			bot.getLogger().ticket.onClose(guild, channel, userClosed, authorId, file);
		}, failureHandler);
	}

	public void createTicket(ButtonInteractionEvent event, GuildMessageChannel channel, String mentions, String message) {
		channel.sendMessage(mentions).queue(msg -> {
			if (db.getTicketSettings(channel.getGuild()).deletePingsEnabled())
				msg.delete().queueAfter(5, TimeUnit.SECONDS, null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_CHANNEL));
		});

		MessageEmbed embed = new EmbedBuilder().setColor(db.getGuildSettings(event.getGuild()).getColor())
			.setDescription(message)
			.build();
		Button close = Button.danger("ticket:close", bot.getLocaleUtil().getLocalized(event.getGuildLocale(), "ticket.close")).withEmoji(Emoji.fromUnicode("🔒")).asDisabled();
		Button claim = Button.primary("ticket:claim", bot.getLocaleUtil().getLocalized(event.getGuildLocale(), "ticket.claim"));
		channel.sendMessageEmbeds(embed).setAllowedMentions(Collections.emptyList()).addActionRow(close, claim).queue(msg -> {
			msg.editMessageComponents(ActionRow.of(close.asEnabled(), claim)).queueAfter(15, TimeUnit.SECONDS, null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_CHANNEL));
		});

		// Send reply
		event.getHook().sendMessageEmbeds(new EmbedBuilder().setColor(Constants.COLOR_SUCCESS)
			.setDescription(bot.getLocaleUtil().getText(event, "bot.ticketing.listener.created").replace("{channel}", channel.getAsMention()))
			.build()
		).setEphemeral(true).queue();
		// Log
		bot.getLogger().ticket.onCreate(event.getGuild(), channel, event.getUser());
	}

}
