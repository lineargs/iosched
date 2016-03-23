/*
 * Copyright (c) 2016 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.samples.apps.iosched.sync.userdata.firebase;

import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;

import com.google.samples.apps.iosched.sync.userdata.UserAction;
import com.google.samples.apps.iosched.sync.userdata.util.UserDataHelper;
import com.google.samples.apps.iosched.util.FirebaseUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Helper class for managing the merge of local and remote user data. Processes the {@link
 * com.firebase.client.DataSnapshot} from Firebase and creates a {@link
 * com.google.samples.apps.iosched.sync.userdata.util.UserDataHelper.UserData} object. Also creates
 * a {@code UserData} object from data stored in the local DB. Creates a merged {@code UserData}
 * object representing the consensus user data, and uses that to update both Firebase and the local
 * DB.
 */
public class MergeHelper {
    /**
     * Holds user data retrieved from the local ContentProvider.
     */
    private final UserDataHelper.UserData mLocalUserData;

    /**
     * Holds user data retrieved from Firebase.
     */
    private final UserDataHelper.UserData mRemoteUserData;

    /**
     * Holds data generated by merging local and remote user data.
     */
    private final UserDataHelper.UserData mMergedUserData;

    public MergeHelper(@NonNull UserDataHelper.UserData localUserData,
            @NonNull UserDataHelper.UserData remoteUserData,
            @NonNull UserDataHelper.UserData mergedUserData) {
        mLocalUserData = checkNotNull(localUserData);
        mRemoteUserData = checkNotNull(remoteUserData);
        mMergedUserData = checkNotNull(mergedUserData);
    }

    @VisibleForTesting
    UserDataHelper.UserData getLocalUserData() {
        return mLocalUserData;
    }

    @VisibleForTesting
    UserDataHelper.UserData getRemoteUserData() {
        return mRemoteUserData;
    }

    @VisibleForTesting
    UserDataHelper.UserData getMergedUserData() {
        return mMergedUserData;
    }

    /**
     * Sets the GCM key for merged user data. Picks the remote GCM key if it exists; otherwise,
     * picks the local GCM key.
     */
    public void mergeGCMKeys() {
        String remoteGcmKey = mRemoteUserData.getGcmKey();
        String localGcmKey = mLocalUserData.getGcmKey();
        mMergedUserData.setGcmKey(remoteGcmKey == null || remoteGcmKey.isEmpty() ? localGcmKey :
                remoteGcmKey);
    }

    /**
     * Processes changes in local user data which were triggered by a user action and which may
     * require a remote Firebase sync. We maintain a flag per data item (see {@link
     * com.google.samples.apps.iosched.provider.ScheduleContract}), and when an item changes, we
     * attempt to sync it.
     *
     * @param actions The user actions that require a remote sync.
     */
    public void mergeUnsyncedActions(final List<UserAction> actions) {
        mMergedUserData.updateVideoIds(mRemoteUserData);
        for (Map.Entry<String, Long> entry : mRemoteUserData.getStarredSessions().entrySet()) {
            mMergedUserData.getStarredSessions().put(entry.getKey(), entry.getValue());
        }
        mMergedUserData.updateFeedbackSubmittedSessionIds(mRemoteUserData);

        for (final UserAction action : actions) {
            if (action.requiresSync) {
                if (UserAction.TYPE.ADD_STAR.equals(action.type) ||
                        UserAction.TYPE.REMOVE_STAR.equals(action.type)) {

                    // The merged user data so far reflects remote. Override remote wherever local
                    // data is more recent.
                    Long mergedItemTimestamp = mMergedUserData.getStarredSessions().get(
                            action.sessionId);

                    // Either remote doesn't have this session, or local is more recent than
                    // remote.
                    if (mergedItemTimestamp == null || mergedItemTimestamp < action.timestamp) {

                        if (UserAction.TYPE.ADD_STAR.equals(action.type)) {
                            mMergedUserData.getStarredSessions()
                                           .put(action.sessionId, action.timestamp);
                        } else {
                            mMergedUserData.getStarredSessions().remove(action.sessionId);
                        }
                    }
                } else if (UserAction.TYPE.VIEW_VIDEO.equals(action.type)) {
                    mMergedUserData.addVideoId(action.videoId);
                } else if (UserAction.TYPE.SUBMIT_FEEDBACK.equals(action.type)) {
                    mMergedUserData.addFeedbackSubmittedSessionId(action.sessionId);
                }
            }
        }
    }

    /**
     * Builds and returns a Map that can be used when calling {@link com.firebase.client
     * .Firebase#updateChildren(Map)} to update Firebase with a single write.
     *
     * @return A Map where the keys are String paths relative to Firebase root, and the values are
     * the data that is written to those paths.
     */
    public Map<String, Object> getPendingFirebaseUpdatesMap() {
        Map<String, Object> pendingFirebaseUpdatesMap = new HashMap<>();
        pendingFirebaseUpdatesMap.put(FirebaseUtils.FIREBASE_NODE_GCM_KEY,
                mMergedUserData.getGcmKey());

        for (String videoID : mMergedUserData.getViewedVideoIds()) {
            pendingFirebaseUpdatesMap.put(FirebaseUtils.getViewedVideoChildPath(videoID), true);
        }

        handleStarredSessions(pendingFirebaseUpdatesMap);
        handleUnstarredSessions(pendingFirebaseUpdatesMap);

        for (String sessionID : mMergedUserData.getFeedbackSubmittedSessionIds()) {
            pendingFirebaseUpdatesMap
                    .put(FirebaseUtils.getFeedbackSubmittedSessionChildPath(sessionID), true);
        }
        return pendingFirebaseUpdatesMap;
    }

    /**
     * Updates {@code map} with sessions added to a user's schedule and updates the timestamp.
     *
     * @param map Map that can be used when calling {@link com.firebase.client
     *            .Firebase#updateChildren(Map)} to update Firebase with a single write.
     */
    private void handleStarredSessions(Map<String, Object> map) {
        for (final Map.Entry<String, Long> entry : mMergedUserData.getStarredSessions()
                                                                  .entrySet()) {
            updateSessionInSchedule(map, entry.getKey(), true);
            updateSessionTimestamp(map, entry.getKey(), entry.getValue());
        }
    }

    /**
     * Updates {@code map} with sessions removed from a user's schedule and updates the timestamp.
     * Note that once a session is added to a user's schedule, it is never removed from Firebase:
     * if it stays in the user schedule, the in_schedule value in Firebase is set to true, and if it
     * is removed from the user schedule, the in_schedule value is set to false.
     *
     * @param map Used when calling {@link com.firebase.client.Firebase#updateChildren(Map)} to
     *            update Firebase with a single write.
     */
    private void handleUnstarredSessions(Map<String, Object> map) {
        for (final Map.Entry<String, Long> entry : mRemoteUserData.getStarredSessions()
                                                                  .entrySet()) {
            // Merged data represents the canonical collection of starred sessions. Sessions found
            // in remote data, but absent in merged data are no longer part of the user schedule.
            // We set their in_schedule value to false.
            if (mMergedUserData.getStarredSessions().get(entry.getKey()) == null) {
                updateSessionInSchedule(map, entry.getKey(), false);
            }
        }
    }

    /**
     * Updates {@code map} with the sessions that have been added or removed from a user's schedule.
     *
     * @param map        Used when calling {@link com.firebase.client.Firebase#updateChildren(Map)}
     *                   to update Firebase with a single write.
     * @param sessionId  The ID of the session that was added or removed from a user's schedule.
     * @param inSchedule Whether session is in schedule (true), or removed from schedule (false).
     */
    private void updateSessionInSchedule(Map<String, Object> map, String sessionId,
            boolean inSchedule) {
        map.put(FirebaseUtils.getStarredSessionInScheduleChildPath(sessionId), inSchedule);
    }

    /**
     * Updates the timestamp for a session that was added or removed from a user's schedule.
     *
     * @param map       Used when calling {@link com.firebase.client.Firebase#updateChildren(Map)}
     *                  to update Firebase with a single write.
     * @param sessionId The ID of the session that was added or removed from a user's schedule.
     * @param timestamp The time when the session was starred or unstarred. In milliseconds since
     *                  epoch.
     */
    private void updateSessionTimestamp(Map<String, Object> map, String sessionId, Long timestamp) {
        map.put(FirebaseUtils.getStarredSessionTimestampChildPath(sessionId), timestamp);
    }

    /**
     * Throws an exception if {@code userData} is null. Otherwise returns {@code userData}.
     *
     * @param userData The {@link UserDataHelper.UserData} object that holds the user data.
     */
    private UserDataHelper.UserData checkNotNull(UserDataHelper.UserData userData) {
        if (userData != null) {
            return userData;
        } else {
            throw new IllegalArgumentException("userData must not be null");
        }
    }
}