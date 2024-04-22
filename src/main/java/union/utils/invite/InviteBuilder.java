/*
 * Copyright 2015 Austin Keener, Michael Ritter, Florian Spieß, and the JDA contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */

package union.utils.invite;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import net.dv8tion.jda.api.entities.Guild.VerificationLevel;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.utils.data.DataObject;
import net.dv8tion.jda.internal.JDAImpl;

public class InviteBuilder {
	
	public static Invite createInvite(JDAImpl jda, DataObject object) {
		final String code = object.getString("code");

		final DataObject channelObject = object.getObject("channel");
		final ChannelType channelType = ChannelType.fromId(channelObject.getInt("type"));

		final Invite.InviteType type;
		final Invite.Guild guild;

		if (channelType == ChannelType.GROUP) {
			type = Invite.InviteType.GROUP;
			guild = null;
		} else if (channelType.isGuild()) {
			type = Invite.InviteType.GUILD;

			final DataObject guildObject = object.getObject("guild");

			final String guildIconId = guildObject.getString("icon", null);
            final long guildId = guildObject.getLong("id");
            final String guildName = guildObject.getString("name");
            final String guildSplashId = guildObject.getString("splash", null);
            final VerificationLevel guildVerificationLevel = VerificationLevel.fromKey(guildObject.getInt("verification_level", -1));
            final int presenceCount = object.getInt("approximate_presence_count", -1);
            final int memberCount = object.getInt("approximate_member_count", -1);

			final Set<String> guildFeatures;
            if (guildObject.isNull("features"))
                guildFeatures = Collections.emptySet();
            else
                guildFeatures = StreamSupport.stream(guildObject.getArray("features").spliterator(), false).map(String::valueOf).collect(Collectors.toUnmodifiableSet());

            guild = new InviteImpl.GuildImpl(guildId, guildIconId, guildName, guildSplashId, guildVerificationLevel, presenceCount, memberCount, guildFeatures);
		} else {
			// Unknown channel type for invites
			type = Invite.InviteType.UNKNOWN;
			guild = null;
		}

		OffsetDateTime timeExpires = null;
		boolean expires = false;

		if (!object.isNull("expires_at")) {
			timeExpires = OffsetDateTime.parse(object.getString("expires_at"));
			expires = true;
		}

		return new InviteImpl(jda, code, expires, timeExpires, guild, type);
	}

}
