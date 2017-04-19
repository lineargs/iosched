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
package com.google.samples.apps.iosched.fcm;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.text.TextUtils;

import com.google.samples.apps.iosched.lib.BuildConfig;
import com.google.samples.apps.iosched.util.AccountUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;

import static com.google.samples.apps.iosched.util.LogUtils.LOGD;
import static com.google.samples.apps.iosched.util.LogUtils.LOGE;
import static com.google.samples.apps.iosched.util.LogUtils.LOGI;
import static com.google.samples.apps.iosched.util.LogUtils.LOGV;
import static com.google.samples.apps.iosched.util.LogUtils.LOGW;
import static com.google.samples.apps.iosched.util.LogUtils.makeLogTag;

/**
 * Helper class used to communicate with the demo server.
 */
public final class ServerUtilities {
    private static final String TAG = makeLogTag("FCMs");

    private static final String PREFERENCES = "com.google.samples.apps.iosched.fcm";
    private static final String PROPERTY_REGISTERED_TS = "registered_ts";
    private static final String KEY_DEVICE_ID = "device_id";
    private static final String KEY_USER_ID = "user_id";
    private static final int MAX_ATTEMPTS = 5;
    private static final int BACKOFF_MILLI_SECONDS = 2000;

    private static final Random sRandom = new Random();

    private static boolean checkFcmEnabled() {
        //TODO(36700561) Check google-services.json instead of API keys.
        if (TextUtils.isEmpty(BuildConfig.FCM_SERVER_URL)) {
            LOGD(TAG, "FCM feature disabled (no URL configured)");
            return false;
        } else if (TextUtils.isEmpty(BuildConfig.FCM_API_KEY)) {
            LOGD(TAG, "FCM feature disabled (no API key configured)");
            return false;
        } else if (TextUtils.isEmpty(BuildConfig.FCM_SENDER_ID)) {
            LOGD(TAG, "FCM feature disabled (no sender ID configured)");
            return false;
        }
        return true;
    }

    /**
     * Register this account/device pair within the server.
     *
     * @param context Current context
     * @param deviceId   The FCM registration ID for this device
     * @param userId  The user identifier used to pair a user with an InstanceID token.
     * @return whether the registration succeeded or not.
     */
    public static boolean register(final Context context, final String deviceId,
                                   final String userId) {
        if (!checkFcmEnabled()) {
            return false;
        }

        LOGD(TAG, "registering device (reg_id = " + deviceId + ")");
        String serverUrl = BuildConfig.FCM_SERVER_URL + "/register";
        LOGI(TAG, "registering on FCM with FCM key: " + AccountUtils.sanitizeUserId(userId));

        Map<String, String> params = new HashMap<>();
        params.put(KEY_DEVICE_ID, deviceId);
        params.put(KEY_USER_ID, userId);
        long backoff = BACKOFF_MILLI_SECONDS + sRandom.nextInt(1000);
        // Once FCM returns a registration id, we need to register it in the
        // demo server. As the server might be down, we will retry it a couple
        // times.
        for (int i = 1; i <= MAX_ATTEMPTS; i++) {
            LOGV(TAG, "Attempt #" + i + " to register");
            try {
                post(serverUrl, params, BuildConfig.FCM_API_KEY);
                setRegisteredOnServer(context, true, deviceId, userId);
                return true;
            } catch (IOException e) {
                // Here we are simplifying and retrying on any error; in a real
                // application, it should retry only on unrecoverable errors
                // (like HTTP error code 503).
                LOGE(TAG, "Failed to register on attempt " + i, e);
                if (i == MAX_ATTEMPTS) {
                    break;
                }
                try {
                    LOGV(TAG, "Sleeping for " + backoff + " ms before retry");
                    Thread.sleep(backoff);
                } catch (InterruptedException e1) {
                    // Activity finished before we complete - exit.
                    LOGD(TAG, "Thread interrupted: abort remaining retries!");
                    Thread.currentThread().interrupt();
                    return false;
                }
                // increase backoff exponentially
                backoff *= 2;
            }
        }
        return false;
    }

    /**
     * Unregister this account/device pair within the server.
     *
     * @param deviceId  The InstanceID token for this application instance.
     * @param userId The user identifier used to pair a user with an InstanceID token.
     */
    public static void unregister(final String deviceId, final String userId) {
        if (!checkFcmEnabled()) {
            return;
        }

        LOGI(TAG, "unregistering device (deviceId = " + deviceId + ")");
        String serverUrl = BuildConfig.FCM_SERVER_URL + "/unregister";
        Map<String, String> params = new HashMap<>();
        params.put(KEY_USER_ID, userId);
        params.put(KEY_DEVICE_ID, deviceId);
        try {
            post(serverUrl, params, BuildConfig.FCM_API_KEY);
        } catch (IOException e) {
            // At this point the device is unregistered from FCM, but still
            // registered on the server.
            // We could try to unregister again, but it is not necessary:
            // if the server tries to send a message to the device, it will get
            // a "NotRegistered" error message and should unregister the device.
            LOGD(TAG, "Unable to unregister from application server", e);
        }
    }

    /**
     * Request user data sync.
     *
     * @param context Current context
     */
    public static void notifyUserDataChanged(final Context context) {
        if (!checkFcmEnabled()) {
            return;
        }

        LOGI(TAG, "Notifying FCM that user data changed");
        String serverUrl = BuildConfig.FCM_SERVER_URL + "/send/self/sync_user";
        try {
            String fcmKey =
                    AccountUtils.getFcmKey(context, AccountUtils.getActiveAccountName(context));
            if (fcmKey != null) {
                post(serverUrl, new HashMap<String, String>(), fcmKey);
            }
        } catch (IOException e) {
            LOGE(TAG, "Unable to notify FCM about user data change", e);
        }
    }

    /**
     * Sets whether the device was successfully registered in the server side.
     *
     * @param context Current context
     * @param flag    True if registration was successful, false otherwise
     * @param deviceId   InstanceID token generated to represent the current instance of the
     *                Application.
     * @param userId  User identifier paired with deviceId on server
     */
    static void setRegisteredOnServer(Context context, boolean flag, String deviceId,
            String userId) {
        final SharedPreferences prefs = context.getSharedPreferences(
                PREFERENCES, Context.MODE_PRIVATE);
        LOGD(TAG, "Setting registered on server status as: " + flag + ", fcmKey="
                + AccountUtils.sanitizeUserId(userId));
        Editor editor = prefs.edit();
        if (flag) {
            editor.putLong(PROPERTY_REGISTERED_TS, new Date().getTime());
            editor.putString(KEY_USER_ID, userId == null ? "" : userId);
            editor.putString(KEY_DEVICE_ID, deviceId);
        } else {
            editor.remove(KEY_DEVICE_ID);
        }
        editor.apply();
    }

    /**
     * Checks whether the device was successfully registered in the server side.
     *
     * @param context Current context
     * @return True if registration was successful, false otherwise
     */
    static boolean isRegisteredOnServer(Context context, String userId) {
        final SharedPreferences prefs = context.getSharedPreferences(
                PREFERENCES, Context.MODE_PRIVATE);
        // Find registration threshold
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, -1);
        long yesterdayTS = cal.getTimeInMillis();
        long regTS = prefs.getLong(PROPERTY_REGISTERED_TS, 0);

        userId = userId == null ? "" : userId;

        if (regTS > yesterdayTS) {
            LOGV(TAG, "FCM registration current. regTS=" + regTS + " yesterdayTS=" + yesterdayTS);

            final String registeredUserId = prefs.getString(KEY_USER_ID, "");
            if (registeredUserId.equals(userId)) {
                LOGD(TAG, "FCM registration is valid and for the correct fcm key: "
                        + AccountUtils.sanitizeUserId(registeredUserId));
                return true;
            }
            LOGD(TAG, "FCM registration is for DIFFERENT FCM key "
                    + AccountUtils.sanitizeUserId(registeredUserId) + ". We were expecting "
                    + AccountUtils.sanitizeUserId(userId));
            return false;
        } else {
            LOGV(TAG, "FCM registration expired. regTS=" + regTS + " yesterdayTS=" + yesterdayTS);
            return false;
        }
    }

    public static String getDeviceId(Context context) {
        final SharedPreferences prefs =
                context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        return prefs.getString(KEY_DEVICE_ID, null);
    }

    /**
     * Issue a POST request to the server.
     *
     * @param endpoint POST address.
     * @param params   request parameters.
     * @throws java.io.IOException propagated from POST.
     */
    private static void post(String endpoint, Map<String, String> params, String key)
            throws IOException {
        URL url;
        try {
            url = new URL(endpoint);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("invalid url: " + endpoint);
        }
        params.put("key", key);
        StringBuilder bodyBuilder = new StringBuilder();
        Iterator<Entry<String, String>> iterator = params.entrySet().iterator();
        // constructs the POST body using the parameters
        while (iterator.hasNext()) {
            Entry<String, String> param = iterator.next();
            bodyBuilder.append(param.getKey()).append('=')
                    .append(param.getValue());
            if (iterator.hasNext()) {
                bodyBuilder.append('&');
            }
        }
        String body = bodyBuilder.toString();
        LOGW(TAG, "Posting to " + url);
        LOGV(TAG, "Posting '" + body + "'");
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setUseCaches(false);
            conn.setChunkedStreamingMode(0);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type",
                    "application/x-www-form-urlencoded;charset=UTF-8");
            conn.setRequestProperty("Content-Length",
                    Integer.toString(body.length()));
            // post the request
            OutputStream out = conn.getOutputStream();
            out.write(body.getBytes());
            out.close();
            // handle the response
            int status = conn.getResponseCode();
            if (status != 200) {
                throw new IOException("Post failed with error code " + status);
            }
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}
