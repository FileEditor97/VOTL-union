package union.commands.moderation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import union.base.command.CooldownScope;
import union.base.command.SlashCommandEvent;
import union.base.waiter.EventWaiter;
import union.commands.CommandBase;
import union.objects.CaseType;
import union.objects.CmdAccessLevel;
import union.objects.CmdModule;
import union.objects.constants.CmdCategory;
import union.objects.constants.Constants;
import union.utils.CaseProofUtil;
import union.utils.database.managers.CaseManager.CaseData;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.requests.ErrorResponse;
import union.utils.exception.AttachmentParseException;

public class KickCmd extends CommandBase {

	private final EventWaiter waiter;
	
	public KickCmd (EventWaiter waiter) {
		this.name = "kick";
		this.path = "bot.moderation.kick";
		this.options = List.of(
			new OptionData(OptionType.USER, "member", lu.getText(path+".member.help"), true),
			new OptionData(OptionType.STRING, "reason", lu.getText(path+".reason.help")).setMaxLength(400),
			new OptionData(OptionType.ATTACHMENT, "proof", lu.getText(path+".proof.help")),
			new OptionData(OptionType.BOOLEAN, "dm", lu.getText(path+".dm.help"))
		);
		this.botPermissions = new Permission[]{Permission.KICK_MEMBERS};
		this.category = CmdCategory.MODERATION;
		this.module = CmdModule.MODERATION;
		this.accessLevel = CmdAccessLevel.MOD;
		this.cooldown = 10;
		this.cooldownScope = CooldownScope.GUILD;
		this.waiter = waiter;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		event.deferReply().queue();
		Guild guild = Objects.requireNonNull(event.getGuild());

		Member tm = event.optMember("member");
		if (tm == null) {
			editError(event, path+".not_found");
			return;
		}
		if (event.getMember().equals(tm) || guild.getSelfMember().equals(tm)) {
			editError(event, path+".not_self");
			return;
		}

		Member mod = event.getMember();
		if (!guild.getSelfMember().canInteract(tm)) {
			editError(event, path+".kick_abort", "Bot can't interact with target member.");
			return;
		}
		if (bot.getCheckUtil().hasHigherAccess(tm, mod)) {
			editError(event, path+".higher_access");
			return;
		}
		if (!mod.canInteract(tm)) {
			editError(event, path+".kick_abort", "You can't interact with target member.");
			return;
		}

		// Get proof
		final CaseProofUtil.ProofData proofData;
		try {
			proofData = CaseProofUtil.getData(event);
		} catch (AttachmentParseException e) {
			editError(event, e.getPath(), e.getMessage());
			return;
		}

		String reason = event.optString("reason", lu.getLocalized(event.getGuildLocale(), path+".no_reason"));
		if (event.optBoolean("dm", true)) {
			tm.getUser().openPrivateChannel().queue(pm -> {
				MessageEmbed embed = bot.getModerationUtil().getDmEmbed(CaseType.KICK, guild, reason, null, mod.getUser(), false);
				if (embed == null) return;
				pm.sendMessageEmbeds(embed).queue(null, new ErrorHandler().ignore(ErrorResponse.CANNOT_SEND_TO_USER));
			});
		}

		tm.kick().reason(reason).queueAfter(2, TimeUnit.SECONDS, done -> {
			// add info to db
			CaseData kickData = bot.getDBUtil().cases.add(CaseType.KICK, tm.getIdLong(), tm.getUser().getName(), mod.getIdLong(), mod.getUser().getName(),
				guild.getIdLong(), reason, Instant.now(), null);
			if (kickData == null) {
				editErrorOther(event, "Failed to create action data.");
				return;
			}
			// log kick
			bot.getLogger().mod.onNewCase(guild, tm.getUser(), kickData, proofData).thenAccept(logUrl -> {
				// Add log url to db
				bot.getDBUtil().cases.setLogUrl(kickData.getRowId(), logUrl);
				// reply and ask for kick sync
				event.getHook().editOriginalEmbeds(
					bot.getModerationUtil().actionEmbed(guild.getLocale(), kickData.getLocalIdInt(),
						path+".success", tm.getUser(), mod.getUser(), reason, logUrl)
				).queue(msg -> {
					buttonSync(event, msg, tm.getUser(), reason);
				});
			});
		},
		failure -> editErrorOther(event, failure.getMessage()));
	}

	private void buttonSync(SlashCommandEvent event, final Message message, User tu, String reason) {
		if (!bot.getCheckUtil().hasAccess(event.getMember(), CmdAccessLevel.OPERATOR)) return;
		long guildId = event.getGuild().getIdLong();

		List<Integer> groupIds = new ArrayList<>();
		groupIds.addAll(bot.getDBUtil().group.getOwnedGroups(guildId));
		groupIds.addAll(bot.getDBUtil().group.getManagedGroups(guildId));
		if (groupIds.isEmpty()) return;

		EmbedBuilder builder = bot.getEmbedUtil().getEmbed()
			.setDescription(lu.getText(event, path+".sync.title"));
		StringSelectMenu menu = StringSelectMenu.create("groupId")
			.setPlaceholder(lu.getText(event, path+".sync.value"))
			.addOptions(groupIds.stream().map(groupId ->
				SelectOption.of(bot.getDBUtil().group.getName(groupId), groupId.toString()).withDescription("ID: "+groupId)
			).collect(Collectors.toList()))
			.setMaxValues(1)
			.build();

		message.replyEmbeds(builder.build()).setActionRow(menu).queue(msg -> {
			waiter.waitForEvent(
				StringSelectInteractionEvent.class,
				e -> e.getMessageId().equals(msg.getId()) && e.getUser().equals(event.getUser()),
				selectEvent -> {
					List<SelectOption> selected = selectEvent.getSelectedOptions();
					
					for (SelectOption option : selected) {
						int groupId = Integer.parseInt(option.getValue());
						Optional.ofNullable(bot.getHelper()).ifPresent(helper -> helper.runKick(groupId, event.getGuild(), tu, reason));
					}

					selectEvent.editMessageEmbeds(builder.setColor(Constants.COLOR_SUCCESS).setDescription(lu.getText(event, path+".sync.done")).build())
						.setComponents().queue();
				},
				15,
				TimeUnit.SECONDS,
				() -> msg.delete().queue()
			);
		});
	}
}
