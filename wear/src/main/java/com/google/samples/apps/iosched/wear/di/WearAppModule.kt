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

package com.google.samples.apps.iosched.wear.di

import android.content.Context
import com.google.samples.apps.iosched.wear.WearApplication
import dagger.Module
import dagger.Provides

/**
 * Defines all the classes that need to be provided in the scope of the wear app.
 *
 * Define here all objects that are shared throughout the wear app, like SharedPreferences,
 * navigators or others. If some of those objects are singletons, they should be annotated with
 * `@Singleton`.
 */
@Module
class WearAppModule {

    @Provides
    fun provideContext(application: WearApplication): Context {
        return application.applicationContext
    }
}
