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

package com.google.samples.apps.iosched.wear.ui.schedule

import com.google.samples.apps.iosched.wear.ui.WearableFragment

/**
 * Lists the remaining sessions in a user's schedule based on the current time.
 */
class ScheduleFragment : WearableFragment() {
    override fun onUpdateAmbient() {
        // TODO(b/74259577): implement ambient UI
    }
    // TODO(b/74258141): Populate WearableRecycleView with schedule.
}
