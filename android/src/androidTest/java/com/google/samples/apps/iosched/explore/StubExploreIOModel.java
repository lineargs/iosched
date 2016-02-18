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

package com.google.samples.apps.iosched.explore;

import android.content.Context;
import android.database.Cursor;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.test.runner.AndroidJUnit4;

import com.google.samples.apps.iosched.archframework.Model;
import com.google.samples.apps.iosched.archframework.QueryEnum;

import org.junit.runner.RunWith;

/**
 * A stub {@link ExploreIOModel}, to be injected using {@link com.google.samples.apps.iosched
 * .injection.Injection}. It overrides {@link #requestData(QueryEnum, Model.DataQueryCallback)} to
 * bypass the loader manager mechanism. Use the classes in {@link com.google.samples.apps.iosched
 * .mockdata} to provide the stub cursors.
 */
@RunWith(AndroidJUnit4.class)
public class StubExploreIOModel extends ExploreIOModel {

    private Cursor mSessionsCursor;

    private Cursor mTagsCursor;

    public StubExploreIOModel(Context context, Cursor sessionsCursor, Cursor tagsCursor) {
        super(context, null, null);
        mSessionsCursor = sessionsCursor;
        mTagsCursor = tagsCursor;
    }

    /**
     * Overrides the loader manager mechanism by directly calling {@link #onLoadFinished(QueryEnum,
     * Cursor)} with a stub {@link Cursor} as provided in the constructor.
     */
    @Override
    public void requestData(final @NonNull ExploreIOModel.ExploreIOQueryEnum query,
            final @NonNull DataQueryCallback callback) {
        // Add the callback so it gets fired properly
        mDataQueryCallbacks.put(query, callback);

        Handler h = new Handler();
        Runnable r = new Runnable() {
            @Override
            public void run() {
                // Call onLoadFinished with stub cursor and query
                switch (query) {
                    case SESSIONS:
                        onLoadFinished(query, mSessionsCursor);
                        break;
                    case TAGS:
                        onLoadFinished(query, mTagsCursor);
                        break;
                }
            }
        };

        // Delayed to ensure the UI is ready, because it will fire the callback to update the view
        // very quickly
        h.postDelayed(r, 5);
    }
}
