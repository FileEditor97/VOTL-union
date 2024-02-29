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
import union.objects.annotation.Nullable;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild.VerificationLevel;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.utils.ImageProxy;

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
    @NotNull
    static RestAction<Invite> resolve(@NotNull final JDA api, @NotNull final String code, final boolean withCounts) {
        return InviteImpl.resolve(api, code, withCounts);
    }

    /**
     * The type of this invite.
     *
     * @return The invite's type
     */
    @NotNull
    Invite.InviteType getType();

    /**
     * The invite code
     *
     * @return the invite code
     */
    @NotNull
    String getCode();

    /**
     * The invite URL for this invite in the format of:
     * {@code "https://discord.gg/" + getCode()}
     *
     * @return Invite URL for this Invite
     */
    @NotNull
    default String getUrl()
    {
        return "https://discord.gg/" + getCode();
    }

    /**
     * The {@link net.dv8tion.jda.api.JDA JDA} instance used to create this Invite
     *
     * @return the corresponding JDA instance
     */
    @NotNull
    JDA getJDA();

    /**
     * Returns experation date of this invite.
     *
     * @return The experation date of this invite
     */
    @NotNull
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
     * An {@link Invite.Guild Invite.Guild} object
     * containing information about this invite's origin guild.
     *
     * @return Information about this invite's origin guild or null in case of a group invite
     * 
     * @see    Invite.Guild
     */
    Guild getGuild();

     /**
     * POJO for the guild information provided by an invite.
     * 
     * @see #getGuild()
     */
    interface Guild extends ISnowflake {
        /**
         * The icon id of this guild.
         *
         * @return The guild's icon id
         *
         * @see    #getIconUrl()
         */
        @Nullable
        String getIconId();

        /**
         * The icon url of this guild.
         *
         * @return The guild's icon url
         *
         * @see    #getIconId()
         */
        @Nullable
        String getIconUrl();

        /**
         * Returns an {@link ImageProxy} for this guild's icon
         *
         * @return Possibly-null {@link ImageProxy} of this guild's icon
         *
         * @see    #getIconUrl()
         */
        @Nullable
        default ImageProxy getIcon()
        {
            final String iconUrl = getIconUrl();
            return iconUrl == null ? null : new ImageProxy(iconUrl);
        }

        /**
         * The name of this guild.
         *
         * @return The guild's name
         */
        @NotNull
        String getName();

        /**
         * The splash image id of this guild.
         *
         * @return The guild's splash image id or {@code null} if the guild has no splash image
         *
         * @see    #getSplashUrl()
         */
        @Nullable
        String getSplashId();

        /**
         * Returns the splash image url of this guild.
         *
         * @return The guild's splash image url or {@code null} if the guild has no splash image
         *
         * @see    #getSplashId()
         */
        @Nullable
        String getSplashUrl();

        /**
         * Returns an {@link ImageProxy} for this invite guild's splash image.
         *
         * @return Possibly-null {@link ImageProxy} of this invite guild's splash image
         *
         * @see    #getSplashUrl()
         */
        @Nullable
        default ImageProxy getSplash()
        {
            final String splashUrl = getSplashUrl();
            return splashUrl == null ? null : new ImageProxy(splashUrl);
        }

        /**
         * Returns the {@link net.dv8tion.jda.api.entities.Guild.VerificationLevel VerificationLevel} of this guild.
         * 
         * @return the verification level of the guild
         */
        @NotNull
        VerificationLevel getVerificationLevel();
        
        /**
         * Returns the approximate count of online members in the guild. If the online member count was not included in the
         * invite, this will return -1. Counts will usually only be returned when resolving the invite via the 
         * {@link #resolve(net.dv8tion.jda.api.JDA, java.lang.String, boolean) Invite.resolve()} method with the
         * withCounts boolean set to {@code true}
         * 
         * @return the approximate count of online members in the guild, or -1 if not present in the invite
         */
        int getOnlineCount();
        
        /**
         * Returns the approximate count of total members in the guild. If the total member count was not included in the
         * invite, this will return -1. Counts will usually only be returned when resolving the invite via the 
         * {@link #resolve(net.dv8tion.jda.api.JDA, java.lang.String, boolean) Invite.resolve()} method with the
         * withCounts boolean set to {@code true}
         * 
         * @return the approximate count of total members in the guild, or -1 if not present in the invite
         */
        int getMemberCount();

        /**
         * The Features of the {@link Invite.Guild Guild}.
         * <p>
         * <b>Possible known features:</b>
         * <ul>
         *     <li>VIP_REGIONS - Guild has VIP voice regions</li>
         *     <li>VANITY_URL - Guild a vanity URL (custom invite link)</li>
         *     <li>INVITE_SPLASH - Guild has custom invite splash. See {@link #getSplashId()} and {@link #getSplashUrl()}</li>
         *     <li>VERIFIED - Guild is "verified"</li>
         *     <li>MORE_EMOJI - Guild is able to use more than 50 emoji</li>
         * </ul>
         *
         * @return Never-null, unmodifiable Set containing all of the Guild's features.
         */
        @NotNull
        Set<String> getFeatures();
    }

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
