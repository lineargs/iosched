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

package com.google.samples.apps.iosched.shared.data.userevent

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import com.google.samples.apps.iosched.shared.data.BootstrapConferenceDataSource
import com.google.samples.apps.iosched.shared.firestore.entity.ReservationRequestResult
import com.google.samples.apps.iosched.shared.firestore.entity.ReservationRequestResult.ReservationRequestStatus.RESERVE_SUCCEEDED
import com.google.samples.apps.iosched.shared.firestore.entity.UserEvent
import com.google.samples.apps.iosched.shared.model.Session
import com.google.samples.apps.iosched.shared.result.Result

/**
 * Returns data loaded from a local JSON file for development and testing.
 */
object FakeUserEventDataSource : UserEventDataSource {

    private val conferenceData = BootstrapConferenceDataSource.getOfflineConferenceData()!!
    private val userEvents = ArrayList<UserEvent>()

    init {
        conferenceData.sessions.forEachIndexed {i, session ->
            val reservation = ReservationRequestResult(RESERVE_SUCCEEDED,
                    System.currentTimeMillis())
            if (i in 1..50) {
                userEvents.add(UserEvent(session.id,
                        isStarred = i % 2 == 0,
                        startTime = session.startTime.toInstant().toEpochMilli(),
                        endTime = session.endTime.toInstant().toEpochMilli(),
                        isReviewed = i % 3 == 0,
                        reservation = reservation))
            }
        }
    }

    override fun getObservableUserEvents(userId: String): LiveData<UserEventsResult> {
        val result = MutableLiveData<UserEventsResult>()
        result.postValue(UserEventsResult(true, userEvents))
        return result
    }

    override fun updateStarred(userId: String, session: Session, isStarred: Boolean):
            LiveData<Result<Boolean>>{
        val result = MutableLiveData<Result<Boolean>>()
        result.postValue(Result.Success(true))
        return result
    }

    override fun getUserEvents(userId: String): List<UserEvent> {
        return userEvents
    }
}
