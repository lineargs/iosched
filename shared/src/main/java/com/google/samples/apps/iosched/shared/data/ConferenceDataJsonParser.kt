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

package com.google.samples.apps.iosched.shared.data

import com.google.gson.GsonBuilder
import com.google.gson.JsonIOException
import com.google.gson.JsonSyntaxException
import com.google.samples.apps.iosched.shared.data.session.json.BlockDeserializer
import com.google.samples.apps.iosched.shared.data.session.json.SessionDeserializer
import com.google.samples.apps.iosched.shared.data.session.json.SessionTemp
import com.google.samples.apps.iosched.shared.data.session.json.TagDeserializer
import com.google.samples.apps.iosched.shared.model.Block
import com.google.samples.apps.iosched.shared.model.ConferenceData
import com.google.samples.apps.iosched.shared.model.Room
import com.google.samples.apps.iosched.shared.model.Session
import com.google.samples.apps.iosched.shared.model.Speaker
import com.google.samples.apps.iosched.shared.model.Tag
import java.io.InputStream

object ConferenceDataJsonParser {

    @Throws(JsonIOException::class, JsonSyntaxException::class)
    fun parseConferenceData(unprocessedSessionData: InputStream): ConferenceData {
        val jsonReader = com.google.gson.stream.JsonReader(unprocessedSessionData.reader())

        val gson = GsonBuilder()
                .registerTypeAdapter(SessionTemp::class.java, SessionDeserializer())
                .registerTypeAdapter(Block::class.java, BlockDeserializer())
                .registerTypeAdapter(Tag::class.java, TagDeserializer())
                .create()

        val tempData: TempConferenceData = gson.fromJson(jsonReader, TempConferenceData::class.java)
        return normalize(tempData)
    }

    /**
     * Adds nested objects like `session.tags` to `sessions`
     */
    private fun normalize(data: TempConferenceData): ConferenceData {
        val sessions: MutableList<Session> = mutableListOf()

        data.sessions.forEach { session: SessionTemp ->
            val newSession = Session(
                    id = session.id,
                    startTime = session.startTime,
                    endTime = session.endTime,
                    title = session.title,
                    abstract = session.abstract,
                    sessionUrl = session.sessionUrl,
                    liveStreamUrl = session.liveStreamUrl,
                    youTubeUrl = session.youTubeUrl,
                    tags = data.tags.filter { it.id in session.tags },
                    speakers = data.speakers.filter { it.id in session.speakers }.toSet(),
                    photoUrl = session.photoUrl,
                    relatedSessions = session.relatedSessions,
                    room = data.rooms.first { it.id == session.room }
            )
            sessions.add(newSession)
        }

        return ConferenceData(
                sessions = sessions,
                tags = data.tags,
                speakers = data.speakers,
                blocks = data.blocks,
                rooms = data.rooms,
                version = data.version)
    }
}

/**
 * Temporary data type for conference data where some collections are lists of IDs instead
 * of lists of domain objects.
 */
data class TempConferenceData(
        val blocks: List<Block>,
        val sessions: List<SessionTemp>,
        val speakers: List<Speaker>,
        val rooms: List<Room>,
        val tags: List<Tag>,
        val version: Int
)
