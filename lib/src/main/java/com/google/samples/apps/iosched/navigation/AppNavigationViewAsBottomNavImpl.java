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

package com.google.samples.apps.iosched.navigation;

import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.google.samples.apps.iosched.lib.R;
import com.google.samples.apps.iosched.navigation.NavigationModel.NavigationItemEnum;

import static com.google.samples.apps.iosched.util.LogUtils.LOGE;
import static com.google.samples.apps.iosched.util.LogUtils.makeLogTag;

public class AppNavigationViewAsBottomNavImpl extends AppNavigationViewAbstractImpl
        implements BottomNavigationView.OnNavigationItemSelectedListener {

    private static final String TAG = makeLogTag(AppNavigationViewAsBottomNavImpl.class);

    private static final int CONTENT_FADE_OUT_DURATION = 150;
    private static final int BOTTOM_NAV_SELECTED_DELAY = 240;

    private final Handler mHandler = new Handler();

    private final BottomNavigationView mNavigationView;

    public AppNavigationViewAsBottomNavImpl(final BottomNavigationView navigationView) {
        mNavigationView = navigationView;
    }

    @Override
    public void displayNavigationItems(final NavigationItemEnum[] items) {
        final Menu menu = mNavigationView.getMenu();
        for (NavigationItemEnum item : items) {
            final MenuItem menuItem = menu.findItem(item.getId());
            if (menuItem != null) {
                menuItem.setVisible(true);
                menuItem.setIcon(item.getIconResource());
                menuItem.setTitle(item.getTitleResource());
                if (item == mSelfItem) {
                    menuItem.setChecked(true);
                }
            } else {
                LOGE(TAG, "Menu Item for navigation item with title " +
                        (item.getTitleResource() != 0
                                ? mActivity.getResources().getString(item.getTitleResource())
                                : "") + "not found");
            }
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull final MenuItem item) {
        final NavigationItemEnum navItem = NavigationItemEnum.getById(item.getItemId());
        if (navItem != null && navItem != mSelfItem) {
            // Launch the target Activity after a short delay, to allow the close animation to play
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    itemSelected(navItem);
                }
            }, BOTTOM_NAV_SELECTED_DELAY);

            // Fade out the main content
            View mainContent = mActivity.findViewById(R.id.main_content);
            if (mainContent != null) {
                mainContent.animate().alpha(0).setDuration(CONTENT_FADE_OUT_DURATION);
            }

            // Return true so that BottomNavigationView updates itself to show the new item
            return true;
        }
        return false;
    }

    @Override
    public void setUpView() {
        mNavigationView.setOnNavigationItemSelectedListener(this);
    }

    @Override
    public void showNavigation() {
        // Don't need this I think
    }
}
