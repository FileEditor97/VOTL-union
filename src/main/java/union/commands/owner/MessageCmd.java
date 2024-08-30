package union.commands.owner;

import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import union.base.command.SlashCommandEvent;
import union.commands.CommandBase;
import union.objects.constants.CmdCategory;
import union.objects.constants.Constants;

import java.util.List;

public class MessageCmd extends CommandBase {

	public MessageCmd() {
		this.name = "message";
		this.path = "bot.owner.message";
		this.options = List.of(
			new OptionData(OptionType.STRING, "channel_id", lu.getText(path+".channel_id.help"), true),
			new OptionData(OptionType.STRING, "content", lu.getText(path+".content.help"), true)
		);
		this.category = CmdCategory.OWNER;
		this.ownerCommand = true;
		this.guildOnly = false;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		String channelId = event.optString("channel_id");
		GuildMessageChannel channel = event.getJDA().getChannelById(GuildMessageChannel.class, channelId);
		if (channel == null) {
			createReply(event, Constants.FAILURE+" Channel not found.");
			return;
		}

		channel.sendMessage(event.optString("content")).queue();
		createReply(event, Constants.SUCCESS);
	}
}
