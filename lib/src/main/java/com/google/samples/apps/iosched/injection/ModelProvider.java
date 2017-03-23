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
package com.google.samples.apps.iosched.injection;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.support.v4.app.LoaderManager;

import com.google.samples.apps.iosched.archframework.Model;
import com.google.samples.apps.iosched.explore.ExploreIOModel;
import com.google.samples.apps.iosched.feedback.FeedbackHelper;
import com.google.samples.apps.iosched.feedback.SessionFeedbackModel;
import com.google.samples.apps.iosched.model.ScheduleHelper;
import com.google.samples.apps.iosched.myio.MyIOModel;
import com.google.samples.apps.iosched.myschedule.MyScheduleModel;
import com.google.samples.apps.iosched.session.SessionDetailModel;
import com.google.samples.apps.iosched.util.SessionsHelper;

/**
 * Provides a way to inject stub classes when running integration tests.
 */
public class ModelProvider {

    // These are all only used for instrumented tests
    @SuppressLint("StaticFieldLeak")
    private static SessionDetailModel stubSessionDetailModel = null;

    @SuppressLint("StaticFieldLeak")
    private static MyScheduleModel stubMyScheduleModel = null;

    @SuppressLint("StaticFieldLeak")
    private static MyIOModel stubMyIOModel = null;

    @SuppressLint("StaticFieldLeak")
    private static SessionFeedbackModel stubSessionFeedbackModel = null;

    @SuppressLint("StaticFieldLeak")
    private static ExploreIOModel stubExploreIOModel = null;

    public static SessionDetailModel provideSessionDetailModel(Uri sessionUri, Context context,
            SessionsHelper sessionsHelper, LoaderManager loaderManager) {
        if (stubSessionDetailModel != null) {
            return stubSessionDetailModel;
        } else {
            return new SessionDetailModel(sessionUri, context, sessionsHelper, loaderManager);
        }
    }

    public static MyScheduleModel provideMyScheduleModel(ScheduleHelper scheduleHelper,
            SessionsHelper sessionsHelper, Context context) {
        MyScheduleModel model = stubMyScheduleModel != null
                ? stubMyScheduleModel
                : new MyScheduleModel(scheduleHelper, sessionsHelper, context);
        model.initStaticDataAndObservers();
        return model;
    }

    public static SessionFeedbackModel provideSessionFeedbackModel(Uri sessionUri, Context context,
            FeedbackHelper feedbackHelper, LoaderManager loaderManager) {
        if (stubSessionFeedbackModel != null) {
            return stubSessionFeedbackModel;
        } else {
            return new SessionFeedbackModel(loaderManager, sessionUri, context, feedbackHelper);
        }
    }

    public static ExploreIOModel provideExploreIOModel(Uri sessionsUri, Context context,
            LoaderManager loaderManager) {
        if (stubExploreIOModel != null) {
            return stubExploreIOModel;
        } else {
            return new ExploreIOModel(context, sessionsUri, loaderManager);
        }
    }

    public static void setStubModel(Model model) {
        if (model instanceof ExploreIOModel) {
            stubExploreIOModel = (ExploreIOModel) model;
        } else if (model instanceof SessionFeedbackModel) {
            stubSessionFeedbackModel = (SessionFeedbackModel) model;
        } else if (model instanceof SessionDetailModel) {
            stubSessionDetailModel = (SessionDetailModel) model;
        }
        if (model instanceof MyScheduleModel) {
            stubMyScheduleModel = (MyScheduleModel) model;
        }
    }

}
