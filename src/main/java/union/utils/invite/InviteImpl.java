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
 */

package union.utils.invite;

import java.time.OffsetDateTime;
import java.util.Set;

import union.objects.annotation.NotNull;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild.VerificationLevel;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.Route;
import net.dv8tion.jda.internal.JDAImpl;
import net.dv8tion.jda.internal.requests.RestActionImpl;
import net.dv8tion.jda.internal.utils.Checks;
import net.dv8tion.jda.internal.utils.EntityString;

public class InviteImpl implements Invite {

	private final JDAImpl api;
	private final String code;
	private final boolean expires;
	private final OffsetDateTime timeExpires;
	private final Guild guild;
	private final Invite.InviteType type;

	public InviteImpl(final JDAImpl api, final String code, final boolean expires,
			final OffsetDateTime timeExpires, final Guild guild, final Invite.InviteType type)
	{
		this.api = api;
		this.code = code;
		this.expires = expires;
		this.timeExpires = timeExpires;
		this.guild = guild;
		this.type = type;
	}

	public static RestAction<Invite> resolve(final JDA api, final String code, final boolean withCounts) {
		Checks.notNull(code, "code");
		Checks.notNull(api, "api");

		Route.CompiledRoute route = Route.Invites.GET_INVITE.compile(code);
		
		route = route.withQueryParams("with_expiration", "true");
		if (withCounts)
			route = route.withQueryParams("with_counts", "true");

		JDAImpl jda = (JDAImpl) api;
		return new RestActionImpl<>(api, route, (response, request) -> InviteBuilder.createInvite(jda, response.getObject()));
	}

	@NotNull
	@Override
	public Invite.InviteType getType() {
		return this.type;
	}

	@NotNull
	@Override
	public String getCode() {
		return this.code;
	}

	@NotNull
	@Override
	public JDAImpl getJDA() {
		return this.api;
	}

	@NotNull
	@Override
	public OffsetDateTime getTimeExpires() {
		return this.timeExpires;
	}

	@Override
	public boolean isExpirable() {
		return this.expires;
	}

	@Override
	public Guild getGuild() {
		return this.guild;
	}

	@Override
	public boolean isFromGuild() {
		return this.guild != null;
	}

	@Override
	public int hashCode() {
		return code.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this)
			return true;
		if (!(obj instanceof InviteImpl))
			return false;
		InviteImpl impl = (InviteImpl) obj;
		return impl.code.equals(this.code);
	}

	@Override
	public String toString() {
		return new EntityString(this)
				.addMetadata("code", code)
				.toString();
	}

	public static class GuildImpl implements Guild {

		private final String iconId, name, splashId;
		private final int presenceCount, memberCount;
        private final long id;
        private final VerificationLevel verificationLevel;
        private final Set<String> features;

		public GuildImpl(final long id, final String iconId, final String name, final String splashId,
                         final VerificationLevel verificationLevel, final int presenceCount, final int memberCount,
						 final Set<String> features)
		{
			this.id = id;
            this.iconId = iconId;
            this.name = name;
            this.splashId = splashId;
            this.verificationLevel = verificationLevel;
            this.presenceCount = presenceCount;
            this.memberCount = memberCount;
            this.features = features;
		}

		public GuildImpl(final net.dv8tion.jda.api.entities.Guild guild)
        {
            this(guild.getIdLong(), guild.getIconId(), guild.getName(), guild.getSplashId(),
                 guild.getVerificationLevel(), -1, -1, guild.getFeatures());
        }

        @Override
        public String getIconId()
        {
            return this.iconId;
        }

        @Override
        public String getIconUrl()
        {
            return this.iconId == null ? null
                    : "https://cdn.discordapp.com/icons/" + this.id + "/" + this.iconId + ".png";
        }

        @Override
        public long getIdLong()
        {
            return id;
        }

        @NotNull
        @Override
        public String getName()
        {
            return this.name;
        }

        @Override
        public String getSplashId()
        {
            return this.splashId;
        }

        @Override
        public String getSplashUrl()
        {
            return this.splashId == null ? null
                    : "https://cdn.discordapp.com/splashes/" + this.id + "/" + this.splashId + ".png";
        }

        @NotNull
        @Override
        public VerificationLevel getVerificationLevel()
        {
            return verificationLevel;
        }
        
        @Override
        public int getOnlineCount()
        {
            return presenceCount;
        }
        
        @Override
        public int getMemberCount()
        {
            return memberCount;
        }

        @NotNull
        @Override
        public Set<String> getFeatures()
        {
            return features;
        }

        @Override
        public String toString()
        {
            return new EntityString(this)
                    .setName(name)
                    .toString();
        }

	}

}
