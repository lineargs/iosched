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

package com.google.samples.apps.iosched.videolibrary;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.v4.util.SparseArrayCompat;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.samples.apps.iosched.R;
import com.google.samples.apps.iosched.archframework.PresenterImpl;
import com.google.samples.apps.iosched.archframework.UpdatableView;
import com.google.samples.apps.iosched.injection.ModelProvider;
import com.google.samples.apps.iosched.provider.ScheduleContract;
import com.google.samples.apps.iosched.ui.widget.DrawShadowFrameLayout;
import com.google.samples.apps.iosched.ui.widget.recyclerview.ItemMarginDecoration;
import com.google.samples.apps.iosched.ui.widget.recyclerview.UpdatableAdapter;
import com.google.samples.apps.iosched.util.AnalyticsHelper;
import com.google.samples.apps.iosched.util.ImageLoader;
import com.google.samples.apps.iosched.util.UIUtils;
import com.google.samples.apps.iosched.videolibrary.VideoLibraryModel.VideoLibraryQueryEnum;
import com.google.samples.apps.iosched.videolibrary.VideoLibraryModel.VideoLibraryUserActionEnum;
import com.google.samples.apps.iosched.videolibrary.data.VideoTrack;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import static com.google.samples.apps.iosched.util.LogUtils.makeLogTag;

/**
 * This Fragment displays all the videos of past Google I/O sessions in the form of a card for each
 * topics and a card for new videos of the current year.
 */
public class VideoLibraryFragment extends Fragment
        implements UpdatableView<VideoLibraryModel, VideoLibraryQueryEnum,
        VideoLibraryUserActionEnum> {

    private static final String TAG = makeLogTag(VideoLibraryFragment.class);

    private static final String VIDEO_LIBRARY_ANALYTICS_CATEGORY = "Video Library";

    private ImageLoader mImageLoader;

    private RecyclerView mCardList = null;

    private VideosAdapter mAdapter;

    private View mEmptyView = null;

    private List<UserActionListener> mListeners = new ArrayList<>();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.video_library_frag, container, false);
        mCardList = (RecyclerView) root.findViewById(R.id.videos_card_list);
        final int cardVerticalMargin = getResources().getDimensionPixelSize(R.dimen.spacing_normal);
        mCardList.addItemDecoration(new ItemMarginDecoration(0, cardVerticalMargin,
                0, cardVerticalMargin));
        mEmptyView = root.findViewById(android.R.id.empty);
        getActivity().overridePendingTransition(0, 0);

        return root;
    }

    @Override
    public void displayData(final VideoLibraryModel model,
            final VideoLibraryQueryEnum query) {
        if ((VideoLibraryModel.VideoLibraryQueryEnum.VIDEOS == query
                || VideoLibraryModel.VideoLibraryQueryEnum.MY_VIEWED_VIDEOS == query)
                && model.hasVideos()) {

            if (mAdapter == null) {
                mAdapter = new VideosAdapter(getActivity(), model, mImageLoader, mListeners);
                mCardList.setAdapter(mAdapter);
            } else {
                mAdapter.update(model);
            }
            mEmptyView.setVisibility(View.GONE);
        } else {
            mEmptyView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void displayErrorMessage(final VideoLibraryQueryEnum query) {
        // No UI changes upon query error
    }

    @Override
    public void displayUserActionResult(final VideoLibraryModel model,
            final VideoLibraryUserActionEnum userAction, final boolean success) {
        // All user actions handled in model
    }

    @Override
    public Uri getDataUri(final VideoLibraryQueryEnum query) {
        switch (query) {
            case VIDEOS:
                return ScheduleContract.Videos.CONTENT_URI;
            case MY_VIEWED_VIDEOS:
                return ScheduleContract.MyViewedVideos.CONTENT_URI;
            default:
                return Uri.EMPTY;
        }
    }

    @Override
    public Context getContext() {
        return getActivity();
    }

    @Override
    public void addListener(UserActionListener toAdd) {
        mListeners.add(toAdd);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mImageLoader = new ImageLoader(getActivity(), android.R.color.transparent);
        initPresenter();
    }

    private void initPresenter() {
        VideoLibraryModel model = ModelProvider
                .provideVideoLibraryModel(getDataUri(VideoLibraryQueryEnum.VIDEOS),
                        getDataUri(VideoLibraryQueryEnum.MY_VIEWED_VIDEOS),
                        getDataUri(VideoLibraryQueryEnum.FILTERS), getActivity(),
                        getLoaderManager());
        PresenterImpl presenter =
                new PresenterImpl(model, this, VideoLibraryUserActionEnum.values(),
                        VideoLibraryQueryEnum.values());
        presenter.loadInitialQueries();

        addListener(presenter);
    }

    private void setContentTopClearance(int clearance) {
        if (mCardList != null) {
            mCardList.setPadding(mCardList.getPaddingLeft(), clearance,
                    mCardList.getPaddingRight(), mCardList.getPaddingBottom());
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        getActivity().invalidateOptionsMenu();

        // Configure video fragment's top clearance to take our overlaid controls (Action Bar
        // and spinner box) into account.
        int actionBarSize = UIUtils.calculateActionBarSize(getActivity());
        DrawShadowFrameLayout drawShadowFrameLayout =
                (DrawShadowFrameLayout) getActivity().findViewById(R.id.main_content);
        if (drawShadowFrameLayout != null) {
            drawShadowFrameLayout.setShadowTopOffset(actionBarSize);
        }
        setContentTopClearance(actionBarSize);
    }

    /**
     * An adapter for providing data for the main list in this fragment. This shows a card per
     * video track. Each card contains a scrolling list of videos within that track or can lead
     * to a details screen showing an expanded view of the track.
     */
    private static class VideosAdapter
            extends UpdatableAdapter<VideoLibraryModel, VideoTrackViewHolder> {

        // Immutable state
        private final Activity mHost;

        private final LayoutInflater mInflater;

        private final ImageLoader mImageLoader;

        private final List<UserActionListener> mListeners;

        // State
        private List<VideoTrack> mVideoTracks;

        private SparseArrayCompat<VideoTrackAdapter> mTrackVideosAdapters;

        private SparseArrayCompat<Parcelable> mTrackVideosState;

        VideosAdapter(@NonNull Activity activity,
                @NonNull VideoLibraryModel model,
                @NonNull ImageLoader imageLoader,
                @NonNull List<UserActionListener> listeners) {
            mHost = activity;
            mInflater = LayoutInflater.from(activity);
            mImageLoader = imageLoader;
            mListeners = listeners;
            mVideoTracks = processVideos(model);
            setupVideoTrackAdapters();
        }

        @Override
        public VideoTrackViewHolder onCreateViewHolder(final ViewGroup parent, final int viewType) {
            final VideoTrackViewHolder holder = new VideoTrackViewHolder(
                    mInflater.inflate(R.layout.explore_io_track_card, parent, false));
            ViewCompat.setImportantForAccessibility(
                    holder.videos, ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            holder.header.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(final View v) {
                    final VideoTrack videoTrack = mVideoTracks.get(holder.getAdapterPosition());
                    // ANALYTICS EVENT: Click on the "More" button of a card in the Video Library
                    // Contains: The clicked header's label
                    AnalyticsHelper.sendEvent(VIDEO_LIBRARY_ANALYTICS_CATEGORY, "morebutton",
                            videoTrack.getTrack());
                    // Start the Filtered Video Library intent.
                    Intent filtered = new Intent(mHost, VideoLibraryFilteredActivity.class);
                    if (videoTrack.getTrackId() == VideoLibraryModel.TRACK_ID_KEYNOTES) {
                        filtered.putExtra(VideoLibraryFilteredActivity.KEY_FILTER_TOPIC,
                                VideoLibraryModel.KEYNOTES_TOPIC);
                    } else if (videoTrack.getTrackId() == VideoLibraryModel.TRACK_ID_NEW) {
                        filtered.putExtra(VideoLibraryFilteredActivity.KEY_FILTER_YEAR,
                                Calendar.getInstance().get(Calendar.YEAR));
                    } else {
                        filtered.putExtra(VideoLibraryFilteredActivity.KEY_FILTER_TOPIC,
                                videoTrack.getTrack());
                    }
                    mHost.startActivity(filtered);
                }
            });
            return holder;
        }

        @Override
        public void onBindViewHolder(final VideoTrackViewHolder holder, final int position) {
            final VideoTrack videoTrack = mVideoTracks.get(position);
            holder.title.setText(videoTrack.getTrack());
            holder.header.setContentDescription(videoTrack.getTrack());
            holder.videos.setAdapter(mTrackVideosAdapters.get(videoTrack.getTrackId()));
            holder.videos.getLayoutManager().onRestoreInstanceState(
                    mTrackVideosState.get(videoTrack.getTrackId()));
        }

        @Override
        public void onViewRecycled(final VideoTrackViewHolder holder) {
            // Cache the scroll position of the video list so that we can restore it in onBind
            final VideoTrack videoTrack = mVideoTracks.get(holder.getAdapterPosition());
            mTrackVideosState.put(videoTrack.getTrackId(),
                    holder.videos.getLayoutManager().onSaveInstanceState());
            super.onViewRecycled(holder);
        }

        @Override
        public int getItemCount() {
            return mVideoTracks.size();
        }

        @Override
        public void update(@NonNull final VideoLibraryModel updatedData) {
            // Attempt to update our model in-place to keep scroll position etc
            final List<VideoTrack> newVideos = processVideos(updatedData);
            boolean changed = false;
            if (newVideos.size() != mVideoTracks.size()) {
                changed = true;
            } else {
                for (int i = 0; i < newVideos.size(); i++) {
                    final VideoTrack newVideoTrack = newVideos.get(i);
                    final VideoTrack oldVideoTrack = mVideoTracks.get(i);
                    if (newVideoTrack.equals(oldVideoTrack)) {
                        mTrackVideosAdapters.get(newVideoTrack.getTrackId())
                                            .update(newVideoTrack.getVideos());
                    } else {
                        changed = true;
                        break;
                    }
                }
            }
            if (changed) {
                // Couldn't do an in-place update, do a full refresh
                mVideoTracks = newVideos;
                setupVideoTrackAdapters();
                notifyDataSetChanged();
            }
        }

        /**
         * Process the data for use; we get given a flat list of videos. Group them by track with
         * special handling of keynotes and new talks from this year. We assume that the provided
         * data is already sorted by track.
         * @param model
         */
        private List<VideoTrack> processVideos(VideoLibraryModel model) {
            final List<VideoTrack> data = new ArrayList<>();

            final VideoTrack keynoteVideos = model.getKeynoteVideos();
            if (keynoteVideos != null) {
                data.add(keynoteVideos);
            }

            final VideoTrack currentYearVideos = model.getCurrentYearVideos();
            if (currentYearVideos != null) {
                data.add(currentYearVideos);
            }

            final List<VideoTrack> videos = model.getVideos();
            if (videos != null && !videos.isEmpty()) {
                data.addAll(videos);
            }
            return data;
        }

        /**
         * Loop over {@link #mVideoTracks} and create adaptor & state objects for each track.
         */
        private void setupVideoTrackAdapters() {
            mTrackVideosAdapters = new SparseArrayCompat<>(mVideoTracks.size());
            mTrackVideosState = new SparseArrayCompat<>(mVideoTracks.size());

            for (final VideoTrack videoTrack : mVideoTracks) {
                mTrackVideosAdapters.put(videoTrack.getTrackId(),
                        VideoTrackAdapter.createHorizontal(mHost,
                                videoTrack.getVideos(), mImageLoader, mListeners));
            }
        }

    }

    private static class VideoTrackViewHolder extends RecyclerView.ViewHolder {

        final ViewGroup header;
        final TextView title;
        final RecyclerView videos;

        public VideoTrackViewHolder(final View itemView) {
            super(itemView);
            header = (ViewGroup) itemView.findViewById(R.id.header);
            title = (TextView) itemView.findViewById(R.id.title);
            videos = (RecyclerView) itemView.findViewById(R.id.sessions);
        }
    }

}
