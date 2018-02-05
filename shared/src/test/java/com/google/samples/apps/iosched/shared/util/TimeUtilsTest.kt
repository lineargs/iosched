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

package com.google.samples.apps.iosched.shared.util

import com.google.samples.apps.iosched.shared.model.Room
import com.google.samples.apps.iosched.shared.model.Session
import com.google.samples.apps.iosched.shared.util.TimeUtils.ConferenceDay
import com.google.samples.apps.iosched.shared.util.TimeUtils.ConferenceDay.DAY_1
import org.junit.Assert
import org.junit.Before
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.threeten.bp.ZonedDateTime

class TimeUtilsTest {

    private lateinit var time0800: ZonedDateTime
    private lateinit var time1000: ZonedDateTime
    private lateinit var time1300: ZonedDateTime

    @Before fun setup() {
        time0800 = ZonedDateTime.parse("2018-05-08T08:00:00.000-08:00[America/Los_Angeles]")
        time1000 = ZonedDateTime.parse("2018-05-08T10:00:00.000-08:00[America/Los_Angeles]")
        time1300 = ZonedDateTime.parse("2018-05-08T13:00:00.000-08:00[America/Los_Angeles]")
    }

    @Test
    fun conferenceDay_contains() {
        val room1 = Room(id = "1", name = "Tent 1", capacity = 40)

        val inDay1 = Session("1", ConferenceDay.DAY_1.start, ConferenceDay.DAY_1.end,
                "", "", room1, "", "", "", emptyList(), emptySet(), "", emptySet())
        assertTrue(DAY_1.contains(inDay1))

        // Starts before DAY_1
        val day1MinusMinute = ConferenceDay.DAY_1.start.minusMinutes(1)
        val notInDay1 = Session("2", day1MinusMinute, ConferenceDay.DAY_1.end,
                "", "", room1, "", "", "", emptyList(), emptySet(), "", emptySet())
        assertFalse(DAY_1.contains(notInDay1))

        // Ends after DAY_1
        val day1PlusMinute = ConferenceDay.DAY_1.end.plusMinutes(1)
        val alsoNotInDay1 = Session("3", ConferenceDay.DAY_1.start, day1PlusMinute,
                "", "", room1, "", "", "", emptyList(), emptySet(), "", emptySet())
        assertFalse(DAY_1.contains(alsoNotInDay1))
    }

    @Test fun timeString_sameMeridiem() {
        Assert.assertEquals("Tue, May 8, 10:00 - 11:00 AM",
                TimeUtils.timeString(time1000, time1000.plusHours(1)))
        Assert.assertEquals("Tue, May 8, 1:00 - 2:00 PM",
                TimeUtils.timeString(time1300, time1300.plusHours(1)))
    }

    @Test fun timeString_differentMeridiem() {
        Assert.assertEquals("Tue, May 8, 10:00 AM - 12:00 PM",
                TimeUtils.timeString(time1000, time1000.plusHours(2)))
    }

    @Test fun timeString_omitsLeadingZeroInDate() {
        Assert.assertEquals("Tue, May 8, 8:00 - 9:00 AM",
                TimeUtils.timeString(time0800, time0800.plusHours(1)))
        val timeMay10 = ZonedDateTime.parse("2018-05-10T13:00:00.000-08:00[America/Los_Angeles]")
        Assert.assertEquals("Thu, May 10, 1:00 - 2:00 PM",
                TimeUtils.timeString(timeMay10, timeMay10.plusHours(1)))
    }

    @Test fun timeString_omitsLeadingZeroInTime() {
        Assert.assertEquals("Tue, May 8, 8:00 - 9:00 AM",
                TimeUtils.timeString(time0800, time0800.plusHours(1)))
        Assert.assertEquals("Tue, May 8, 8:00 - 10:00 AM",
                TimeUtils.timeString(time0800, time1000))
        Assert.assertEquals("Tue, May 8, 12:00 - 1:00 PM",
                TimeUtils.timeString(time1300.minusHours(1), time1300))
    }
}
