package union.commands.roles;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import union.base.command.SlashCommand;
import union.base.command.SlashCommandEvent;
import union.base.waiter.EventWaiter;
import union.commands.CommandBase;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.constants.CmdCategory;
import union.objects.constants.Constants;
import union.utils.exception.FormatterException;
import union.utils.message.TimeUtil;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.TimeFormat;

public class TempRoleCmd extends CommandBase {

	private final EventWaiter waiter;
	private final int MAX_DAYS = 400;
	
	public TempRoleCmd(EventWaiter waiter) {
		this.waiter = waiter;
		this.name = "temprole";
		this.path = "bot.roles.temprole";
		this.children = new SlashCommand[]{new Assign(), new Cancel(), new Extend(), new View()};
		this.category = CmdCategory.ROLES;
		this.module = CmdModule.ROLES;
		this.accessLevel = CmdAccessLevel.MOD;
	}

	@Override
	protected void execute(SlashCommandEvent event) {}

	private class Assign extends SlashCommand {
		public Assign() {
			this.name = "assign";
			this.path = "bot.roles.temprole.assign";
			this.options = List.of(
				new OptionData(OptionType.ROLE, "role", lu.getText(path+".role.help"), true),
				new OptionData(OptionType.USER, "user", lu.getText(path+".user.help"), true),
				new OptionData(OptionType.STRING, "duration", lu.getText(path+".duration.help"), true),
				new OptionData(OptionType.BOOLEAN, "delete", lu.getText(path+".delete.help"))
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply().queue();

			Guild guild = Objects.requireNonNull(event.getGuild());
			// Check role
			Role role = event.optRole("role");
			if (role == null) {
				editError(event, path+".no_role");
				return;
			}
			String denyReason = bot.getCheckUtil().denyRole(role, event.getGuild(), event.getMember(), true);
			if (denyReason != null) {
				editError(event, path+".incorrect_role", "Role: %s\n> %s".formatted(role.getAsMention(), denyReason));
				return;
			}
			// Check if role whitelisted
			if (bot.getDBUtil().getGuildSettings(guild).isRoleWhitelistEnabled()) {
				if (!bot.getDBUtil().role.existsRole(role.getIdLong())) {
					// Not whitelisted
					editError(event, path+".not_whitelisted", "Role: %s".formatted(role.getAsMention()));
					return;
				}
			}
			// Check member
			Member member = event.optMember("user");
			if (member == null) {
				editError(event, path+".no_member");
				return;
			}
			if (member.isOwner() || member.getUser().isBot()) {
				editError(event, path+".incorrect_user");
				return;
			}
			
			// Check if already added
			long roleId = role.getIdLong();
			long userId = member.getIdLong();
			if (bot.getDBUtil().tempRole.expireAt(roleId, userId) != null) {
				editError(event, path+".already_set");
				return;
			}

			// Check duration
			final Duration duration;
			try {
				duration = TimeUtil.stringToDuration(event.optString("duration"), false);
			} catch (FormatterException ex) {
				editError(event, ex.getPath());
				return;
			}
			if (duration.toMinutes() < 10 || duration.toDays() > MAX_DAYS) {
				editError(event, path+".time_limit", "Received: "+duration);
				return;
			}

			Instant until = Instant.now().plus(duration);
			if (event.optBoolean("delete", false)) {
				if (!event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
					editPermError(event, Permission.ADMINISTRATOR, false);
					return;
				}
				Button confirm = Button.danger("confirm-temp", lu.getText(event, path+".confirm"));
				event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getEmbed(Constants.COLOR_WARNING)
					.setTitle(lu.getText(event, path+".confirm_title"))
					.setDescription(lu.getText(event, path+".confirm_value").formatted(TimeUtil.formatTime(until, true)))
					.build())
					.setActionRow(confirm).queue(msg -> {
						waiter.waitForEvent(
							ButtonInteractionEvent.class,
							e -> (msg.getIdLong() == e.getMessageIdLong()) && e.getUser().equals(event.getUser()),
							actionEvent -> {
								guild.addRoleToMember(member, role).reason("Assigned temporary role | by %s".formatted(event.getMember().getEffectiveName())).queue(done -> {
									try {
										bot.getDBUtil().tempRole.add(guild.getIdLong(), roleId, userId, true, until);
									} catch (SQLException e) {
										editErrorDatabase(event, e, "temprole add");
										return;
									}
									// Log
									bot.getLogger().role.onTempRoleAdded(guild, event.getUser(), member.getUser(), role, duration, true);
									// Send reply
									editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
										.setDescription(lu.getText(event, path+".done").replace("{role}", role.getAsMention()).replace("{user}", member.getAsMention())
											.replace("{until}", TimeUtil.formatTime(until, true)))
										.build()
									);
								}, failure -> editErrorOther(event, failure.getMessage()));
							},
							20,
							TimeUnit.SECONDS,
							() -> msg.editMessageComponents(ActionRow.of(confirm.asDisabled().withLabel(lu.getText(event, "errors.timed_out")))).queue()
						);
					});
			} else {
				guild.addRoleToMember(member, role).reason("Assigned temporary role | by %s".formatted(event.getMember().getEffectiveName())).queue(done -> {
					try {
						bot.getDBUtil().tempRole.add(guild.getIdLong(), roleId, userId, false, until);
					} catch (SQLException e) {
						editErrorDatabase(event, e, "temprole add");
						return;
					}
					// Log
					bot.getLogger().role.onTempRoleAdded(guild, event.getUser(), member.getUser(), role, duration, false);
					// Send reply
					editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
						.setDescription(lu.getText(event, path+".done").replace("{role}", role.getAsMention()).replace("{user}", member.getAsMention())
							.replace("{until}", TimeUtil.formatTime(until, true)))
						.build()
					);
				}, failure -> editErrorOther(event, failure.getMessage()));
			}
		}
	}

	private class Cancel extends SlashCommand {
		public Cancel() {
			this.name = "cancel";
			this.path = "bot.roles.temprole.cancel";
			this.options = List.of(
				new OptionData(OptionType.ROLE, "role", lu.getText(path+".role.help"), true),
				new OptionData(OptionType.USER, "user", lu.getText(path+".user.help"), true)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply().queue();
			// Check role
			Role role = event.optRole("role");
			if (role == null) {
				editError(event, path+".no_role");
				return;
			}
			// Check member
			Member member = event.optMember("user");
			if (member == null) {
				editError(event, path+".no_member");
				return;
			}
			// Check time
			Instant time = bot.getDBUtil().tempRole.expireAt(role.getIdLong(), member.getIdLong());
			if (time == null) {
				editError(event, path+".not_found");
				return;
			}

			event.getGuild().removeRoleFromMember(member, role).reason("Canceled temporary role | by "+event.getMember().getEffectiveName()).queue();

			try {
				bot.getDBUtil().tempRole.remove(role.getIdLong(), member.getIdLong());
			} catch (SQLException e) {
				editErrorDatabase(event, e, "temprole remove");
				return;
			}
			// Log
			bot.getLogger().role.onTempRoleRemoved(event.getGuild(), event.getUser(), member.getUser(), role);
			// Send reply
			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done"))
				.build()
			);
		}
	}

	private class Extend extends SlashCommand {
		public Extend() {
			this.name = "extend";
			this.path = "bot.roles.temprole.extend";
			this.options = List.of(
				new OptionData(OptionType.ROLE, "role", lu.getText(path+".role.help"), true),
				new OptionData(OptionType.USER, "user", lu.getText(path+".user.help"), true),
				new OptionData(OptionType.STRING, "duration", lu.getText(path+".duration.help"), true)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply().queue();
			// Check role
			Role role = event.optRole("role");
			if (role == null) {
				editError(event, path+".no_role");
				return;
			}
			// Check member
			Member member = event.optMember("user");
			if (member == null) {
				editError(event, path+".no_member");
				return;
			}
			// Check time
			Instant previousTime = bot.getDBUtil().tempRole.expireAt(role.getIdLong(), member.getIdLong());
			if (previousTime == null) {
				editError(event, path+".not_found");
				return;
			}

			// Check duration
			final Duration duration;
			try {
				duration = TimeUtil.stringToDuration(event.optString("duration"), false);
			} catch (FormatterException ex) {
				editError(event, ex.getPath());
				return;
			}
			Instant until = previousTime.plus(duration);
			if (duration.toMinutes() < 10 || until.isAfter(Instant.now().plus(MAX_DAYS, ChronoUnit.DAYS))) {
				editError(event, path+".time_limit", "New duration: %s days".formatted(Duration.between(Instant.now(), until).toDays()));
				return;
			}

			try {
				bot.getDBUtil().tempRole.updateTime(role.getIdLong(), member.getIdLong(), until);
			} catch (SQLException e) {
				editErrorDatabase(event, e, "temprole update");
				return;
			}
			// Log
			bot.getLogger().role.onTempRoleUpdated(event.getGuild(), event.getUser(), member.getUser(), role, until);
			// Send reply
			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").replace("{role}", role.getAsMention()).replace("{user}", member.getAsMention())
					.replace("{until}", TimeUtil.formatTime(until, true)))
				.build()
			);
		}
	}

	private class View extends SlashCommand {
		public View() {
			this.name = "view";
			this.path = "bot.roles.temprole.view";
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			event.deferReply(true).queue();

			Guild guild = event.getGuild();
			List<Map<String, Object>> list = bot.getDBUtil().tempRole.getAll(guild.getIdLong());
			if (list.isEmpty()) {
				editEmbed(event, bot.getEmbedUtil().getEmbed().setDescription(lu.getText(event, path+".empty")).build());
				return;
			}

			EmbedBuilder builder = bot.getEmbedUtil().getEmbed().setTitle(lu.getText(event, path+".title"));
			StringBuffer buffer = new StringBuffer();

			list.forEach(data -> {
				String line = getLine(data);
				if (buffer.length() + line.length() > 1024) {
					builder.addField("", buffer.toString(), false);
					buffer.setLength(0);
				}
				buffer.append(line);
			});
			builder.addField("", buffer.toString(), false);

			editEmbed(event, builder.build());
		}

		private String getLine(Map<String, Object> map) {
			Instant time = Instant.ofEpochSecond((Integer) map.get("expireAfter"));
			return "<@&%s> | <@%s> | %s\n".formatted(map.get("roleId"), map.get("userId"), TimeFormat.DATE_TIME_SHORT.format(time));
		}
	}

}
