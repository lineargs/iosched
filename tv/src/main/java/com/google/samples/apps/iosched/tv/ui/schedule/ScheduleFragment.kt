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

package com.google.samples.apps.iosched.tv.ui.schedule

import android.app.Activity
import android.arch.lifecycle.Observer
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.support.v17.leanback.app.BackgroundManager
import android.support.v17.leanback.app.RowsSupportFragment
import android.support.v17.leanback.widget.ArrayObjectAdapter
import android.support.v17.leanback.widget.HeaderItem
import android.support.v17.leanback.widget.ImageCardView
import android.support.v17.leanback.widget.ListRow
import android.support.v17.leanback.widget.ListRowPresenter
import android.support.v17.leanback.widget.Presenter
import android.view.ViewGroup
import android.widget.Toast
import com.google.samples.apps.iosched.shared.model.Session
import com.google.samples.apps.iosched.shared.model.UserSession
import com.google.samples.apps.iosched.shared.util.TimeUtils.ConferenceDay
import com.google.samples.apps.iosched.shared.util.activityViewModelProvider
import com.google.samples.apps.iosched.shared.util.getEnum
import com.google.samples.apps.iosched.shared.util.getThemeColor
import com.google.samples.apps.iosched.shared.util.inTransaction
import com.google.samples.apps.iosched.shared.util.lazyFast
import com.google.samples.apps.iosched.shared.util.putEnum
import com.google.samples.apps.iosched.tv.R
import com.google.samples.apps.iosched.tv.TvApplication
import com.google.samples.apps.iosched.tv.ui.SpinnerFragment
import com.google.samples.apps.iosched.tv.ui.presenter.SessionPresenter
import com.google.samples.apps.iosched.tv.ui.sessiondetail.SessionDetailActivity
import com.google.samples.apps.iosched.tv.util.toArrayObjectAdapter
import javax.inject.Inject

/**
 * Displays a single day's session schedule.
 */
class ScheduleFragment : RowsSupportFragment() {

    @Inject
    lateinit var viewModelFactory: ScheduleViewModelFactory

    private lateinit var viewModel: ScheduleViewModel

    private val conferenceDay: ConferenceDay by lazyFast {
        val args = arguments ?: throw IllegalStateException("Missing arguments!")
        args.getEnum<ConferenceDay>(ARG_CONFERENCE_DAY)
    }

    private val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())

    private lateinit var noSessionsRow: ListRow

    private val spinnerFragment = SpinnerFragment()

    private lateinit var backgroundManager: BackgroundManager
    private lateinit var defaultBackground: Drawable

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (context?.applicationContext as TvApplication).scheduleComponent
            .inject(scheduleFragment = this)

        adapter = rowsAdapter

        fragmentManager?.inTransaction {
            add(R.id.main_frame, spinnerFragment)
        }

        viewModel = activityViewModelProvider(viewModelFactory)

        noSessionsRow = createNoSessionRow()

        setOnItemViewClickedListener { itemViewHolder, item, _, _ ->
            if (item is Session) {
                val context = itemViewHolder.view.context
                // TODO: Add fragment transition from session card to detail's logo presenter
                startActivity(
                    SessionDetailActivity.createIntent(context = context, sessionId = item.id))
            }
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel.getSessionsGroupedByTimeForDay(conferenceDay)
                .observe(requireActivity(), Observer { map ->
                    loadAdapter(sessionsByTimeSlot = map ?: emptyMap())
                })

        viewModel.isLoading.observe(this, Observer { isLoading ->

            if (isLoading == false) {
                fragmentManager?.inTransaction {
                    remove(spinnerFragment)
                }
            }
        })

        viewModel.errorMessage.observe(this, Observer { message ->
            //TODO: Change once there's a way to show errors to the user
            if (!message.isNullOrEmpty() && !viewModel.wasErrorMessageShown()) {
                // Prevent the message from showing more than once
                viewModel.onErrorMessageShown()
                Toast.makeText(this.context, message, Toast.LENGTH_LONG).show()
            }
        })
    }

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        prepareBackgroundManager(requireActivity())
    }

    private fun prepareBackgroundManager(activity: Activity) {
        backgroundManager = BackgroundManager.getInstance(activity)
        if (!backgroundManager.isAttached) {
            backgroundManager.attach(activity.window)
        }
        // Use the darker primary color for the background to contrast with the headers.
        val color = activity.getThemeColor(R.attr.colorPrimaryDark, R.color.colorPrimaryDark)
        defaultBackground = ColorDrawable(color)

        backgroundManager.drawable = defaultBackground
    }

    private fun createNoSessionRow(): ListRow {
        val noSessionHeader = HeaderItem(-1, getString(R.string.no_sessions_available))
        val noSessionAdapter = ArrayObjectAdapter(object : Presenter() {
            override fun onCreateViewHolder(parent: ViewGroup?): ViewHolder {
                return ViewHolder(ImageCardView(parent?.context))
            }

            override fun onBindViewHolder(viewHolder: ViewHolder?, item: Any?) {
                val cardView = viewHolder?.view as ImageCardView

                // TODO: replace with actual error message wording
                cardView.titleText = getString(R.string.try_later)
                cardView.contentText = getString(R.string.sorry_for_the_troubles)

                // Set the image card's height and width.
                val resources = cardView.context.resources
                val cardWidth = resources.getDimensionPixelSize(R.dimen.card_width)
                val cardHeight = resources.getDimensionPixelSize(R.dimen.card_height)
                cardView.setMainImageDimensions(cardWidth, cardHeight)
            }

            override fun onUnbindViewHolder(viewHolder: ViewHolder?) {}
        }).apply { add(Any()) }
        return ListRow(noSessionHeader, noSessionAdapter)
    }

    private fun loadAdapter(sessionsByTimeSlot: Map<String, List<UserSession>>) {

        val rows = mutableListOf<ListRow>()

        if (sessionsByTimeSlot.isEmpty()) {
            rows.add(noSessionsRow)
        } else {
            for (timeSlot in sessionsByTimeSlot) {
                val header = HeaderItem(timeSlot.key)
                // TODO: use UserSession instead of plain session
                val sessions = timeSlot.value.map { it -> it.session }
                val sessionAdapter = sessions.toArrayObjectAdapter(SessionPresenter())
                val timeSlotRow = ListRow(header, sessionAdapter)
                rows.add(timeSlotRow)
            }
        }

        rowsAdapter.setItems(rows, TimeSlotSessionDiffCallback())
        mainFragmentAdapter.fragmentHost.notifyDataReady(mainFragmentAdapter)
    }

    companion object {

        const val ARG_CONFERENCE_DAY = "com.google.samples.apps.iosched.tv.ARG_CONFERENCE_DAY"

        fun newInstance(day: ConferenceDay): ScheduleFragment {
            val args = Bundle().apply {
                putEnum(ARG_CONFERENCE_DAY, day)
            }
            return ScheduleFragment().apply { arguments = args }
        }
    }
}
