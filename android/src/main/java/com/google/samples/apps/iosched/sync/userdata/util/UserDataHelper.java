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
 * TODO (shailen): Refactor. Class mixes util methods, Pojos and business logic.
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
     * TODO(shailen): put this in UserData.
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
                    if(userData.getStarredSessionIds() == null) {
                        // TODO (shailen): Make this part of setter. Create lazily.
                        userData.setStarredSessionIds(new HashSet<String>());
                    }
                    userData.getStarredSessionIds().add(action.sessionId);
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
     * Returns the User Data that's on the device's local DB.
     */
    static public UserData getLocalUserData(Context context) {
        UserData userData = new UserData();

        // Get Starred Sessions.
        userData.setStarredSessionIds(getColumnContentAsArray(context,
                ScheduleContract.MySchedule.CONTENT_URI, ScheduleContract.MySchedule.SESSION_ID));

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
        // TODO (shailen): throw if null. Callers should ensure the data is not null.
        if (userData == null) {
            return;
        }

        // first clear all stars.
        context.getContentResolver().delete(ScheduleContract.MySchedule.CONTENT_URI,
                ScheduleContract.MySchedule.MY_SCHEDULE_ACCOUNT_NAME +" = ?",
                new String[]{accountName});

        // Now add the ones in sessionIds.
        ArrayList<UserAction> actions = new ArrayList<UserAction>();
        if (userData.getStarredSessionIds() != null) {
            for (String sessionId : userData.getStarredSessionIds()) {
                UserAction action = new UserAction();
                action.type = UserAction.TYPE.ADD_STAR;
                action.sessionId = sessionId;
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
     * TODO (shailen): should not be part of a utility class.
     */
    public static class UserData {

        @SerializedName(JSON_ATTRIBUTE_STARRED_SESSIONS)
        private Set<String> starredSessionIds = new HashSet<String>();

        @SerializedName(JSON_ATTRIBUTE_FEEDBACK_SUBMITTED_SESSIONS)
        private Set<String> feedbackSubmittedSessionIds = new HashSet<String>();

        @SerializedName(JSON_ATTRIBUTE_VIEWED_VIDEOS)
        private Set<String> viewedVideoIds = new HashSet<String>();

        @SerializedName(JSON_ATTRIBUTE_GCM_KEY)
        private String gcmKey;

        public Set<String> getStarredSessionIds() {
            return starredSessionIds;
        }

        public void setStarredSessionIds(Set<String> starredSessionIds) {
            this.starredSessionIds = starredSessionIds;
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

        @Override
        public boolean equals(final Object obj) {
            if (!(obj instanceof UserData)) {
                return false;
            }
            if (obj == this) {
                return true;
            }

            final UserData other = (UserData) obj;
            return this.getGcmKey().equals(other.getGcmKey()) &&
                    this.getStarredSessionIds().equals(other.getStarredSessionIds()) &&
                    this.getViewedVideoIds().equals(other.getViewedVideoIds()) &&
                    this.getFeedbackSubmittedSessionIds().equals(
                            other.getFeedbackSubmittedSessionIds());
        }

        @Override
        public int hashCode() {
            int result = 17;
            int prime = 37;
            result = prime * result + ((this.starredSessionIds != null) ?
                    this.starredSessionIds.hashCode() : 0);
            result = prime * result + ((this.feedbackSubmittedSessionIds != null) ?
                    this.feedbackSubmittedSessionIds.hashCode() : 0);
            result = prime * result + ((this.viewedVideoIds != null) ?
                    this.viewedVideoIds.hashCode() : 0);
            result = prime * result + ((this.gcmKey != null) ?
                    this.gcmKey.hashCode() : 0);
            return result;
        }
    }
}
