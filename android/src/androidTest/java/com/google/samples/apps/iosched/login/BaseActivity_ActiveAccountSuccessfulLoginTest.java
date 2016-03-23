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

package com.google.samples.apps.iosched.login;

import android.support.test.InstrumentationRegistry;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.test.suitebuilder.annotation.LargeTest;

import com.google.samples.apps.iosched.R;
import com.google.samples.apps.iosched.explore.ExploreIOActivity;
import com.google.samples.apps.iosched.injection.LoginAndAuthProvider;
import com.google.samples.apps.iosched.settings.SettingsUtils;
import com.google.samples.apps.iosched.testutils.NavigationUtils;
import com.google.samples.apps.iosched.util.AccountUtils;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withText;

/**
 * UI tests for {@link com.google.samples.apps.iosched.ui.BaseActivity} with an active account and
 * the user successfully logs in.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class BaseActivity_ActiveAccountSuccessfulLoginTest {

    private static final String ACCOUNT_NAME = "geswjop";

    private StubLoginAndAuth mStubLoginAndAuth;

    @Rule
    public ActivityTestRule<ExploreIOActivity> mActivityRule =
            new ActivityTestRule<ExploreIOActivity>(ExploreIOActivity.class) {

                @Override
                protected void beforeActivityLaunched() {
                    // Make sure the EULA screen is not shown.
                    SettingsUtils.markTosAccepted(InstrumentationRegistry.getTargetContext(), true);

                    // Set active account
                    AccountUtils
                            .setActiveAccount(InstrumentationRegistry.getTargetContext(),
                                    ACCOUNT_NAME);

                    // Set stub login and auth as successful
                    mStubLoginAndAuth =
                            new StubLoginAndAuth(ACCOUNT_NAME, true, true);
                    LoginAndAuthProvider.setStubLoginAndAuth(mStubLoginAndAuth);
                }

                @Override
                protected void afterActivityLaunched() {
                    // Set up the activity as a listener of the stub login and auth
                    mStubLoginAndAuth.setListener(mActivityRule.getActivity());

                }
            };

    @Test
    public void accountName_IsDisplayed() {
        // Given navigation menu
        NavigationUtils.showNavigation();

        // Then the account name is shown
        onView(withText(ACCOUNT_NAME)).check(matches(isDisplayed()));
    }

    @Test
    public void mySchedule_IsDisplayed() {
        NavigationUtils.checkNavigationItemIsDisplayed(R.string.navdrawer_item_my_schedule);
    }
}
