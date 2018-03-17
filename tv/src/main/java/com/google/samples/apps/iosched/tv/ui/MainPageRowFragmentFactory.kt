/*
 * Copyright 2018 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.samples.apps.iosched.tv.ui

import android.support.v17.leanback.app.BrowseSupportFragment
import android.support.v17.leanback.widget.Row
import com.google.samples.apps.iosched.shared.util.TimeUtils.CONFERENCE_DAYS
import com.google.samples.apps.iosched.tv.ui.schedule.ScheduleFragment

/**
 * Creates a [ScheduleFragment] for a given conference day from the nav row.
 */
class MainPageRowFragmentFactory : BrowseSupportFragment.FragmentFactory<ScheduleFragment>() {

    override fun createFragment(rowObj: Any): ScheduleFragment {
        val row = rowObj as Row
        val ordinal = row.headerItem.id.toInt()
        if (ordinal in 0..CONFERENCE_DAYS.size) {
            val day = CONFERENCE_DAYS[ordinal]
            return ScheduleFragment.newInstance(day)
        }
        throw IllegalArgumentException("Invalid row selected: $rowObj")
    }
}
