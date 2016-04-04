/*
 * Copyright 2015 Google Inc. All rights reserved.
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

package com.google.samples.apps.iosched.explore;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.util.SparseArrayCompat;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.samples.apps.iosched.R;
import com.google.samples.apps.iosched.archframework.ModelWithLoaderManager;
import com.google.samples.apps.iosched.archframework.PresenterImpl;
import com.google.samples.apps.iosched.archframework.UpdatableView;
import com.google.samples.apps.iosched.explore.ExploreIOModel.ExploreIOQueryEnum;
import com.google.samples.apps.iosched.explore.ExploreIOModel.ExploreIOUserActionEnum;
import com.google.samples.apps.iosched.explore.data.ItemGroup;
import com.google.samples.apps.iosched.explore.data.LiveStreamData;
import com.google.samples.apps.iosched.explore.data.MessageData;
import com.google.samples.apps.iosched.explore.data.SessionData;
import com.google.samples.apps.iosched.injection.ModelProvider;
import com.google.samples.apps.iosched.provider.ScheduleContract;
import com.google.samples.apps.iosched.session.SessionDetailActivity;
import com.google.samples.apps.iosched.settings.ConfMessageCardUtils;
import com.google.samples.apps.iosched.settings.SettingsUtils;
import com.google.samples.apps.iosched.ui.widget.DrawShadowFrameLayout;
import com.google.samples.apps.iosched.ui.widget.recyclerview.ItemMarginDecoration;
import com.google.samples.apps.iosched.ui.widget.recyclerview.UpdatableAdapter;
import com.google.samples.apps.iosched.util.ImageLoader;
import com.google.samples.apps.iosched.util.ThrottledContentObserver;
import com.google.samples.apps.iosched.util.TimeUtils;
import com.google.samples.apps.iosched.util.UIUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import static com.google.samples.apps.iosched.settings.ConfMessageCardUtils
        .ConferencePrefChangeListener;

/**
 * Display the Explore I/O cards. There are three styles of cards, which are referred to as Groups
 * by the CollectionView implementation.
 * <p/>
 * <ul> <li>The live-streaming session card.</li> <li>Time sensitive message cards.</li> <li>Session
 * topic cards.</li> </ul>
 * <p/>
 * Only the final group of cards is dynamically loaded from a {@link
 * android.content.ContentProvider}.
 */
public class ExploreIOFragment extends Fragment
        implements UpdatableView<ExploreIOModel, ExploreIOQueryEnum, ExploreIOUserActionEnum> {

    /**
     * Used to load images asynchronously on a background thread.
     */
    private ImageLoader mImageLoader;

    /**
     * RecyclerView containing a stream of cards to display to the user.
     */
    private RecyclerView mCardList = null;

    /**
     * Adapter for providing data for the stream of cards.
     */
    private ExploreAdapter mAdapter;

    /**
     * Empty view displayed when {@code mCardList} is empty.
     */
    private View mEmptyView;

    private List<UserActionListener> mListeners = new ArrayList<>();

    private ThrottledContentObserver mSessionsObserver, mTagsObserver;

    private ConferencePrefChangeListener mConfMessagesAnswerChangeListener =
            new ConferencePrefChangeListener() {
                @Override
                protected void onPrefChanged(String key, boolean value) {
                    fireReloadEvent();
                }
            };

    private OnSharedPreferenceChangeListener mSettingsChangeListener =
            new OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
                        String key) {
                    if (SettingsUtils.PREF_DECLINED_WIFI_SETUP.equals(key)) {
                        fireReloadEvent();
                    }
                }
            };

    @Override
    public View onCreateView(LayoutInflater inflater,
            ViewGroup container,
            Bundle savedInstanceState) {
        final View root = inflater.inflate(R.layout.explore_io_frag, container, false);
        mCardList = (RecyclerView) root.findViewById(R.id.explore_card_list);
        final int cardVerticalMargin = getResources().getDimensionPixelSize(R.dimen.spacing_normal);
        mCardList.addItemDecoration(new ItemMarginDecoration(0, cardVerticalMargin,
                0, cardVerticalMargin));
        mEmptyView = root.findViewById(android.R.id.empty);
        getActivity().overridePendingTransition(0, 0);
        return root;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mImageLoader = new ImageLoader(getActivity(), R.drawable.io_logo);
        initPresenter();
    }

    @Override
    public void displayData(final ExploreIOModel model, final ExploreIOQueryEnum query) {
        // Only display data when the tag metadata is available.
        if (model.getTagTitles() != null) {
            if (mAdapter == null) {
                mAdapter = new ExploreAdapter(getActivity(), model, mImageLoader);
                mCardList.setAdapter(mAdapter);
            } else {
                mAdapter.update(model);
            }
            mEmptyView.setVisibility(mAdapter.getItemCount() > 0 ? View.GONE : View.VISIBLE);
        }
    }

    @Override
    public void displayErrorMessage(final ExploreIOQueryEnum query) {
        // No UI changes when error with query
    }

    @Override
    public void displayUserActionResult(final ExploreIOModel model,
            final ExploreIOUserActionEnum userAction, final boolean success) {
        // All user actions handled in model
    }

    @Override
    public Uri getDataUri(final ExploreIOQueryEnum query) {
        switch (query) {
            case SESSIONS:
                return ScheduleContract.Sessions.CONTENT_URI;
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

    private void initPresenter() {
        ExploreIOModel model = ModelProvider.provideExploreIOModel(
                getDataUri(ExploreIOQueryEnum.SESSIONS), getContext(),
                getLoaderManager());
        PresenterImpl presenter = new PresenterImpl(model, this,
                ExploreIOUserActionEnum.values(), ExploreIOQueryEnum.values());
        presenter.loadInitialQueries();
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

        // configure fragment's top clearance to take our overlaid controls (Action Bar
        // and spinner box) into account.
        int actionBarSize = UIUtils.calculateActionBarSize(getActivity());
        DrawShadowFrameLayout drawShadowFrameLayout =
                (DrawShadowFrameLayout) getActivity().findViewById(R.id.main_content);
        if (drawShadowFrameLayout != null) {
            drawShadowFrameLayout.setShadowTopOffset(actionBarSize);
        }
        setContentTopClearance(actionBarSize);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        // Register preference change listeners
        ConfMessageCardUtils.registerPreferencesChangeListener(getContext(),
                mConfMessagesAnswerChangeListener);
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
        sp.registerOnSharedPreferenceChangeListener(mSettingsChangeListener);

        // Register content observers
        mSessionsObserver = new ThrottledContentObserver(new ThrottledContentObserver.Callbacks() {
            @Override
            public void onThrottledContentObserverFired() {
                fireReloadEvent();
                fireReloadTagsEvent();
            }
        });
        mTagsObserver = new ThrottledContentObserver(new ThrottledContentObserver.Callbacks() {
            @Override
            public void onThrottledContentObserverFired() {
                fireReloadTagsEvent();
            }
        });

    }

    @Override
    public void onDetach() {
        super.onDetach();
        if (mConfMessagesAnswerChangeListener != null) {
            ConfMessageCardUtils.unregisterPreferencesChangeListener(getContext(),
                    mConfMessagesAnswerChangeListener);
        }
        if (mSettingsChangeListener != null) {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
            sp.unregisterOnSharedPreferenceChangeListener(mSettingsChangeListener);
        }
        getActivity().getContentResolver().unregisterContentObserver(mSessionsObserver);
        getActivity().getContentResolver().unregisterContentObserver(mTagsObserver);
    }

    /**
     * Let all UserActionListener know that the video list has been reloaded and that therefore we
     * need to display another random set of sessions.
     */
    private void fireReloadEvent() {
        if (!isAdded()) {
            return;
        }
        for (UserActionListener h1 : mListeners) {
            Bundle args = new Bundle();
            args.putInt(ModelWithLoaderManager.KEY_RUN_QUERY_ID,
                    ExploreIOModel.ExploreIOQueryEnum.SESSIONS.getId());
            h1.onUserAction(ExploreIOModel.ExploreIOUserActionEnum.RELOAD, args);
        }
    }

    private void fireReloadTagsEvent() {
        if (!isAdded()) {
            return;
        }
        for (UserActionListener h1 : mListeners) {
            Bundle args = new Bundle();
            args.putInt(ModelWithLoaderManager.KEY_RUN_QUERY_ID,
                    ExploreIOModel.ExploreIOQueryEnum.TAGS.getId());
            h1.onUserAction(ExploreIOModel.ExploreIOUserActionEnum.RELOAD, args);
        }
    }

    /**
     * Adapter for providing cards (Messages, Keynote, Live Stream and conference Tracks)
     * for the Explore fragment.
     */
    private static class ExploreAdapter
            extends UpdatableAdapter<ExploreIOModel, RecyclerView.ViewHolder> {

        private static final int TYPE_TRACK = 0;

        private static final int TYPE_MESSAGE = 1;

        private static final int TYPE_KEYNOTE = 2;

        private static final int TYPE_LIVE_STREAM = 3;

        private static final int LIVE_STREAM_TRACK_ID = R.string.live_now;

        // Immutable state
        private final Activity mHost;

        private final LayoutInflater mInflater;

        private final ImageLoader mImageLoader;

        // State
        private List mItems;

        private Map<String, String> mTitles;

        // Maps of state keyed on track id
        private SparseArrayCompat<UpdatableAdapter> mTrackSessionsAdapters;

        private SparseArrayCompat<Parcelable> mTrackSessionsState;

        ExploreAdapter(@NonNull Activity activity,
                       @NonNull ExploreIOModel model,
                       @NonNull ImageLoader imageLoader) {
            mHost = activity;
            mTitles = model.getTagTitles();
            mImageLoader = imageLoader;
            mInflater = LayoutInflater.from(activity);
            mItems = processModel(model);
            setupSessionAdapters(model);
        }

        public void update(@NonNull ExploreIOModel model) {
            // Attempt to update our data in-place so as not to lose scroll position etc.
            final List newItems = processModel(model);
            boolean changed = false;
            if (newItems.size() != mItems.size()) {
                changed = true;
            } else {
                for (int i = 0; i < newItems.size(); i++) {
                    final Object newCard = newItems.get(i);
                    final Object oldCard = mItems.get(i);
                    if (newCard.equals(oldCard)) {
                        if (newCard instanceof ItemGroup) {
                            final ItemGroup newTrack = (ItemGroup) newCard;
                            mTrackSessionsAdapters.get(getTrackId(newTrack))
                                                  .update(newTrack.getSessions());
                        }
                    } else {
                        changed = true;
                        break;
                    }
                }
            }
            if (changed) {
                // Couldn't update existing model, do a full refresh
                mItems = newItems;
                setupSessionAdapters(model);
                notifyDataSetChanged();
            }
        }

        @Override
        public int getItemViewType(final int position) {
            final Object item = mItems.get(position);
            if (item instanceof LiveStreamData) {
                return TYPE_LIVE_STREAM;
            } else if (item instanceof ItemGroup) {
                return TYPE_TRACK;
            } else if (item instanceof MessageData) {
                return TYPE_MESSAGE;
            } else if (item instanceof SessionData) {
                return TYPE_KEYNOTE;
            }
            throw new IllegalArgumentException("Unknown view type.");
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(final ViewGroup parent,
                final int viewType) {
            switch (viewType) {
                case TYPE_TRACK:
                    return createTrackViewHolder(parent);
                case TYPE_MESSAGE:
                    return createMessageViewHolder(parent);
                case TYPE_KEYNOTE:
                    return createKeynoteViewHolder(parent);
                case TYPE_LIVE_STREAM:
                    return createLiveStreamViewHolder(parent);
                default:
                    throw new IllegalArgumentException("Unknown view type.");
            }
        }

        @Override
        public void onBindViewHolder(final RecyclerView.ViewHolder holder, final int position) {
            switch (getItemViewType(position)) {
                case TYPE_TRACK:
                    bindTrack((TrackViewHolder) holder, (ItemGroup) mItems.get(position));
                    break;
                case TYPE_MESSAGE:
                    bindMessage((MessageViewHolder) holder, (MessageData) mItems.get(position));
                    break;
                case TYPE_KEYNOTE:
                    bindKeynote((KeynoteViewHolder) holder, (SessionData) mItems.get(position));
                    break;
                case TYPE_LIVE_STREAM:
                    bindLiveStream((TrackViewHolder) holder, (LiveStreamData) mItems.get(position));
                    break;
            }
        }

        @Override
        public void onViewRecycled(final RecyclerView.ViewHolder holder) {
            if (holder instanceof TrackViewHolder) {
                // Cache the scroll position of the session list so that we can restore it in onBind
                final TrackViewHolder trackHolder = (TrackViewHolder) holder;
                final int position = trackHolder.getAdapterPosition();
                if (position == RecyclerView.NO_POSITION) return;
                final int trackId = getTrackId((ItemGroup) mItems.get(position));
                mTrackSessionsState.put(trackId,
                        trackHolder.sessions.getLayoutManager().onSaveInstanceState());
            }
            super.onViewRecycled(holder);
        }

        @Override
        public int getItemCount() {
            return mItems.size();
        }

        private @NonNull TrackViewHolder createTrackViewHolder(final ViewGroup parent) {
            final TrackViewHolder holder = new TrackViewHolder(
                    mInflater.inflate(R.layout.explore_io_track_card, parent, false));
            ViewCompat.setImportantForAccessibility(
                    holder.sessions, ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            holder.header.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(final View v) {
                    final ItemGroup track = (ItemGroup) mItems.get(holder.getAdapterPosition());
                    final Intent intent = new Intent(mHost, ExploreSessionsActivity.class);
                    intent.putExtra(ExploreSessionsActivity.EXTRA_FILTER_TAG, track.getId());
                    ActivityCompat.startActivity(mHost, intent, null);
                }
            });
            return holder;
        }

        private @NonNull MessageViewHolder createMessageViewHolder(final ViewGroup parent) {
            final MessageViewHolder holder = new MessageViewHolder(
                    mInflater.inflate(R.layout.explore_io_message_card, parent, false));
            // Work with pre-existing infrastructure which supplied a click listener and relied on
            // a shared pref listener & a reload to dismiss message cards.
            // By setting our own click listener and manually calling onClick we can remove the
            // item in the adapter directly.
            holder.buttonStart.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(final View v) {
                    final int position = holder.getAdapterPosition();
                    final MessageData message = (MessageData) mItems.get(position);
                    message.getStartButtonClickListener().onClick(holder.buttonStart);
                    mItems.remove(position);
                    notifyItemRemoved(position);
                }
            });
            holder.buttonEnd.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(final View v) {
                    final int position = holder.getAdapterPosition();
                    final MessageData message = (MessageData) mItems.get(position);
                    message.getEndButtonClickListener().onClick(holder.buttonEnd);
                    mItems.remove(position);
                    notifyItemRemoved(position);
                }
            });
            return holder;
        }

        private @NonNull KeynoteViewHolder createKeynoteViewHolder(final ViewGroup parent) {
            final KeynoteViewHolder holder = new KeynoteViewHolder(
                    mInflater.inflate(R.layout.explore_io_keynote_card, parent, false));
            holder.clickableItem.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(final View v) {
                    final SessionData keynote =
                            (SessionData) mItems.get(holder.getAdapterPosition());
                    final Intent intent = new Intent(mHost, SessionDetailActivity.class);
                    intent.setData(
                            ScheduleContract.Sessions.buildSessionUri(keynote.getSessionId()));
                    ActivityCompat.startActivity(mHost, intent, null);
                }
            });
            return holder;
        }

        private @NonNull TrackViewHolder createLiveStreamViewHolder(final ViewGroup parent) {
            final TrackViewHolder holder = new TrackViewHolder(
                    mInflater.inflate(R.layout.explore_io_track_card, parent, false));
            ViewCompat.setImportantForAccessibility(
                    holder.sessions, ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            holder.header.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(final View v) {
                    final Intent intent = new Intent(mHost, ExploreSessionsActivity.class);
                    intent.setData(ScheduleContract.Sessions
                            .buildSessionsAfterUri(TimeUtils.getCurrentTime(mHost)));
                    intent.putExtra(ExploreSessionsActivity.EXTRA_SHOW_LIVE_STREAM_SESSIONS, true);
                    ActivityCompat.startActivity(mHost, intent, null);
                }
            });
            return holder;
        }

        private void bindTrack(final TrackViewHolder holder, final ItemGroup track) {
            bindTrackOrLiveStream(holder, track, mTitles.get(track.getTitle()));
        }

        private void bindMessage(final MessageViewHolder holder, final MessageData message) {
            holder.description.setText(message.getMessageString(mHost));
            if (message.getIconDrawableId() > 0) {
                holder.icon.setVisibility(View.VISIBLE);
                holder.icon.setImageResource(message.getIconDrawableId());
            } else {
                holder.icon.setVisibility(View.GONE);
            }
            if (message.getStartButtonStringResourceId() != -1) {
                holder.buttonEnd.setVisibility(View.VISIBLE);
                holder.buttonStart.setText(message.getStartButtonStringResourceId());
            } else {
                holder.buttonStart.setVisibility(View.GONE);
            }
            if (message.getEndButtonStringResourceId() != -1) {
                holder.buttonEnd.setVisibility(View.VISIBLE);
                holder.buttonEnd.setText(message.getEndButtonStringResourceId());
            } else {
                holder.buttonEnd.setVisibility(View.GONE);
            }
        }

        private void bindKeynote(final KeynoteViewHolder holder, final SessionData keynote) {
            holder.title.setText(keynote.getSessionName());
            if (!TextUtils.isEmpty(keynote.getImageUrl())) {
                mImageLoader.loadImage(keynote.getImageUrl(), holder.thumbnail);
            }
            if (!TextUtils.isEmpty(keynote.getDetails())) {
                holder.description.setText(keynote.getDetails());
            }
        }

        private void bindLiveStream(final TrackViewHolder holder, final LiveStreamData data) {
            bindTrackOrLiveStream(holder, data, mHost.getString(R.string.live_now));
        }

        private void bindTrackOrLiveStream(final TrackViewHolder holder, final ItemGroup track,
                final String title) {
            holder.title.setText(title);
            holder.header.setContentDescription(title);
            mImageLoader.loadImage(track.getPhotoUrl(), holder.headerImage);
            final int trackId = getTrackId(track);
            holder.sessions.setAdapter(mTrackSessionsAdapters.get(trackId));
            holder.sessions.getLayoutManager().onRestoreInstanceState(
                    mTrackSessionsState.get(trackId));
        }

        /**
         * Process the given {@link ExploreIOModel} to create the list of items to be displayed by
         * the {@link RecyclerView}.
         */
        private List processModel(final ExploreIOModel model) {

            final ArrayList exploreCards = new ArrayList();

            // Add any Message cards
            final List<MessageData> messages = model.getMessages();
            if (messages != null && !messages.isEmpty()) {
                exploreCards.addAll(messages);
            }

            // Add Keynote card.
            final SessionData keynote = model.getKeynoteData();
            if (keynote != null) {
                exploreCards.add(keynote);
            }

            // Add Live Stream card.
            final LiveStreamData liveStream = model.getLiveStreamData();
            if (liveStream != null && liveStream.getSessions().size() > 0) {
                exploreCards.add(liveStream);
            }

            // Add track cards
            exploreCards.addAll(model.getTracks());

            // Add theme cards
            exploreCards.addAll(model.getThemes());

            /* TODO sort out theme/topic order; right now we list themes, then topics. */

            return exploreCards;
        }

        /**
         * Setup adapters for tracks which have child session lists
         */
        private void setupSessionAdapters(final ExploreIOModel model) {
            final int trackCount = model.getTracks().size() + model.getThemes().size()
                    + (model.getLiveStreamData() != null ? 1 : 0);
            mTrackSessionsAdapters = new SparseArrayCompat<>(trackCount);
            mTrackSessionsState = new SparseArrayCompat<>(trackCount);

            final LiveStreamData liveStream = model.getLiveStreamData();
            if (liveStream != null && liveStream.getSessions().size() > 0) {
                mTrackSessionsAdapters.put(getTrackId(liveStream),
                        new LiveStreamSessionsAdapter(mHost, liveStream.getSessions(),
                                mImageLoader));
            }

            for (final ItemGroup topicGroup : model.getTracks()) {
                mTrackSessionsAdapters.put(getTrackId(topicGroup),
                        SessionsAdapter.createHorizontal(mHost, topicGroup.getSessions()));
            }

            // Add theme cards
            for (final ItemGroup themeGroup : model.getThemes()) {
                mTrackSessionsAdapters.put(getTrackId(themeGroup),
                        SessionsAdapter.createHorizontal(mHost, themeGroup.getSessions()));
            }
        }


        /**
         * A derived ID for each track; used as a key for some state objects
         */
        private int getTrackId(ItemGroup track) {
            if (track instanceof LiveStreamData) {
                return LIVE_STREAM_TRACK_ID;
            } else {
                return track.getId().hashCode();
            }
        }
    }

    private static class TrackViewHolder extends RecyclerView.ViewHolder {

        final CardView card;
        final ViewGroup header;
        final ImageView headerImage;
        final TextView title;
        final RecyclerView sessions;

        public TrackViewHolder(View itemView) {
            super(itemView);
            card = (CardView) itemView;
            header = (ViewGroup) card.findViewById(R.id.header);
            headerImage = (ImageView) card.findViewById(R.id.header_image);
            title = (TextView) header.findViewById(R.id.title);
            sessions = (RecyclerView) card.findViewById(R.id.sessions);
        }
    }

    private static class MessageViewHolder extends RecyclerView.ViewHolder {

        final ImageView icon;
        final TextView description;
        final Button buttonStart;
        final Button buttonEnd;

        public MessageViewHolder(final View itemView) {
            super(itemView);
            icon = (ImageView) itemView.findViewById(R.id.icon);
            description = (TextView) itemView.findViewById(R.id.description);
            buttonStart = (Button) itemView.findViewById(R.id.buttonStart);
            buttonEnd = (Button) itemView.findViewById(R.id.buttonEnd);
        }
    }

    private static class KeynoteViewHolder extends RecyclerView.ViewHolder {

        final ImageView thumbnail;
        final TextView title;
        final TextView description;
        final ViewGroup clickableItem;

        public KeynoteViewHolder(final View itemView) {
            super(itemView);
            thumbnail = (ImageView) itemView.findViewById(R.id.thumbnail);
            title = (TextView) itemView.findViewById(R.id.title);
            description = (TextView) itemView.findViewById(R.id.description);
            clickableItem = (ViewGroup) itemView.findViewById(R.id.explore_io_clickable_item);
        }
    }
}
