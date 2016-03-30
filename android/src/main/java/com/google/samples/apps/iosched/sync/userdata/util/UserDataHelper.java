/*
 * Copyright 2014 Google Inc. All rights reserved.
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
package com.google.samples.apps.iosched.sync.userdata.util;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import com.google.common.base.Charsets;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.google.samples.apps.iosched.provider.ScheduleContract;
import com.google.samples.apps.iosched.sync.userdata.UserAction;

import java.util.*;

/**
 * Helper class to handle the format of the User Data that is stored into AppData.
 * TODO: Refactor. Class mixes util methods, Pojos and business logic. See b/27809362.
 */
public class UserDataHelper {

    /** JSON Attribute name of the starred sessions values. */
    public static final String JSON_ATTRIBUTE_STARRED_SESSIONS = "starred_sessions";

    /** JSON Attribute name of the GCM Key value. */
    public static final String JSON_ATTRIBUTE_GCM_KEY = "gcm_key";

    /** JSON Attribute name of the feedback submitted for sessions values. */
    public static final String JSON_ATTRIBUTE_FEEDBACK_SUBMITTED_SESSIONS = "feedback_submitted_sessions";

    /** JSON Attribute name of the viewed videos values. */
    public static final String JSON_ATTRIBUTE_VIEWED_VIDEOS = "viewed_videos";

    /**
     * Returns a JSON string representation of the given UserData object.
     */
    static public String toJsonString(UserData userData) {
        return new Gson().toJson(userData);
    }

    /**
     * Returns the JSON string representation of the given UserData object as a byte array.
     */
    static public byte[] toByteArray(UserData userData) {
        return toJsonString(userData).getBytes(Charsets.UTF_8);
    }

    /**
     * Deserializes the UserData given as a JSON string into a {@link UserData} object.
     * TODO: put this in UserData.
     */
    static public UserData fromString(String str) {
        if (str == null || str.isEmpty()) {
            return new UserData();
        }
        return new Gson().fromJson(str, UserData.class);
    }

    /**
     * Creates a UserData object from the given List of user actions.
     */
    static public UserData getUserData(List<UserAction> actions) {
        UserData userData = new UserData();
        if (actions != null) {
            for (UserAction action : actions) {
                if (action.type == UserAction.TYPE.ADD_STAR) {
                    if(userData.getStarredSessions() == null) {
                        // TODO: Make this part of setter. Create lazily.
                        userData.setStarredSessions(new HashMap<String, Long>());
                    }
                    userData.getStarredSessions().put(action.sessionId,
                            action.timestamp);
                } else if (action.type == UserAction.TYPE.VIEW_VIDEO) {
                    if(userData.getViewedVideoIds() == null) {
                        userData.setViewedVideoIds(new HashSet<String>());
                    }
                    userData.getViewedVideoIds().add(action.videoId);
                } else if (action.type == UserAction.TYPE.SUBMIT_FEEDBACK) {
                    if(userData.getFeedbackSubmittedSessionIds() == null) {
                        userData.setFeedbackSubmittedSessionIds(new HashSet<String>());
                    }
                    userData.getFeedbackSubmittedSessionIds().add(action.sessionId);
                }
            }
        }
        return userData;
    }

    /**
     * Reads the data from the {@code column} of the content's {@code queryUri} and returns it as an
     * Array.
     */
    static private Set<String> getColumnContentAsArray(Context context, Uri queryUri,
            String column){
        Cursor cursor = context.getContentResolver().query(queryUri,
                new String[]{column}, null, null, null);
        Set<String> columnValues = new HashSet<>();
        try {
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    columnValues.add(cursor.getString(0));
                } while (cursor.moveToNext());
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return columnValues;
    }

    /**
     * Reads the data from columns of the content's {@code queryUri} and returns it as a Map.
     */
    static private Map<String, Long> getColumnContentAsMap(Context context, Uri queryUri,
            String column1, String column2) {
        Cursor cursor = context.getContentResolver().query(queryUri,
                new String[]{column1, column2}, null, null, null);
        Map<String, Long> columnValues = new HashMap<>();
        try {
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    columnValues.put(cursor.getString(cursor.getColumnIndex(column1)),
                            cursor.getLong(cursor.getColumnIndex(column2)));
                } while (cursor.moveToNext());
            }

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return columnValues;
    }

    /**
     * Returns the User Data that's on the device's local DB.
     */
    static public UserData getLocalUserData(Context context) {
        UserData userData = new UserData();

        userData.setStarredSessions(getColumnContentAsMap(context,
                ScheduleContract.MySchedule.CONTENT_URI, ScheduleContract.MySchedule.SESSION_ID,
                ScheduleContract.MySchedule.MY_SCHEDULE_TIMESTAMP));

        // Get Viewed Videos.
        userData.setViewedVideoIds(getColumnContentAsArray(context,
                ScheduleContract.MyViewedVideos.CONTENT_URI,
                ScheduleContract.MyViewedVideos.VIDEO_ID));

        // Get Feedback Submitted Sessions.
        userData.setFeedbackSubmittedSessionIds(getColumnContentAsArray(context,
                ScheduleContract.MyFeedbackSubmitted.CONTENT_URI,
                ScheduleContract.MyFeedbackSubmitted.SESSION_ID));

        return userData;
    }

    /**
     * Writes the given user data into the device's local DB.
     */
    static public void setLocalUserData(Context context, UserData userData, String accountName) {
        // TODO: throw if null. Callers should ensure the data is not null. See b/27809502.
        if (userData == null) {
            return;
        }

        // first clear all stars.
        context.getContentResolver().delete(ScheduleContract.MySchedule.CONTENT_URI,
                ScheduleContract.MySchedule.MY_SCHEDULE_ACCOUNT_NAME +" = ?",
                new String[]{accountName});

        // Now add the ones in sessionIds.
        ArrayList<UserAction> actions = new ArrayList<UserAction>();
        if (userData.getStarredSessions() != null) {
            for (Map.Entry<String, Long> entry : userData.getStarredSessions().entrySet()) {
                UserAction action = new UserAction();
                action.type = UserAction.TYPE.ADD_STAR;
                action.sessionId = entry.getKey();
                action.timestamp = entry.getValue();
                actions.add(action);
            }
        }

        // first clear all viewed videos.
        context.getContentResolver().delete(ScheduleContract.MyViewedVideos.CONTENT_URI,
                ScheduleContract.MyViewedVideos.MY_VIEWED_VIDEOS_ACCOUNT_NAME +" = ?",
                new String[]{accountName});

        // Now add the viewed videos.
        if (userData.getViewedVideoIds() != null) {
            for (String videoId : userData.getViewedVideoIds()) {
                UserAction action = new UserAction();
                action.type = UserAction.TYPE.VIEW_VIDEO;
                action.videoId = videoId;
                actions.add(action);
            }
        }

        // first clear all feedback submitted videos.
        context.getContentResolver().delete(ScheduleContract.MyFeedbackSubmitted.CONTENT_URI,
                ScheduleContract.MyFeedbackSubmitted.MY_FEEDBACK_SUBMITTED_ACCOUNT_NAME +" = ?",
                new String[]{accountName});

        // Now add the feedback submitted videos.
        if (userData.getFeedbackSubmittedSessionIds() != null) {
            for (String sessionId : userData.getFeedbackSubmittedSessionIds()) {
                UserAction action = new UserAction();
                action.type = UserAction.TYPE.SUBMIT_FEEDBACK;
                action.sessionId = sessionId;
                actions.add(action);
            }
        }

        UserActionHelper.updateContentProvider(context, actions, accountName);
    }

    /**
     * Represents all User specific data that can be synchronized on Google Drive App Data.
     * TODO: Pojo should not be part of a utility class.
     */
    public static class UserData {

        @SerializedName(JSON_ATTRIBUTE_STARRED_SESSIONS)
        private Map<String, Long> starredSessions = new HashMap<>();

        @SerializedName(JSON_ATTRIBUTE_FEEDBACK_SUBMITTED_SESSIONS)
        private Set<String> feedbackSubmittedSessionIds = new HashSet<String>();

        @SerializedName(JSON_ATTRIBUTE_VIEWED_VIDEOS)
        private Set<String> viewedVideoIds = new HashSet<String>();

        @SerializedName(JSON_ATTRIBUTE_GCM_KEY)
        private String gcmKey;

        public Map<String, Long> getStarredSessions() {
            return starredSessions;
        }

        public void setStarredSessions(Map<String, Long> starredSessions) {
            this.starredSessions = starredSessions;
        }

        public Set<String> getFeedbackSubmittedSessionIds() {
            return feedbackSubmittedSessionIds;
        }

        public void setFeedbackSubmittedSessionIds(Set<String> feedbackSubmittedSessionIds) {
            this.feedbackSubmittedSessionIds = feedbackSubmittedSessionIds;
        }

        public Set<String> getViewedVideoIds() {
            return viewedVideoIds;
        }

        public void setViewedVideoIds(Set<String> viewedVideoIds) {
            this.viewedVideoIds = viewedVideoIds;
        }

        public String getGcmKey() {
            return gcmKey;
        }

        public void setGcmKey(String gcmKey) {
            this.gcmKey = gcmKey;
        }

        public void addVideoId(String videoId) {
            getViewedVideoIds().add(videoId);
        }

        public void updateVideoIds(UserData other) {
            getViewedVideoIds().addAll(other.getViewedVideoIds());
        }

        public void addFeedbackSubmittedSessionId(String sessionId) {
            getFeedbackSubmittedSessionIds().add(sessionId);
        }

        public void updateFeedbackSubmittedSessionIds(UserData other) {
            getFeedbackSubmittedSessionIds().addAll(other.getFeedbackSubmittedSessionIds());
        }

        /**
         * Indicates whether this is equal to another object. Auto-generated by Intellij.
         */
        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            final UserData userData = (UserData) o;

            if (starredSessions != null ? !starredSessions.equals(userData.starredSessions) :
                    userData.starredSessions != null) {
                return false;
            }
            if (feedbackSubmittedSessionIds != null ?
                    !feedbackSubmittedSessionIds.equals(userData.feedbackSubmittedSessionIds) :
                    userData.feedbackSubmittedSessionIds != null) {
                return false;
            }
            if (viewedVideoIds != null ? !viewedVideoIds.equals(userData.viewedVideoIds) :
                    userData.viewedVideoIds != null) {
                return false;
            }
            return !(gcmKey != null ? !gcmKey.equals(userData.gcmKey) : userData.gcmKey != null);

        }

        /**
         * Returns a hash code value for this object. Auto-generated by Intellij.
         */
        @Override
        public int hashCode() {
            int result = starredSessions != null ? starredSessions.hashCode() : 0;
            result = 31 * result +
                    (feedbackSubmittedSessionIds != null ? feedbackSubmittedSessionIds.hashCode() :
                            0);
            result = 31 * result + (viewedVideoIds != null ? viewedVideoIds.hashCode() : 0);
            result = 31 * result + (gcmKey != null ? gcmKey.hashCode() : 0);
            return result;
        }
    }
}
