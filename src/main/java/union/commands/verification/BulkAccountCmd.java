package union.commands.verification;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import union.base.command.CooldownScope;
import union.base.command.SlashCommandEvent;
import union.base.waiter.EventWaiter;
import union.commands.CommandBase;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.constants.CmdCategory;
import union.objects.constants.Constants;

import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BulkAccountCmd extends CommandBase {

	private final EventWaiter waiter;
	private final Pattern steamRegex = Pattern.compile(".*?(STEAM_[0-5]:[01]:\\d+).*");

	public BulkAccountCmd(EventWaiter waiter) {
		this.name = "bulk-account";
		this.path = "bot.verification.bulk-account";
		this.category = CmdCategory.VERIFICATION;
		this.module = CmdModule.VERIFICATION;
		this.accessLevel = CmdAccessLevel.ADMIN;
		this.cooldown = 30;
		this.cooldownScope = CooldownScope.GLOBAL;
		this.waiter = waiter;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		event.replyEmbeds(
			bot.getEmbedUtil().getEmbed()
				.setDescription(lu.getText(event, path+".input_awaiting"))
				.appendDescription("\n```\nSTEAM_0:0:793164364\n/addcredits STEAM_0:0:728683733 134\nMEOW STEAM_0:0:793163264\n```")
				.build()
			).setEphemeral(false)
			.queue();

		waiter.waitForEvent(
			MessageReceivedEvent.class,
			(e) -> e.getChannel().getIdLong() == event.getChannel().getIdLong() && e.getAuthor().getIdLong() == event.getMember().getIdLong(),
			(msgEvent) -> {
				Message msg = msgEvent.getMessage();

				final Set<String> ids = new LinkedHashSet<>();
				if (!msg.getAttachments().isEmpty()) {
					// As attachment
					Message.Attachment attachment = msg.getAttachments().get(0);
					if (!attachment.getContentType().contains("text/plain") || !attachment.getFileExtension().equals("txt")) {
						editError(event, path+".file_type");
						return;
					}

					int scannedLines = 0;
					try {
						URL url = new URL(attachment.getUrl());
						Scanner scanner = new Scanner(url.openStream());

						while (scanner.hasNextLine()) {
							String line = scanner.nextLine();
							Matcher matcher = steamRegex.matcher(line);

							if (matcher.find()) {
								ids.add(matcher.group(1));

								if (ids.size() >= 60) break;
							}
							if (++scannedLines >= 100) break;
						}
					} catch (IOException e) {
						editErrorOther(event, "Failed to read file: "+e.getMessage());
						bot.getAppLogger().warn("Failed to parse attachmenet file", e);
					}
				} else {
					// As message content
					String content = msg.getContentRaw();
					if (content.isBlank()) {
						editError(event, path+".content_empty");
						return;
					}

					Matcher matcher = steamRegex.matcher(content);

					while (matcher.find()) {
						ids.add(matcher.group(1));

						if (ids.size() >= 60) break;
					}
				}
				if (ids.isEmpty()) {
					editError(event, path+".empty");
					return;
				}

				msgEvent.getMessage().delete().queue(null, ignoreRest); // Delete message from user

				EmbedBuilder builder = bot.getEmbedUtil().getEmbed(Constants.COLOR_WARNING)
					.setDescription(lu.getText(event, path+".start")
						.formatted(ids.size(), ids.size()) // size and estimated time in seconds
					);
				event.getHook().editOriginalEmbeds(builder.build()).queue();

				bot.getDBUtil().unionPlayers.startPlayerBulkFetcher(
					event.getHook(), event.getUserLocale(),
					msgEvent.getGuild().getIdLong(), builder, ids
				);
			},
			30,
			TimeUnit.SECONDS,
			() -> {
				event.getHook().editOriginalEmbeds(
					bot.getEmbedUtil().getEmbed(Constants.COLOR_WARNING)
						.setDescription(lu.getText(event, path+".input_expired"))
						.build()
					).queue();
			}
		);
	}

}
