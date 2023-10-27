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

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.Route;
import net.dv8tion.jda.internal.JDAImpl;
import net.dv8tion.jda.internal.requests.RestActionImpl;
import net.dv8tion.jda.internal.utils.Checks;
import net.dv8tion.jda.internal.utils.EntityString;

import jakarta.annotation.Nonnull;

import java.time.OffsetDateTime;

public class InviteImpl implements Invite {

	private final JDAImpl api;
	private final String code;
	private final boolean expires;
	private final OffsetDateTime timeExpires;
	private final boolean isGuild;
	private final Invite.InviteType type;

	public InviteImpl(final JDAImpl api, final String code, final boolean expires,
			final OffsetDateTime timeExpires, final boolean isGuild, final Invite.InviteType type)
	{
		this.api = api;
		this.code = code;
		this.expires = expires;
		this.timeExpires = timeExpires;
		this.isGuild = isGuild;
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

	@Nonnull
	@Override
	public Invite.InviteType getType() {
		return this.type;
	}

	@Nonnull
	@Override
	public String getCode() {
		return this.code;
	}

	@Nonnull
	@Override
	public JDAImpl getJDA() {
		return this.api;
	}

	@Nonnull
	@Override
	public OffsetDateTime getTimeExpires() {
		return this.timeExpires;
	}

	@Override
	public boolean isExpirable() {
		return this.expires;
	}

	@Override
	public boolean isFromGuild() {
		return this.isGuild;
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

}
