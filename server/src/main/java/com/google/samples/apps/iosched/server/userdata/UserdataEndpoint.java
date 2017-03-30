/*
 * Copyright 2017 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.samples.apps.iosched.server.userdata;

import static com.googlecode.objectify.ObjectifyService.ofy;

import com.google.api.server.spi.auth.common.User;
import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.config.ApiNamespace;
import com.google.api.server.spi.config.Named;
import com.google.api.server.spi.response.UnauthorizedException;
import com.google.samples.apps.iosched.server.userdata.db.BookmarkedSession;
import com.google.samples.apps.iosched.server.userdata.db.ReservedSession;
import com.google.samples.apps.iosched.server.userdata.db.ReservedSession.Status;
import com.google.samples.apps.iosched.server.userdata.db.UserData;
import com.googlecode.objectify.NotFoundException;

import java.util.Map;

/** Endpoint for user data storage. */
@Api(
        name = "userdata",
        title = "IOSched User Data",
        description = "Storage for user preferences",
        version = "v1",
        namespace = @ApiNamespace(
                ownerDomain = "iosched.apps.samples.google.com",
                ownerName = "iosched.apps.samples.google.com",
                packagePath = ""
        ),
        clientIds = {Ids.WEB_CLIENT_ID, Ids.ANDROID_CLIENT_ID, Ids.IOS_CLIENT_ID,
                com.google.api.server.spi.Constant.API_EXPLORER_CLIENT_ID},
        audiences = {Ids.ANDROID_AUDIENCE}
)
public class UserdataEndpoint {
    /**
     * Helper method to get the data object for a given User.
     *
     * @param user User to lookup
     * @return User's data object
     */
    private UserData getUser(User user) throws UnauthorizedException {
        if (user == null) {
            throw new UnauthorizedException("Invalid credentials");
        }
        String uid = user.getId();

        try {
            return ofy().load().type(UserData.class).id(uid).safe();
        } catch (NotFoundException e) {
            UserData data = new UserData();
            data.userId = uid;
            return data;
        }
    }

    /**
     * Helper method to save a user data object
     *
     * @param data Data to save
     */
    private void save(UserData data) throws UnauthorizedException {
        ofy().save().entity(data).now();
    }

    /**
     * Endpoint: Get all saved data for currently authenticated user.
     *
     * @param user Current user (injected by Endpoints)
     * @return Saved UserData object for current user
     */
    @ApiMethod(name = "getAll", path = "all")
    public UserData getAll(User user) throws UnauthorizedException {
        return getUser(user);
    }

    /**
     * Get bookmarked sessions for currently authenticated user.
     *
     * @param user Current user (injected by Endpoints)
     * @return Bookmarked sessions for current user
     */
    @ApiMethod(name = "getBookmarkedSessions", path = "bookmarked")
    public Map<String, BookmarkedSession> getBookmarkedSessions(User user)
            throws UnauthorizedException {
        return getUser(user).bookmarkedSessions;
    }

    /**
     * Get reserved sessions for currently authenticated user.
     *
     * @param user Current user (injected by Endpoints)
     * @return Reserved sessions for current user
     */
    @ApiMethod(name = "getReservedSessions", path = "reserved")
    public Map<String, ReservedSession> getReservedSessions(User user)
            throws UnauthorizedException {
        return getUser(user).reservedSessions;
    }

    /**
     * Get reviewed sessions for currently authenticated user.
     *
     * @param user Current user (injected by Endpoints)
     * @return Reviewed sessions for currently authenticated user (as an array of Strings)
     */
    @ApiMethod(name = "getReviewedSessions", path = "reviewed")
    public Object[] getReviewedSessions(User user) throws UnauthorizedException {
        return getUser(user).reviewedSessions.toArray();
    }

    /**
     * Mark a session as reviewed for the current user. This can not be unset.
     *
     * @param user      Current user (injected by Endpoints)
     * @param sessionId Session ID to mark as reviewed.
     * @return The list of reviewed sessions for the user (as an array of Strings)
     */
    @ApiMethod(name = "addReviewedSession", path = "reviewed", httpMethod = ApiMethod.HttpMethod
            .PUT)
    public Object[] addReviewedSession(User user, @Named("sessionId") String sessionId)
            throws UnauthorizedException {
        UserData data = getUser(user);
        data.reviewedSessions.add(sessionId);
        save(data);
        return data.reviewedSessions.toArray();
    }

    /**
     * Add a bookmarked session for the current user. If the session is already in the user's feed,
     * it will be annotated with inSchedule=true.
     *
     * @param user         Current user (injected by Endpoints)
     * @param sessionId    Session ID to mark as bookmarked.
     * @param timestampUTC The time (in millis, UTC) when the user performed this action. May be
     *                     different than the time this method is called if offline sync is
     *                     implemented. MUST BE ACCURATE - COMPENSATE FOR CLOCK DRIFT!
     * @return The list of bookmarked sessions for the user
     */
    @ApiMethod(name = "addBookmarkedSession", path = "bookmarked", httpMethod = ApiMethod
            .HttpMethod.PUT)
    public Map<String, BookmarkedSession> addBookmarkedSession(User user,
            @Named("sessionId") String sessionId, @Named
            ("timestampUTC") long timestampUTC) throws UnauthorizedException {
        UserData data = getUser(user);
        BookmarkedSession s = new BookmarkedSession(sessionId, true, timestampUTC);
        data.bookmarkedSessions.put(sessionId, s);
        save(data);
        return data.bookmarkedSessions;
    }

    /**
     * Remove a bookmarked session for the current user. The session will still be
     * attached to the user's feed, but will be annotated with inSchedule=false.
     *
     * @param user         Current user (injected by Endpoints)
     * @param sessionId    Session ID to mark as not bookmarked.
     * @param timestampUTC The time (in millis, UTC) when the user performed this action. May be
     *                     different than the time this method is called if offline sync is
     *                     implemented. MUST BE ACCURATE - COMPENSATE FOR CLOCK DRIFT!
     */
    @ApiMethod(name = "removeBookmarkedSession", path = "bookmarked", httpMethod = ApiMethod
            .HttpMethod.DELETE)
    public void removeBookmarkedSession(User user, @Named("sessionId") String sessionId, @Named
            ("timestampUTC") long timestampUTC) throws UnauthorizedException {
        UserData data = getUser(user);
        BookmarkedSession s = new BookmarkedSession(sessionId, false, timestampUTC);
        data.bookmarkedSessions.put(sessionId, s);
        save(data);
    }

    /**
     * Add a waitlisted session for the current user. If the session is already in the user's feed,
     * it will be annotated with status=WAITLISTED.
     *
     * @param user         Current user (injected by Endpoints)
     * @param sessionId    Session ID to mark as reserved.
     * @param timestampUTC The time (in millis, UTC) when the user performed this action. May be
     *                     different than the time this method is called if offline sync is
     *                     implemented. MUST BE ACCURATE - COMPENSATE FOR CLOCK DRIFT!
     * @return The list of reserved sessions for the user
     */
    @ApiMethod(name = "addWaitlistedSession", path = "reservations/waitlist",
        httpMethod = ApiMethod.HttpMethod.PUT)
    public Map<String, ReservedSession> addWaitlistedSession (
        User user,
        @Named("sessionId") String sessionId,
        @Named("timestampUTC") long timestampUTC)
        throws UnauthorizedException {
        UserData data = getUser(user);
        ReservedSession s = new ReservedSession(sessionId, Status.WAITLISTED, timestampUTC);
        data.reservedSessions.put(sessionId, s);
        save(data);
        return data.reservedSessions;
    }

    /**
     * Add a reserved session for the current user. If the session is already in the user's feed,
     * it will be annotated with status=RESERVED.
     *
     * @param user         Current user (injected by Endpoints)
     * @param sessionId    Session ID to mark as reserved.
     * @param timestampUTC The time (in millis, UTC) when the user performed this action. May be
     *                     different than the time this method is called if offline sync is
     *                     implemented. MUST BE ACCURATE - COMPENSATE FOR CLOCK DRIFT!
     * @return The list of reserved sessions for the user
     */
    @ApiMethod(name = "addReservedSession", path = "reservations",
        httpMethod = ApiMethod.HttpMethod.PUT)
    public Map<String, ReservedSession> addReservedSession(
            User user,
            @Named("sessionId") String sessionId,
            @Named("timestampUTC") long timestampUTC)
            throws UnauthorizedException {
        UserData data = getUser(user);
        ReservedSession s = new ReservedSession(sessionId, Status.RESERVED, timestampUTC);
        data.reservedSessions.put(sessionId, s);
        save(data);
        return data.reservedSessions;
    }

    /**
     * Remove a reserved session for the current user. The session will still be
     * attached to the user's feed, but will be annotated with status=DELETED.
     *
     * @param user         Current user (injected by Endpoints)
     * @param sessionId    Session ID to mark as not reserved.
     * @param timestampUTC The time (in millis, UTC) when the user performed this action. May be
     *                     different than the time this method is called if offline sync is
     *                     implemented. MUST BE ACCURATE - COMPENSATE FOR CLOCK DRIFT!
     */
    @ApiMethod(name = "removeReservedSession", path = "reservations",
        httpMethod = ApiMethod.HttpMethod.DELETE)
    public void removeReservedSession(User user, @Named("sessionId") String sessionId, @Named
            ("timestampUTC") long timestampUTC) throws UnauthorizedException {
        UserData data = getUser(user);
        ReservedSession s = new ReservedSession(sessionId, Status.DELETED, timestampUTC);
        data.reservedSessions.put(sessionId, s);
        save(data);
    }
}
