package union.listeners;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import union.App;
import union.objects.CmdAccessLevel;
import union.objects.constants.Constants;
import union.objects.constants.Links;
import union.utils.database.DBUtil;
import union.utils.message.LocaleUtil;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.requests.ErrorResponse;

import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
public class InteractionListener extends ListenerAdapter {

	private final App bot;
	private final LocaleUtil lu;
	private final DBUtil db;
	private final EventWaiter waiter;

	public InteractionListener(App bot, EventWaiter waiter) {
		// TODO: add timeout - 5 sec
		this.bot = bot;
		this.lu = bot.getLocaleUtil();
		this.db = bot.getDBUtil();
		this.waiter = waiter;
	}

	public void replyError(IReplyCallback event, String... text) {
		if (text.length > 1) {
			event.replyEmbeds(bot.getEmbedUtil().getError(event, text[0], text[1])).setEphemeral(true).queue();
		} else {
			event.replyEmbeds(bot.getEmbedUtil().getError(event, text[0])).setEphemeral(true).queue();
		}
	}
	
	
	@Override
	public void onButtonInteraction(@Nonnull ButtonInteractionEvent event) {
		String buttonId = event.getButton().getId();

		if (buttonId.startsWith("verify")) {
			Member member = event.getMember();
			Guild guild = event.getGuild();

			String roleId = db.verify.getVerifyRole(guild.getId());
			if (roleId == null) {
				replyError(event, "bot.verification.failed_role", "The verification role is not configured");
				return;
			}
			Role role = guild.getRoleById(roleId);
			if (role == null) {
				replyError(event, "bot.verification.failed_role", "Verification role not found");
				return;
			}
			if (member.getRoles().contains(role)) {
				replyError(event, "bot.verification.you_verified");
				return;
			}

			String steam64 = db.verifyRequest.getSteam64(member.getId());
			if (steam64 != null) {
				// Give verify role to user
				guild.addRoleToMember(member, role).reason("Verification completed - "+steam64).queue(
					success -> {
						event.deferEdit().queue(null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_INTERACTION));
						bot.getLogListener().onVerified(member.getUser(), steam64, guild);
						if (!bot.getDBUtil().verifyCache.isVerified(member.getId())) {
							bot.getDBUtil().verifyCache.addUser(member.getId(), steam64);
						}
					},
					failure -> {
						replyError(event, "bot.verification.failed_role");
						bot.getLogger().warn("Was unable to add verify role to user in "+guild.getName()+"("+guild.getId()+")", failure);
					});
			} else {
				event.deferReply(true).queue(null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_INTERACTION));
				String guildId = guild.getId();
				Button refresh = Button.of(ButtonStyle.DANGER, "verify-refresh", lu.getText(event, "bot.verification.listener.refresh"), Emoji.fromUnicode("🔁"));
				// Check if user pressed refresh button
				if (buttonId.endsWith("refresh")) {
					// Ask user to wait for 30 seconds each time
					event.getHook().editOriginalEmbeds(new EmbedBuilder().setColor(Constants.COLOR_FAILURE).setTitle(lu.getText(event, "bot.verification.listener.wait_title"))
						.setDescription(lu.getText(event, "bot.verification.listener.wait_value")).build()).queue();
					event.editButton(refresh.asDisabled()).queue(success -> event.editButton(refresh).queueAfter(30, TimeUnit.SECONDS));
					return;
				}
				// Reply with instruction on how to verify, buttons - link and refresh
				Button verify = Button.link(Links.UNIONTEAMS, lu.getText(event, "bot.verification.listener.connect"));
				EmbedBuilder builder = new EmbedBuilder().setColor(bot.getDBUtil().guild.getColor(guildId)).setTitle(lu.getText(event, "bot.verification.embed.title"))
					.setDescription(bot.getDBUtil().verify.getInstructionText(guildId))
					.addField(lu.getText(event, "bot.verification.embed.howto"), lu.getText(event, "bot.verification.embed.guide"), false);

				event.getHook().editOriginalEmbeds(builder.build()).setActionRow(verify, refresh).queue();
			}
		} else if (buttonId.equals("role_remove")) {
			event.deferReply(true).queue();
			Guild guild = event.getGuild();
			List<Role> currentRoles = event.getMember().getRoles();

			List<Role> allRoles = new ArrayList<Role>();
			db.role.getAssignable(guild.getId()).forEach(data -> allRoles.add(guild.getRoleById(data.get("roleId").toString())));
			db.role.getCustom(guild.getId()).forEach(data -> allRoles.add(guild.getRoleById(data.get("roleId").toString())));
			List<Role> roles = allRoles.stream().filter(role -> currentRoles.contains(role)).collect(Collectors.toList());
			if (roles.isEmpty()) {
				event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getError(event, "bot.ticketing.listener.no_assigned")).queue();
				return;
			}

			List<SelectOption> options = roles.stream().map(role -> SelectOption.of(role.getName(), role.getId())).toList();	
			StringSelectMenu menu = StringSelectMenu.create("menu:role_remove")
				.setPlaceholder(lu.getLocalized(event.getUserLocale(), "bot.ticketing.embeds.select"))
				.setMaxValues(options.size())
				.addOptions(options)
				.build();
			event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getEmbed().setDescription(lu.getText(event, "bot.ticketing.listener.remove_title")).build()).setActionRow(menu).queue(msg ->
				waiter.waitForEvent(
					StringSelectInteractionEvent.class,
					e -> e.getComponentId().equals("menu:role_remove"),
					actionEvent -> {
						List<Role> remove = actionEvent.getSelectedOptions().stream().map(option -> guild.getRoleById(option.getValue())).toList();
						guild.modifyMemberRoles(event.getMember(), null, remove).reason("User request").queue(done -> {
							event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getEmbed(event)
								.setDescription(lu.getText(event, "bot.ticketing.listener.remove_done").replace("{roles}", remove.stream().map(role -> role.getAsMention()).collect(Collectors.joining(", "))))
								.setColor(Constants.COLOR_SUCCESS)
								.build()
							).setComponents().queue();
						}, failure -> {
							event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getError(event, "bot.ticketing.listener.remove_failed", failure.getMessage())).setComponents().queue();
						});
					},
					40,
					TimeUnit.SECONDS,
					() -> event.getHook().editOriginalComponents(ActionRow.of(
						menu.createCopy().setPlaceholder(lu.getText(event, "bot.ticketing.listener.timed_out")).setDisabled(true).build())
					).queue()
				)
			);
		} else if (buttonId.startsWith("toggle")) {
			String roleId = buttonId.split(":")[1];
			Role role = event.getGuild().getRoleById(roleId);
			if (role == null || !db.role.isToggleable(roleId)) {
				replyError(event, "bot.ticketing.listener.toggle_failed", "Role not found or can't be toggled");
				return;
			}
			if (event.getMember().getRoles().contains(role)) {
				event.getGuild().removeRoleFromMember(event.getMember(), role).queue(done -> {
					event.replyEmbeds(bot.getEmbedUtil().getEmbed(event)
						.setDescription(lu.getText(event, "bot.ticketing.listener.toggle_removed").replace("{role}", role.getAsMention()))
						.setColor(Constants.COLOR_SUCCESS)
						.build()
					).setEphemeral(true).queue();
				}, failure -> {
					replyError(event, "bot.ticketing.listener.toggle_failed", failure.getMessage());
				});
			} else {
				event.getGuild().addRoleToMember(event.getMember(), role).queue(done -> {
					event.replyEmbeds(bot.getEmbedUtil().getEmbed(event)
						.setDescription(lu.getText(event, "bot.ticketing.listener.toggle_added").replace("{role}", role.getAsMention()))
						.setColor(Constants.COLOR_SUCCESS)
						.build()
					).setEphemeral(true).queue();
				}, failure -> {
					replyError(event, "bot.ticketing.listener.toggle_failed", failure.getMessage());
				});
			}
		} else if (buttonId.equals("ticket:close")) {
			String channelId = event.getChannel().getId();
			if (!db.ticket.isOpened(channelId)) {
				// Ticket is closed
				event.getChannel().asThreadChannel().delete().queue();
				return;
			}
			event.deferReply(true).queue();
			event.getChannel().asThreadChannel().delete().queue(done -> {
				db.ticket.closeTicket(Instant.now(), channelId);
				bot.getLogListener().onTicketClose(event.getMember(), event.getGuild(), event.getChannel(), db.ticket.getTicketId(channelId));
			}, failure -> {
				event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getError(event, "bot.ticketing.listener.close_failed")).queue();
				bot.getLogger().error("Couldn't close ticket with channelID:"+channelId, failure);
			});
		} else if (buttonId.equals("ticket:approve")) {
			if (!bot.getCheckUtil().hasAccess(event.getMember(), CmdAccessLevel.MOD)) {
				// User has no Mod access (OR admin, server owner, dev) to approve role request
				replyError(event, "errors.interaction.no_access");
				return;
			}
			String channelId = event.getChannel().getId();
			if (!db.ticket.isOpened(channelId)) {
				replyError(event, "bot.ticketing.listener.is_closed");
				return;
			}
			Guild guild = event.getGuild();
			String userId = db.ticket.getUserId(channelId);
			guild.retrieveMemberById(userId).queue(member -> {
				List<Role> roles = db.ticket.getRoleIds(channelId).stream().map(id -> guild.getRoleById(id)).filter(role -> role != null).toList();
				String ticketId = db.ticket.getTicketId(channelId);
				if (roles.isEmpty()) {
					event.replyEmbeds(bot.getEmbedUtil().getEmbed(event)
						.setDescription(lu.getText(event, "bot.ticketing.listener.role_none"))
						.setColor(Constants.COLOR_WARNING)
						.build()
					).setEphemeral(true).queue();
				} else {
					guild.modifyMemberRoles(member, roles, null).reason("Request role-"+ticketId+" approved by "+event.getMember().getEffectiveName()).queue(done -> {
						bot.getLogListener().onRolesApproved(member, event.getMember(), guild, roles, ticketId);
						db.ticket.setAccepted(event.getMember().getId(), channelId);
						event.replyEmbeds(bot.getEmbedUtil().getEmbed(event)
							.setDescription(lu.getLocalized(event.getGuildLocale(), "bot.ticketing.listener.role_added"))
							.setColor(Constants.COLOR_SUCCESS)
							.build()
						).queue(null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_INTERACTION));
						member.getUser().openPrivateChannel().queue(
							dm -> dm.sendMessage(bot.getLocaleUtil().getLocalized(guild.getLocale(), "bot.ticketing.listener.role_dm")
								.replace("{roles}", roles.stream().map(role -> role.getName()).collect(Collectors.joining(" | ")))
								.replace("{server}", guild.getName())
								.replace("{id}", ticketId)
								.replace("{mod}", event.getMember().getEffectiveName()))
								.queue(null, new ErrorHandler().ignore(ErrorResponse.CANNOT_SEND_TO_USER))
						);
					}, failure -> {
						replyError(event, "bot.ticketing.listener.role_failed", failure.getMessage());
					});
				}
			}, failure -> {
				replyError(event, "bot.ticketing.listener.no_member", failure.getMessage());
			});
		}
	}


	@Override
	public void onModalInteraction(@Nonnull ModalInteractionEvent event) {
		String modalId = event.getModalId();

		if (modalId.equals("vfpanel")) {
			if (event.getValues().size() == 0) {
				replyError(event, "errors.interaction.no_values");
				return;
			}
			String guildId = event.getGuild().getId();

			String main = event.getValue("main").getAsString();
			db.verify.setMainText(guildId, main.isEmpty() ? "NULL" : main);
			String description = event.getValue("description").getAsString();
			db.verify.setInstructionText(guildId, description.isEmpty() ? "NULL" : description);

			event.replyEmbeds(new EmbedBuilder().setColor(Constants.COLOR_SUCCESS)
				.setDescription(lu.getText(event, "bot.verification.vfpanel.text.done"))
				.build()
			).setEphemeral(true).queue();
		} else if (modalId.startsWith("ticket")) {
			// IS NOT USED
			String ticketType = modalId.split(":")[1];
			if (ticketType.equals("role_add")) {
				// TODO: change how role request are created, implement modal, to get some info from user, but also save requested roles
				event.deferEdit().queue();
			} else {
				event.deferEdit().queue();
			}
		}
	}

	@Override
	public void onStringSelectInteraction(@Nonnull StringSelectInteractionEvent event) {
		String menuId = event.getComponentId();
		Guild guild = event.getGuild();
		String guildId = guild.getId();

		if (menuId.equals("menu:role_add")) {
			// TODO: create modal to request user info
			// maybe use waiter to save information over
			List<Role> memberRoles = event.getMember().getRoles();
			List<String> options = event.getSelectedOptions().stream().map(option -> option.getValue()).toList();
			boolean otherRole = options.contains("other");
			List<Role> add = options.stream().filter(option -> !option.equals("other")).map(option -> guild.getRoleById(option))
				.filter(role -> role != null && !memberRoles.contains(role)).toList();
			
			if (!otherRole && add.isEmpty()) {
				replyError(event, "bot.ticketing.listener.role_none");
				return;
			}
			
			event.deferReply(true).queue();
			
			String channelId = db.ticket.getOpenedTicket(event.getMember().getId(), guildId);
			if (channelId != null) {
				ThreadChannel channel = guild.getThreadChannelById(channelId);
				if (channel != null) {
					event.getHook().editOriginalEmbeds(new EmbedBuilder().setColor(Constants.COLOR_FAILURE)
						.setDescription(lu.getText(event, "bot.ticketing.listener.ticket_exists").replace("{channel}", channel.getAsMention()))
						.build()
					).queue();
					return;
				}
				db.ticket.closeTicket(Instant.now(), channelId);
			}

			Integer ticketId = 1 + db.ticket.lastId(guildId);
			event.getChannel().asTextChannel().createThreadChannel(lu.getLocalized(event.getGuildLocale(), "ticket.role")+"-"+ticketId.toString(), true).queue(
				channel -> {
					String roleIds = add.stream().map(role -> role.getId()).collect(Collectors.joining(";"));
					db.ticket.addRoleTicket(ticketId, event.getMember().getId(), guildId, channel.getId(), "role", roleIds);
					
					StringBuffer mentions = new StringBuffer(event.getMember().getAsMention());
					db.access.getAllRoles(guildId).forEach(roleId -> mentions.append(" <@&"+roleId+">"));
					channel.sendMessage(mentions.toString()).queue(msg -> msg.delete().queueAfter(5, TimeUnit.SECONDS, null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_CHANNEL)));
					
					String steam64 = db.verifyCache.getSteam64(event.getMember().getId());
					String rolesString = String.join(" ", add.stream().map(role -> role.getAsMention()).collect(Collectors.joining(" ")), (otherRole ? lu.getLocalized(event.getGuildLocale(), "bot.ticketing.embeds.other") : ""));
					String proofString = add.stream().map(role -> db.role.getDescription(role.getId())).filter(str -> str != null).distinct().collect(Collectors.joining("\n- ", "- ", ""));
					MessageEmbed embed = new EmbedBuilder().setColor(db.guild.getColor(guildId))
						.setDescription(String.format("SteamID\n> %s\n%s\n> %s\n\n%s, %s\n%s\n\n%s",
							(steam64 == null ? "None" : bot.getSteamUtil().convertSteam64toSteamID(steam64) + "\n> [UnionTeams](https://unionteams.ru/player/"+steam64+")"),
							lu.getLocalized(event.getGuildLocale(), "ticket.role_title"),
							rolesString,
							event.getMember().getEffectiveName(),
							lu.getLocalized(event.getGuildLocale(), "ticket.role_header"),
							(proofString.length() < 3 ? lu.getLocalized(event.getGuildLocale(), "ticket.role_proof") : proofString),
							lu.getLocalized(event.getGuildLocale(), "ticket.role_footer")
						))
						.build();
					Button approve = Button.success("ticket:approve", lu.getLocalized(event.getGuildLocale(), "ticket.role_approve"));
					Button close = Button.danger("ticket:close", lu.getLocalized(event.getGuildLocale(), "ticket.close")).withEmoji(Emoji.fromUnicode("🔒")).asDisabled();
					channel.sendMessageEmbeds(embed).setAllowedMentions(Collections.emptyList()).addActionRow(approve, close).queue(msg -> {
						msg.editMessageComponents(ActionRow.of(approve, close.asEnabled())).queueAfter(10, TimeUnit.SECONDS);
					});

					event.getHook().editOriginalEmbeds(new EmbedBuilder().setColor(Constants.COLOR_SUCCESS)
						.setDescription(lu.getText(event, "bot.ticketing.listener.created").replace("{channel}", channel.getAsMention()))
						.build()
					).queue();
				}, failure -> {
					event.replyEmbeds(bot.getEmbedUtil().getError(event, "bot.ticketing.listener.cant_create")).setEphemeral(true).queue();
				});
		}
	}
	
}
