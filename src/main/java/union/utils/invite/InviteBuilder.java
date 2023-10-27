package union.utils.invite;

import java.time.OffsetDateTime;

import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.utils.data.DataObject;
import net.dv8tion.jda.internal.JDAImpl;

public class InviteBuilder {
	
	public static Invite createInvite(JDAImpl jda, DataObject object) {
		final String code = object.getString("code");

		final DataObject channelObject = object.getObject("channel");
		final ChannelType channelType = ChannelType.fromId(channelObject.getInt("type"));

		final Invite.InviteType type;
		boolean isFromGuild = false;

		if (channelType == ChannelType.GROUP) {
			type = Invite.InviteType.GROUP;
		} else if (channelType.isGuild()) {
			type = Invite.InviteType.GUILD;
			isFromGuild = true;
		} else {
			// Unknown channel type for invites
			type = Invite.InviteType.UNKNOWN;
		}

		OffsetDateTime timeExpires = null;
		boolean expires = false;

		if (!object.isNull("expires_at")) {
			timeExpires = OffsetDateTime.parse(object.getString("expires_at"));
			expires = true;
		}
			

		return new InviteImpl(jda, code, expires, timeExpires, isFromGuild, type);
	}

}
