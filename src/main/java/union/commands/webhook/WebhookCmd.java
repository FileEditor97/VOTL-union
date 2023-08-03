package union.commands.webhook;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import union.App;
import union.commands.CommandBase;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.command.SlashCommand;
import union.objects.command.SlashCommandEvent;
import union.objects.constants.CmdCategory;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.entities.WebhookType;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.exceptions.PermissionException;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData;

public class WebhookCmd extends CommandBase {

	public WebhookCmd(App bot) {
		super(bot);
		this.name = "webhook";
		this.path = "bot.webhook";
		this.children = new SlashCommand[]{new ShowList(bot), new Create(bot), new Select(bot),
			new Remove(bot), new Move(bot)};
		this.botPermissions = new Permission[]{Permission.MANAGE_WEBHOOKS};
		this.category = CmdCategory.WEBHOOK;
		this.module = CmdModule.WEBHOOK;
		this.accessLevel = CmdAccessLevel.ADMIN;
	}

	@Override
	protected void execute(SlashCommandEvent event)	{

	}

	private class ShowList extends SlashCommand {

		public ShowList(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "list";
			this.path = "bot.webhook.list";
			this.options = List.of(
				new OptionData(OptionType.BOOLEAN, "all", lu.getText(path+".all.help"))
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();

			Guild guild = Objects.requireNonNull(event.getGuild());
			String guildId = guild.getId();
			DiscordLocale userLocale = event.getUserLocale();

			Boolean listAll = event.optBoolean("all", false);

			EmbedBuilder embedBuilder = bot.getEmbedUtil().getEmbed(event)
				.setTitle(lu.getLocalized(userLocale, path+".embed.title"));
			
			// Retrieves every webhook in server
			guild.retrieveWebhooks().queue(webhooks -> {
				// Remove FOLLOWER type webhooks
				webhooks = webhooks.stream().filter(wh -> wh.getType().equals(WebhookType.INCOMING)).collect(Collectors.toList());

				// If there is any webhook and only saved in DB are to be shown
				if (!listAll) {
					// Keeps only saved in DB type Webhook objects
					List<String> regWebhookIDs = bot.getDBUtil().webhook.getIds(guildId);
						
					webhooks = webhooks.stream().filter(wh -> regWebhookIDs.contains(wh.getId())).collect(Collectors.toList());
				}

				if (webhooks.isEmpty()) {
					embedBuilder.setDescription(
						lu.getLocalized(userLocale, (listAll ? path+".embed.none_found" : path+".embed.none_registered"))
					);
				} else {
					String title = lu.getLocalized(userLocale, path+".embed.value");
					StringBuilder text = new StringBuilder();
					for (Webhook wh : webhooks) {
						if (text.length() > 790) { // max characters for field value = 1024, and max for each line = ~226, so at least 4.5 lines fits in one field
							embedBuilder.addField(title, text.toString(), false);
							title = "\u200b";
							text.setLength(0);
						}
						text.append(String.format("%s | `%s` | %s\n", wh.getName(), wh.getId(), wh.getChannel().getAsMention()));
					}

					embedBuilder.addField(title, text.toString(), false);
				}

				editHookEmbed(event, embedBuilder.build());
			});
		}

	}

	private class Create extends SlashCommand {

		public Create(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "create";
			this.path = "bot.webhook.add.create";
			this.options = List.of(
				new OptionData(OptionType.STRING, "name", lu.getText(path+".name.help"), true),
				new OptionData(OptionType.CHANNEL, "channel", lu.getText(path+".channel.help"))
					.setChannelTypes(ChannelType.TEXT)
			);
			this.subcommandGroup = new SubcommandGroupData("add", lu.getText("bot.webhook.add.help"));
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Guild guild = Objects.requireNonNull(event.getGuild());

			String setName = event.optString("name", "Default name").trim();
			GuildChannel channel = event.optGuildChannel("channel", event.getGuildChannel());

			if (setName.isEmpty() || setName.length() > 100) {
				createError(event, path+".invalid_range");
				return;
			}

			try {
				// DYK, guildChannel doesn't have WebhookContainer! no shit
				guild.getTextChannelById(channel.getId()).createWebhook(setName).reason("By "+event.getUser().getName()).queue(
					webhook -> {
						bot.getDBUtil().webhook.add(webhook.getId(), webhook.getGuild().getId(), webhook.getToken());
						createReplyEmbed(event,
							bot.getEmbedUtil().getEmbed(event).setDescription(
								lu.getText(event, path+".done").replace("{webhook_name}", webhook.getName())
							).build()
						);
					}
				);
			} catch (PermissionException ex) {
				createPermError(event, ex.getPermission(), true);
				ex.printStackTrace();
			}
		}

	}

	private class Select extends SlashCommand {

		public Select(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "select";
			this.path = "bot.webhook.add.select";
			this.options = List.of(
				new OptionData(OptionType.STRING, "id", lu.getText(path+".id.help"), true)
			);
			this.subcommandGroup = new SubcommandGroupData("add", lu.getText("bot.webhook.add.help"));
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			String webhookId = event.optString("id", "0").trim();

			try {
				event.getJDA().retrieveWebhookById(Objects.requireNonNull(webhookId)).queue(
					webhook -> {
						if (bot.getDBUtil().webhook.exists(webhookId)) {
							createError(event, path+".error_registered");
						} else {
							bot.getDBUtil().webhook.add(webhook.getId(), webhook.getGuild().getId(), webhook.getToken());
							createReplyEmbed(event,
								bot.getEmbedUtil().getEmbed(event).setDescription(
									lu.getText(event, path+".done").replace("{webhook_name}", webhook.getName())
								).build()
							);
						}
					}, failure -> {
						createError(event, path+".error_not_found", failure.getMessage());
					}
				);
			} catch (IllegalArgumentException ex) {
				createError(event, path+".error_not_found", ex.getMessage());
			}
		}

	}

	private class Remove extends SlashCommand {

		public Remove(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "remove";
			this.path = "bot.webhook.remove";
			this.options = List.of(
				new OptionData(OptionType.STRING, "id", lu.getText(path+".id.help"), true),
				new OptionData(OptionType.BOOLEAN, "delete", lu.getText(path+".delete.help"))
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Guild guild = Objects.requireNonNull(event.getGuild());

			String webhookId = event.optString("id", "0").trim();
			Boolean delete = event.optBoolean("delete", false); 

			try {
				event.getJDA().retrieveWebhookById(webhookId).queue(
					webhook -> {
						if (!bot.getDBUtil().webhook.exists(webhookId)) {
							createError(event, path+".error_not_registered");
						} else {
							if (webhook.getGuild().equals(guild)) {
								if (delete) {
									webhook.delete(webhook.getToken()).reason("By "+event.getUser().getName()).queue();
								}
								bot.getDBUtil().webhook.remove(webhookId);
								createReplyEmbed(event,
									bot.getEmbedUtil().getEmbed(event).setDescription(
										lu.getText(event, path+".done").replace("{webhook_name}", webhook.getName())
									).build()
								);
							} else {
								createError(event, path+".error_not_guild", 
									String.format("Selected webhook guild: %s", webhook.getGuild().getName()));
							}
						}
					},
					failure -> {
						createError(event, path+".error_not_found", failure.getMessage());
					}
				);
			} catch (IllegalArgumentException ex) {
				createError(event, path+".error_not_found", ex.getMessage());
			}
		}

	}

	private class Move extends SlashCommand {

		public Move(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "move";
			this.path = "bot.webhook.move";
			this.options = List.of(
				new OptionData(OptionType.STRING, "id", lu.getText(path+".id.help"), true),
				new OptionData(OptionType.CHANNEL, "channel", lu.getText(path+".channel.help"), true)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Guild guild = Objects.requireNonNull(event.getGuild());

			String webhookId = event.optString("id", "0").trim();
			GuildChannel channel = event.optGuildChannel("channel");

			if (!channel.getType().equals(ChannelType.TEXT)) {
				createError(event, path+".error_channel", "Selected channel is not Text Channel");
				return;
			}

			event.getJDA().retrieveWebhookById(webhookId).queue(
				webhook -> {
					if (bot.getDBUtil().webhook.exists(webhookId)) {
						webhook.getManager().setChannel(guild.getTextChannelById(channel.getId())).reason("By "+event.getUser().getName()).queue(
							wm -> {
								createReplyEmbed(event,
									bot.getEmbedUtil().getEmbed(event).setDescription(
										lu.getText(event, path+".done")
											.replace("{webhook_name}", webhook.getName())
											.replace("{channel}", channel.getName())
									).build()
								);
							},
							failure -> {
								createError(event, "errors.unknown", failure.getMessage());
							}
						);
					} else {
						createError(event, path+".error_not_registered");
					}
				}, failure -> {
					createError(event, path+".error_not_found", failure.getMessage());
				}
			);
		}

	}
}
