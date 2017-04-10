/*
 * Copyright 2014 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.samples.apps.iosched.myschedule;

import android.app.Activity;
import android.content.Intent;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ViewSwitcher;

import com.airbnb.lottie.LottieAnimationView;
import com.google.samples.apps.iosched.Config;
import com.google.samples.apps.iosched.archframework.UpdatableView;
import com.google.samples.apps.iosched.feedback.SessionFeedbackActivity;
import com.google.samples.apps.iosched.lib.R;
import com.google.samples.apps.iosched.model.TagMetadata;
import com.google.samples.apps.iosched.model.TagMetadata.Tag;
import com.google.samples.apps.iosched.myschedule.MyScheduleModel.MyScheduleQueryEnum;
import com.google.samples.apps.iosched.myschedule.MyScheduleModel.MyScheduleUserActionEnum;
import com.google.samples.apps.iosched.myschedule.ScheduleItemViewHolder.Callbacks;
import com.google.samples.apps.iosched.provider.ScheduleContract.Sessions;
import com.google.samples.apps.iosched.util.TimeUtils;

/**
 * This is used by the {@link android.support.v4.view.ViewPager} used by the narrow layout in {@link
 * MyScheduleActivity}. It is a {@link ListFragment} that shows schedule items for a day, using
 * {@link MyScheduleDayAdapter} as its data source.
 */
public class MyScheduleSingleDayFragment extends Fragment
        implements UpdatableView<MyScheduleModel, MyScheduleQueryEnum, MyScheduleUserActionEnum>,
        LoaderCallbacks<Cursor>, Callbacks {

    private static final int TAG_METADATA_TOKEN = 0x8;

    private TagMetadata mTagMetadata;

    /**
     * This is 1 for the first day of the conference, 2 for the second, and so on, and {@link
     * MyScheduleModel#PRE_CONFERENCE_DAY_ID} for the preconference day
     */
    private int mDayId = 1;

    private ViewSwitcher mLoadingSwitcher;
    private LottieAnimationView mLoadingView;
    private RecyclerView mRecyclerView;
    private MyScheduleDayAdapter mViewAdapter;

    private UserActionListener<MyScheduleUserActionEnum> mListener;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        getLoaderManager().initLoader(TAG_METADATA_TOKEN, null, this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.my_schedule_singleday_frag, container, false);
        mLoadingSwitcher = (ViewSwitcher) view;
        mLoadingView = (LottieAnimationView) view.findViewById(R.id.loading_anim);
        mRecyclerView = (RecyclerView) view.findViewById(android.R.id.list);
        mRecyclerView.setHasFixedSize(true);
        return view;
    }

    public RecyclerView getRecyclerView() {
        return mRecyclerView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mListener != null) {
            mListener.onUserAction(MyScheduleUserActionEnum.RELOAD_DATA, null);
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        initViews();
    }

    private void initViews() {
        mDayId = getArguments().getInt(MyScheduleActivity.ARG_CONFERENCE_DAY_INDEX, 0);

        // Set id to list view, so it can be referred to from tests
        TypedArray ids = getResources().obtainTypedArray(R.array.myschedule_listview_ids);
        int listViewId = ids.getResourceId(mDayId, 0);
        ids.recycle();
        mRecyclerView.setId(listViewId);
    }

    @Override
    public void displayData(MyScheduleModel model, MyScheduleQueryEnum query) {
        switch (query) {
            case SCHEDULE:
                updateSchedule(model);
                break;
        }
    }

    @Override
    public void displayErrorMessage(MyScheduleQueryEnum query) {
        // Not showing any error
    }

    @Override
    public void displayUserActionResult(MyScheduleModel model, MyScheduleUserActionEnum userAction,
            boolean success) {
        switch (userAction) {
            case RELOAD_DATA:
            case SESSION_STAR:
            case SESSION_UNSTAR:
                updateSchedule(model);
                break;
            case SESSION_SLOT:
                break;
            case FEEDBACK:
                break;
        }
    }

    private void updateSchedule(MyScheduleModel model) {
        if (isVisible()) {
            showSchedule();
            if (mViewAdapter == null) {
                mViewAdapter = new MyScheduleDayAdapter(this, mTagMetadata, true);
            }
            mViewAdapter.updateItems(model.getConferenceDataForDay(mDayId));
            if (mRecyclerView.getAdapter() == null) {
                mRecyclerView.setAdapter(mViewAdapter);
            }

            if (isShowingCurrentDay()) {
                LinearLayoutManager lm = (LinearLayoutManager) mRecyclerView.getLayoutManager();
                if (lm.findFirstVisibleItemPosition() == 0) {
                    // If we're showing the current day and we're still showing the first pos, move
                    // to the current time slot
                    moveToCurrentTimeSlot(false);
                }
            }
        }
    }

    private void moveToCurrentTimeSlot(boolean animate) {
        final long now = TimeUtils.getCurrentTime(getContext());
        final int pos = mViewAdapter.findTimeHeaderPositionForTime(now);
        if (pos >= 0) {
            if (animate) {
                mRecyclerView.smoothScrollToPosition(pos);
            } else {
                mRecyclerView.scrollToPosition(pos);
            }
        }
    }

    private boolean isShowingCurrentDay() {
        final long now = TimeUtils.getCurrentTime(getContext());
        return mDayId > 0 && now >= Config.CONFERENCE_DAYS[mDayId - 1][0]
                && now <= Config.CONFERENCE_DAYS[mDayId - 1][1];
    }

    public void resetListPosition() {
        if (isShowingCurrentDay()) {
            moveToCurrentTimeSlot(true);
        } else {
            // Else scroll to the first item
            mRecyclerView.smoothScrollToPosition(0);
        }
    }

    @Override
    public Uri getDataUri(MyScheduleQueryEnum query) {
        // Not used by the model
        return null;
    }

    @Override
    public void addListener(UserActionListener<MyScheduleUserActionEnum> listener) {
        mListener = listener;
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        switch (id) {
            case TAG_METADATA_TOKEN:
                return TagMetadata.createCursorLoader(getActivity());
            default:
                return null;
        }
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
        switch (loader.getId()) {
            case TAG_METADATA_TOKEN:
                mTagMetadata = new TagMetadata(cursor);
                if (mViewAdapter != null) {
                    mViewAdapter.setTagMetadata(mTagMetadata);
                }
                break;
        }
    }

    @Override
    public void onLoaderReset(final Loader<Cursor> loader) {
    }

    // -- Adapter callbacks

    @Override
    public void onSessionClicked(String sessionId) {
        Bundle args = new Bundle();
        Uri sessionUri = Sessions.buildSessionUri(sessionId);
        args.putString(MyScheduleModel.SESSION_URL_KEY, sessionUri.toString());
        mListener.onUserAction(MyScheduleUserActionEnum.SESSION_SLOT, args);

        startActivity(new Intent(Intent.ACTION_VIEW, sessionUri));
    }

    @Override
    public boolean bookmarkingEnabled() {
        return true;
    }

    @Override
    public void onBookmarkClicked(String sessionId, boolean isInSchedule) {
        MyScheduleUserActionEnum action = isInSchedule
                ? MyScheduleUserActionEnum.SESSION_UNSTAR
                : MyScheduleUserActionEnum.SESSION_STAR;
        Bundle args = new Bundle();
        args.putString(MyScheduleModel.SESSION_ID_KEY, sessionId);
        mListener.onUserAction(action, args);
    }

    @Override
    public void onFeedbackClicked(String sessionId, String sessionTitle) {
        Bundle args = new Bundle();
        args.putString(MyScheduleModel.SESSION_ID_KEY, sessionId);
        args.putString(MyScheduleModel.SESSION_TITLE_KEY, sessionTitle);
        mListener.onUserAction(MyScheduleUserActionEnum.FEEDBACK, args);

        SessionFeedbackActivity.launchFeedback(getContext(), sessionId);
    }

    @Override
    public void onTagClicked(Tag tag) {
        Activity activity = getActivity();
        if (activity instanceof ScheduleViewParent) {
            ((ScheduleViewParent) activity).onRequestFilterByTag(tag);
        }
    }

    private void showSchedule() {
        mLoadingView.cancelAnimation();
        mLoadingSwitcher.setDisplayedChild(1);
    }
}
