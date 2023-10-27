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

import jakarta.annotation.Nonnull;

import java.time.OffsetDateTime;

/**
 * Representation of a Discord Invite.
 * This class is immutable.
 *
 * @since  3.0
 * @author Aljoscha Grebe
 *
 * @see    #resolve(JDA, String)
 * @see    #resolve(JDA, String, boolean)
 *
 * @see    net.dv8tion.jda.api.entities.Guild#retrieveInvites() Guild.retrieveInvites()
 * @see    net.dv8tion.jda.api.entities.channel.attribute.IInviteContainer#retrieveInvites()
 */
public interface Invite {
    /**
     * Retrieves a new {@link Invite Invite} instance for the given invite code.
     * <br><b>You cannot resolve invites if you were banned from the origin Guild!</b>
     *
     * <p>Possible {@link net.dv8tion.jda.api.requests.ErrorResponse ErrorResponses} include:
     * <ul>
     *     <li>{@link net.dv8tion.jda.api.requests.ErrorResponse#UNKNOWN_INVITE Unknown Invite}
     *     <br>The Invite did not exist (possibly deleted) or the account is banned in the guild.</li>
     * </ul>
     *
     * @param  api
     *         The JDA instance
     * @param  code
     *         A valid invite code
     * @param  withCounts
     *         Whether or not to include online and member counts for guild invites or users for group invites
     *
     * @return {@link net.dv8tion.jda.api.requests.RestAction RestAction} - Type: {@link Invite Invite}
     *         <br>The Invite object
     */
    @Nonnull
    static RestAction<Invite> resolve(@Nonnull final JDA api, @Nonnull final String code, final boolean withCounts) {
        return InviteImpl.resolve(api, code, withCounts);
    }

    /**
     * The type of this invite.
     *
     * @return The invite's type
     */
    @Nonnull
    Invite.InviteType getType();

    /**
     * The invite code
     *
     * @return the invite code
     */
    @Nonnull
    String getCode();

    /**
     * The invite URL for this invite in the format of:
     * {@code "https://discord.gg/" + getCode()}
     *
     * @return Invite URL for this Invite
     */
    @Nonnull
    default String getUrl()
    {
        return "https://discord.gg/" + getCode();
    }

    /**
     * The {@link net.dv8tion.jda.api.JDA JDA} instance used to create this Invite
     *
     * @return the corresponding JDA instance
     */
    @Nonnull
    JDA getJDA();

    /**
     * Returns experation date of this invite.
     *
     * @return The experation date of this invite
     */
    @Nonnull
    OffsetDateTime getTimeExpires();

    /**
     * Whether this Invite can expire or not.
     *
     * @return Whether this invite is temporary or not
     */
    boolean isExpirable();

    /**
     * Whether this Invite is for joining a guild.
     *
     * @return Whether this invite is for guild
     */
    boolean isFromGuild();

    /**
     * Enum representing the type of an invite.
     *
     * @see #getType()
     */
    enum InviteType {
        GUILD,
        GROUP,
        UNKNOWN
    }
}
