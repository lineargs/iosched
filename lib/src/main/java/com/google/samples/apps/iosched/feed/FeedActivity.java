/*
 * Copyright (c) 2017 Google Inc.
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
package com.google.samples.apps.iosched.feed;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.widget.RecyclerView;

import com.google.samples.apps.iosched.lib.R;
import com.google.samples.apps.iosched.navigation.NavigationModel;
import com.google.samples.apps.iosched.ui.BaseActivity;

/**
 * This is the host Activity for a list of cards that present key updates on the conference.
 * The cards are shown in a {@link RecyclerView} and the content in each card comes from a Firebase
 * Real-Time Database.
 */
public class FeedActivity extends BaseActivity {

    private static final String SCREEN_LABEL = "Feed";

    private FeedContract.Presenter mPresenter;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.feed_act);
        final FeedFragment contentFragment = FeedFragment.getInstance(getSupportFragmentManager());
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        // Each fragment in the pager adapter is an updatable view that the presenter must know
        mPresenter = new FeedPresenter(sharedPreferences, contentFragment);
        contentFragment.setPresenter(mPresenter);
    }

    @Override
    protected NavigationModel.NavigationItemEnum getSelfNavDrawerItem() {
        return NavigationModel.NavigationItemEnum.FEED;
    }

    @Override
    public boolean canSwipeRefreshChildScrollUp() {
        return true;
    }

    @Override
    protected String getScreenLabel() {
        return SCREEN_LABEL;
    }
}
