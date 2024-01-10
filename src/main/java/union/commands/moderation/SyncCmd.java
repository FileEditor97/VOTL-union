package union.commands.moderation;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import union.App;
import union.base.command.CooldownScope;
import union.base.command.SlashCommand;
import union.base.command.SlashCommandEvent;
import union.base.waiter.EventWaiter;
import union.commands.CommandBase;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.constants.CmdCategory;
import union.objects.constants.Constants;
import union.utils.database.managers.CaseManager.CaseData;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;

public class SyncCmd extends CommandBase {

	private static EventWaiter waiter;
	
	public SyncCmd(App bot, EventWaiter waiter) {
		super(bot);
		this.name = "sync";
		this.path = "bot.moderation.sync";
		this.children = new SlashCommand[]{new Ban(bot), new Unban(bot), new Kick(bot)};
		this.botPermissions = new Permission[]{Permission.KICK_MEMBERS, Permission.BAN_MEMBERS};
		this.category = CmdCategory.MODERATION;
		this.module = CmdModule.MODERATION;
		this.accessLevel = CmdAccessLevel.OPERATOR;
		SyncCmd.waiter = waiter;
	}

	@Override
	protected void execute(SlashCommandEvent event) {}

	private class Ban extends SlashCommand {

		public Ban(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "ban";
			this.path = "bot.moderation.sync.ban";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "id", lu.getText(path+".id.help"), true).setMinValue(1),
				new OptionData(OptionType.INTEGER, "group", lu.getText(path+".group.help"), true, true).setMinValue(1)
			);
			this.cooldownScope = CooldownScope.GUILD;
			this.cooldown = 20;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();
			CaseData banData = bot.getDBUtil().cases.getInfo(event.optInteger("id"));
			if (banData == null || event.getGuild().getIdLong() != banData.getGuildId()) {
				editError(event, path+".not_found");
				return;
			}
			if (!banData.isActive() || !banData.getDuration().isZero()) {
				editError(event, path+".expirable_ban");
				return;
			}
			
			Integer groupId = event.optInteger("group");
			String masterId = bot.getDBUtil().group.getMaster(groupId);
			if (masterId == null || !masterId.equals(event.getGuild().getId())) {
				editError(event, path+".no_group", "Group ID: `"+groupId.toString()+"`");
				return;
			}

			EmbedBuilder builder = bot.getEmbedUtil().getEmbed(event);
			event.getGuild().retrieveBan(User.fromId(banData.getTargetId())).queue(ban -> {
				MessageEmbed embed = builder.setDescription(lu.getText(event, path+".embed_title")).build();
				ActionRow button = ActionRow.of(
					Button.of(ButtonStyle.PRIMARY, "button:confirm", lu.getText(event, path+".button_confirm"))
				);
				event.getHook().editOriginalEmbeds(embed).setComponents(button).queue(msg -> {
					waiter.waitForEvent(
						ButtonInteractionEvent.class,
						e -> msg.getId().equals(e.getMessageId()) && e.getComponentId().equals("button:confirm"),
						action -> {
							User target = ban.getUser();
							String reason = ban.getReason();

							List<String> guilds = bot.getDBUtil().group.getGroupGuildIds(groupId);
							if (guilds.isEmpty()) {
								editError(event, path+".no_guilds");
								return;
							};

							event.getHook().editOriginalEmbeds(builder.setColor(Constants.COLOR_SUCCESS).setDescription(lu.getText(event, path+".done")).build())
								.setComponents().queue();
							// Perform action using Helper bot
							Optional.ofNullable(bot.getHelper()).ifPresent(helper -> helper.runBan(groupId, event.getGuild(), target, reason));
						},
						20,
						TimeUnit.SECONDS,
						() -> {
							event.getHook().editOriginalComponents(ActionRow.of(Button.of(ButtonStyle.SECONDARY, "timedout", "Timed out").asDisabled())).queue();
						}
					);
				});
			},
			t -> {
				editError(event, "errors.unknown", t.getMessage());
			});
		}

	}

	private class Unban extends SlashCommand {

		public Unban(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "unban";
			this.path = "bot.moderation.sync.unban";
			this.options = List.of(
				new OptionData(OptionType.USER, "user", lu.getText(path+".user.help"), true),
				new OptionData(OptionType.INTEGER, "group", lu.getText(path+".group.help"), true, true).setMinValue(0)
			);
			this.cooldownScope = CooldownScope.GUILD;
			this.cooldown = 20;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();

			User target = event.optUser("user");
			if (target == null) {
				editError(event, path+".not_found");
				return;
			}
			if (event.getUser().equals(target) || event.getJDA().getSelfUser().equals(target)) {
				editError(event, path+".not_self");
				return;
			}

			Integer groupId = event.optInteger("group");
			String masterId = bot.getDBUtil().group.getMaster(groupId);
			if (masterId == null || !masterId.equals(event.getGuild().getId())) {
				editError(event, path+".no_group", "Group ID: `"+groupId.toString()+"`");
				return;
			}

			EmbedBuilder builder = bot.getEmbedUtil().getEmbed(event);

			MessageEmbed embed = builder.setDescription(lu.getText(event, path+".embed_title")).build();
			ActionRow button = ActionRow.of(
				Button.of(ButtonStyle.PRIMARY, "button:confirm", lu.getText(event, path+".button_confirm"))
			);
			event.getHook().editOriginalEmbeds(embed).setComponents(button).queue(msg -> {
				waiter.waitForEvent(
					ButtonInteractionEvent.class,
					e -> msg.getId().equals(e.getMessageId()) && e.getComponentId().equals("button:confirm"),
					action -> {
						List<String> guilds = bot.getDBUtil().group.getGroupGuildIds(groupId);
						if (guilds.isEmpty()) {
							editError(event, path+".no_guilds");
							return;
						};

						event.getHook().editOriginalEmbeds(builder.setColor(Constants.COLOR_SUCCESS).setDescription(lu.getText(event, path+".done")).build())
							.setComponents().queue();
						// Perform action using Helper bot
						Optional.ofNullable(bot.getHelper()).ifPresent(helper -> helper.runUnban(groupId, event.getGuild(), target, "Manual ban lift"));
					},
					20,
					TimeUnit.SECONDS,
					() -> {
						event.getHook().editOriginalComponents(ActionRow.of(Button.of(ButtonStyle.SECONDARY, "timed_out", "Timed out").asDisabled())).queue();
					}
				);
			});
		}

	}

	private class Kick extends SlashCommand {
		
		public Kick(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "kick";
			this.path = "bot.moderation.sync.kick";
			this.options = List.of(
				new OptionData(OptionType.USER, "user", lu.getText(path+".user.help"), true),
				new OptionData(OptionType.INTEGER, "group", lu.getText(path+".group.help"), true, true).setMinValue(0)
			);
			this.cooldownScope = CooldownScope.GUILD;
			this.cooldown = 20;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();

			User target = event.optUser("user");
			if (target == null) {
				editError(event, path+".not_found");
				return;
			}
			if (event.getUser().equals(target) || event.getJDA().getSelfUser().equals(target)) {
				editError(event, path+".not_self");
				return;
			}
			if (event.getGuild().isMember(target)) {
				editError(event, path+".is_member");
				return;
			}

			Integer groupId = event.optInteger("group");
			String masterId = bot.getDBUtil().group.getMaster(groupId);
			if (masterId == null || !masterId.equals(event.getGuild().getId())) {
				editError(event, path+".no_group", "Group ID: `"+groupId.toString()+"`");
				return;
			}

			EmbedBuilder builder = bot.getEmbedUtil().getEmbed(event);

			MessageEmbed embed = builder.setDescription(lu.getText(event, path+".embed_title")).build();
			ActionRow button = ActionRow.of(
				Button.of(ButtonStyle.PRIMARY, "button:confirm", lu.getText(event, path+".button_confirm"))
			);
			event.getHook().editOriginalEmbeds(embed).setComponents(button).queue(msg -> {
				waiter.waitForEvent(
					ButtonInteractionEvent.class,
					e -> msg.getId().equals(e.getMessageId()) && e.getComponentId().equals("button:confirm"),
					action -> {
						List<String> guilds = bot.getDBUtil().group.getGroupGuildIds(groupId);
						if (guilds.isEmpty()) {
							editError(event, path+".no_guilds");
							return;
						};

						event.getHook().editOriginalEmbeds(builder.setColor(Constants.COLOR_SUCCESS).setDescription(lu.getText(event, path+".done")).build())
							.setComponents().queue();
						// Perform action using Helper bot
						Optional.ofNullable(bot.getHelper()).ifPresent(helper -> helper.runKick(groupId, event.getGuild(), target, "Manual kick"));
					},
					20,
					TimeUnit.SECONDS,
					() -> {
						event.getHook().editOriginalComponents(ActionRow.of(Button.of(ButtonStyle.SECONDARY, "timed_out", "Timed out").asDisabled())).queue();
					}
				);
			});
		}
	}

}
