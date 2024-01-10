package union.commands.roles;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import union.App;
import union.base.command.SlashCommand;
import union.base.command.SlashCommandEvent;
import union.commands.CommandBase;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.constants.CmdCategory;
import union.objects.constants.Constants;
import union.utils.exception.FormatterException;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.TimeFormat;

public class TempRoleCmd extends CommandBase {

	private final int MAX_DAYS = 150;
	
	public TempRoleCmd(App bot) {
		super(bot);
		this.name = "temprole";
		this.path = "bot.roles.temprole";
		this.children = new SlashCommand[]{new Assign(bot), new Cancel(bot), new Extend(bot), new View(bot)};
		this.category = CmdCategory.ROLES;
		this.module = CmdModule.ROLES;
		this.accessLevel = CmdAccessLevel.MOD;
	}

	@Override
	protected void execute(SlashCommandEvent event) {}

	private class Assign extends SlashCommand {

		public Assign(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
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
			if (role.isPublicRole() || role.isManaged() || !guild.getSelfMember().canInteract(role) || role.hasPermission(Permission.ADMINISTRATOR)) {
				editError(event, path+".incorrect_role");
				return;
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
			String roleId = role.getId();
			String userId = member.getId();
			if (bot.getDBUtil().tempRole.expireAt(roleId, userId) != null) {
				editError(event, path+".already_set");
				return;
			}

			// Check duration
			final Duration duration;
			try {
				duration = bot.getTimeUtil().stringToDuration(event.optString("duration"), false);
			} catch (FormatterException ex) {
				editError(event, ex.getPath());
				return;
			}
			if (duration.toMinutes() < 10 || duration.toDays() > MAX_DAYS) {
				editError(event, path+".time_limit", "Received: "+duration.toString());
				return;
			}

			Boolean delete = event.optBoolean("delete", false);
			if (delete && !event.getMember().hasPermission(Permission.MANAGE_ROLES)) {
				editPermError(event, Permission.MANAGE_ROLES, false);
				return;
			}
			Instant until = Instant.now().plus(duration);

			guild.addRoleToMember(member, role).reason("Assigned temporary role | by %s".formatted(event.getMember().getEffectiveName())).queue(done -> {
				bot.getDBUtil().tempRole.add(guild.getId(), roleId, userId, delete, until);
				// Log
				bot.getLogListener().role.onTempRoleAdded(guild, event.getUser(), member.getUser(), role, duration);
				// Send reply
				editHookEmbed(event, bot.getEmbedUtil().getEmbed(event)
					.setColor(Constants.COLOR_SUCCESS)
					.setDescription(lu.getText(event, path+".done").replace("{role}", role.getAsMention()).replace("{user}", member.getAsMention())
						.replace("{until}", bot.getTimeUtil().formatTime(until, true)))
					.build()
				);
			}, failure -> {
				editError(event, "errors.unknown", failure.getMessage());
			});
		}

	}

	private class Cancel extends SlashCommand {

		public Cancel(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
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
			Instant time = bot.getDBUtil().tempRole.expireAt(role.getId(), member.getId());
			if (time == null) {
				editError(event, path+".not_found");
				return;
			}

			event.getGuild().removeRoleFromMember(member, role).reason("Canceled temporary role | by "+event.getMember().getEffectiveName()).queue();

			bot.getDBUtil().tempRole.remove(role.getId(), member.getId());
			// Log
			bot.getLogListener().role.onTempRoleRemoved(event.getGuild(), event.getUser(), member.getUser(), role);
			// Send reply
			editHookEmbed(event, bot.getEmbedUtil().getEmbed(event)
				.setColor(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done"))
				.build()
			);
		}

	}

	private class Extend extends SlashCommand {

		public Extend(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
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
			Instant previousTime = bot.getDBUtil().tempRole.expireAt(role.getId(), member.getId());
			if (previousTime == null) {
				editError(event, path+".not_found");
				return;
			}

			// Check duration
			final Duration duration;
			try {
				duration = bot.getTimeUtil().stringToDuration(event.optString("duration"), false);
			} catch (FormatterException ex) {
				editError(event, ex.getPath());
				return;
			}
			Instant until = previousTime.plus(duration);
			if (duration.toMinutes() < 10 || until.isAfter(Instant.now().plus(MAX_DAYS, ChronoUnit.DAYS))) {
				editError(event, path+".time_limit", "New duration: %s days".formatted(Duration.between(Instant.now(), until).toDays()));
				return;
			}

			bot.getDBUtil().tempRole.updateTime(role.getId(), member.getId(), until);
			// Log
			bot.getLogListener().role.onTempRoleUpdated(event.getGuild(), event.getUser(), member.getUser(), role, until);
			// Send reply
			editHookEmbed(event, bot.getEmbedUtil().getEmbed(event)
				.setColor(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, path+".done").replace("{role}", role.getAsMention()).replace("{user}", member.getAsMention())
					.replace("{until}", bot.getTimeUtil().formatTime(until, true)))
				.build()
			);
		}

	}

	private class View extends SlashCommand {

		public View(App bot) {
			this.bot = bot;
			this.lu = bot.getLocaleUtil();
			this.name = "view";
			this.path = "bot.roles.temprole.view";
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Guild guild = event.getGuild();
			List<Map<String, Object>> list = bot.getDBUtil().tempRole.getAll(guild.getId());
			if (list.isEmpty()) {
				createReplyEmbed(event, bot.getEmbedUtil().getEmbed(event).setDescription(lu.getText(event, path+".empty")).build());
				return;
			}

			event.deferReply(true).queue();
			EmbedBuilder builder = bot.getEmbedUtil().getEmbed(event).setTitle(lu.getText(event, path+".title"));
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

			editHookEmbed(event, builder.build());
		}

		private String getLine(Map<String, Object> map) {
			Instant time = Instant.ofEpochSecond((Integer) map.get("expireAfter"));
			return "<@&%s> | <@%s> | %s\n".formatted(map.get("roleId"), map.get("userId"), TimeFormat.DATE_TIME_SHORT.format(time));
		}

	}

}
