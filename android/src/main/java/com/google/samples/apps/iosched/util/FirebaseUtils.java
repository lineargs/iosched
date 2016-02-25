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

package com.google.samples.apps.iosched.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.google.samples.apps.iosched.BuildConfig;

import java.util.zip.CRC32;

import static com.google.samples.apps.iosched.util.LogUtils.LOGI;
import static com.google.samples.apps.iosched.util.LogUtils.makeLogTag;

/**
 * Utility methods for setting up and interacting with a Firebase account associated with a user
 * account.
 */
public class FirebaseUtils {

    private static final String TAG = makeLogTag(AccountUtils.class);

    // These names are are prefixes; the account is appended to them.
    public static final String PREFIX_PREF_FIREBASE_UID = "firebase_uid_";
    public static final String PREFIX_PREF_FIREBASE_URL = "firebase_url_";

    /**
     * @param context Context used to lookup {@link SharedPreferences}.
     * @return The user id (UID) generated by Firebase.
     */
    public static String getFirebaseUid(final Context context) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        return sp.getString(
                AccountUtils.makeAccountSpecificPrefKey(context, PREFIX_PREF_FIREBASE_UID), "");
    }

    /**
     * Stores the UID generated by Firebase in {@link SharedPreferences}.
     *
     * @param context Context used to lookup {@link SharedPreferences}.
     * @param uid     The user id (UID) generated by Firebase.
     */
    public static void setFirebaseUid(final Context context, final String accountName,
            final String uid) {
        LOGI(TAG, "Saving Firebase UID for accountName " + accountName);
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        sp.edit().putString(
                AccountUtils.makeAccountSpecificPrefKey(context, PREFIX_PREF_FIREBASE_UID),
                uid).apply();
        // TODO: call setFirebaseUrl here.
    }

    /**
     * Retrieves the Firebase url associated with the current account stored in {@link
     * SharedPreferences}.
     *
     * @param context     Context used to lookup {@link SharedPreferences}.
     * @param accountName The account name associated with the chosen user account.
     * @return The Firebase UID associated with the current account.
     */
    public static String getFirebaseUrl(Context context, String accountName) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        return sp.getString(
                AccountUtils.makeAccountSpecificPrefKey(accountName,
                        PREFIX_PREF_FIREBASE_URL), "");
    }

    /**
     * Calculates the Firebase url associated with an account id generated by calling {@link
     * com.google.android.gms.auth.GoogleAuthUtil#getAccountId(Context, String)}.
     *
     * @param context   Context used to lookup {@link SharedPreferences}.
     * @param accountId The account ID associated with the currently chosen account.
     */
    public static void setFirebaseUrl(Context context, String accountId) {
        String[] firebaseUrls = BuildConfig.FIREBASE_URLS;
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        CRC32 crc = new CRC32();
        crc.update(accountId.getBytes());
        int index = (int) (crc.getValue() % firebaseUrls.length);
        LOGI(TAG, "Selected Firebase db # " + index);
        sp.edit().putString(
                AccountUtils.makeAccountSpecificPrefKey(context, PREFIX_PREF_FIREBASE_URL),
                firebaseUrls[index]).apply();
    }
}
