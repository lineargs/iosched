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

package com.google.samples.apps.iosched.ui.schedule

import android.arch.lifecycle.ViewModelProvider
import android.databinding.ObservableBoolean
import android.os.Bundle
import android.support.design.widget.CoordinatorLayout
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentActivity
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentPagerAdapter
import android.support.v4.view.ViewPager
import android.support.v4.view.ViewPager.SimpleOnPageChangeListener
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.OnHierarchyChangeListener
import androidx.view.doOnLayout
import com.google.android.material.widget.FloatingActionButton
import com.google.android.material.widget.Snackbar
import com.google.android.material.widget.TabLayout
import com.google.samples.apps.iosched.R
import com.google.samples.apps.iosched.databinding.FragmentScheduleBinding
import com.google.samples.apps.iosched.shared.domain.users.SwapRequestParameters
import com.google.samples.apps.iosched.shared.result.EventObserver
import com.google.samples.apps.iosched.shared.util.TimeUtils.ConferenceDay
import com.google.samples.apps.iosched.shared.util.activityViewModelProvider
import com.google.samples.apps.iosched.shared.util.lazyFast
import com.google.samples.apps.iosched.ui.MainNavigationFragment
import com.google.samples.apps.iosched.ui.messages.SnackbarMessageManager
import com.google.samples.apps.iosched.ui.reservation.RemoveReservationDialogFragment
import com.google.samples.apps.iosched.ui.reservation.RemoveReservationDialogFragment.Companion.DIALOG_REMOVE_RESERVATION
import com.google.samples.apps.iosched.ui.reservation.RemoveReservationDialogParameters
import com.google.samples.apps.iosched.ui.reservation.SwapReservationDialogFragment
import com.google.samples.apps.iosched.ui.reservation.SwapReservationDialogFragment.Companion.DIALOG_SWAP_RESERVATION
import com.google.samples.apps.iosched.ui.schedule.agenda.ScheduleAgendaFragment
import com.google.samples.apps.iosched.ui.schedule.day.ScheduleDayFragment
import com.google.samples.apps.iosched.ui.sessiondetail.SessionDetailActivity
import com.google.samples.apps.iosched.ui.setUpSnackbar
import com.google.samples.apps.iosched.ui.signin.SignInDialogFragment
import com.google.samples.apps.iosched.ui.signin.SignOutDialogFragment
import com.google.samples.apps.iosched.widget.BottomSheetBehavior
import com.google.samples.apps.iosched.widget.BottomSheetBehavior.Companion.STATE_COLLAPSED
import com.google.samples.apps.iosched.widget.BottomSheetBehavior.Companion.STATE_EXPANDED
import com.google.samples.apps.iosched.widget.BottomSheetBehavior.Companion.STATE_HIDDEN
import dagger.android.support.DaggerFragment
import javax.inject.Inject

/**
 * The Schedule page of the top-level Activity.
 */
class ScheduleFragment : DaggerFragment(), MainNavigationFragment {

    companion object {
        private val COUNT = ConferenceDay.values().size + 1 // Agenda
        private val AGENDA_POSITION = COUNT - 1
        private const val DIALOG_NEED_TO_SIGN_IN = "dialog_need_to_sign_in"
        private const val DIALOG_CONFIRM_SIGN_OUT = "dialog_confirm_sign_out"
        private const val DIALOG_SCHEDULE_HINTS = "dialog_schedule_hints"
        private const val STATE_BOTTOM_NAV_TRANSLATION = "state.BOTTOM_NAV_TRANSLATION"
    }

    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory

    private lateinit var viewModel: ScheduleViewModel
    private lateinit var coordinatorLayout: CoordinatorLayout

    @Inject lateinit var snackbarMessageManager: SnackbarMessageManager

    private lateinit var filtersFab: FloatingActionButton
    private lateinit var dummyBottomView: View
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<*>
    // Peek height we want to maintain above the bottom navigation
    private val basePeekHeight: Int by lazyFast {
        resources.getDimensionPixelSize(R.dimen.bottom_sheet_peek_height)
    }

    private val isAgendaPage = ObservableBoolean(false)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        viewModel = activityViewModelProvider(viewModelFactory)
        val binding = FragmentScheduleBinding.inflate(inflater, container, false).apply {
            setLifecycleOwner(this@ScheduleFragment)
            viewModel = this@ScheduleFragment.viewModel
            isAgendaPage = this@ScheduleFragment.isAgendaPage
        }

        coordinatorLayout = binding.coordinatorLayout
        dummyBottomView = binding.dummyBottomNavigation
        filtersFab = binding.filterFab
        // We can't lookup bottomSheetBehavior here since it's on a <fragment> tag

        setUpSnackbar(viewModel.snackBarMessage, coordinatorLayout, snackbarMessageManager)

        viewModel.navigateToSessionAction.observe(this, EventObserver { sessionId ->
            openSessionDetail(sessionId)
        })

        viewModel.navigateToSignInDialogAction.observe(this, EventObserver {
            openSignInDialog()
        })

        viewModel.navigateToSignOutDialogAction.observe(this, EventObserver {
            openSignOutDialog()
        })
        viewModel.navigateToRemoveReservationDialogAction.observe(this, EventObserver {
            openRemoveReservationDialog(requireActivity(), it)
        })
        viewModel.navigateToSwapReservationDialogAction.observe(this, EventObserver {
            openSwapReservationDialog(requireActivity(), it)
        })
        viewModel.scheduleUiHintsShown.observe(this, EventObserver {
            if (!it) {
                openScheduleUiHintsDialog()
            }
        })
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val viewpager: ViewPager = view.findViewById(R.id.viewpager)
        viewpager.offscreenPageLimit = COUNT - 1
        viewpager.adapter = ScheduleAdapter(childFragmentManager)

        val tabs: TabLayout = view.findViewById(R.id.tabs)
        tabs.setupWithViewPager(viewpager)

        viewpager.addOnPageChangeListener(object : SimpleOnPageChangeListener() {
            override fun onPageSelected(position: Int) {
                // Hide the FAB on the agenda page
                isAgendaPage.set(position == AGENDA_POSITION)
            }
        })

        // Ensure snackbars appear above the hiding BottomNavigationView.
        // We clear the Snackbar's insetEdge, which is also set in it's (final) showView() method,
        // so we have to do it later (e.g. when it's added to the hierarchy).
        coordinatorLayout.setOnHierarchyChangeListener(object : OnHierarchyChangeListener {
            override fun onChildViewAdded(parent: View, child: View) {
                if (child is Snackbar.SnackbarLayout) {
                    child.layoutParams = (child.layoutParams as CoordinatorLayout.LayoutParams)
                        .apply {
                            insetEdge = Gravity.NO_GRAVITY
                            dodgeInsetEdges = Gravity.BOTTOM
                        }
                    // Also make it draw over the bottom sheet
                    child.elevation = resources.getDimension(R.dimen.bottom_sheet_elevation)
                }
            }

            override fun onChildViewRemoved(parent: View, child: View) {}
        })

        bottomSheetBehavior = BottomSheetBehavior.from(view.findViewById(R.id.filter_sheet))
        filtersFab.setOnClickListener {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        }

        if (savedInstanceState == null) {
            // Set the peek height on first layout
            dummyBottomView.doOnLayout { onBottomNavSlide(dummyBottomView.translationY) }
            // Make bottom sheet hidden at first
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putFloat(STATE_BOTTOM_NAV_TRANSLATION, dummyBottomView.translationY)
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        val ty = savedInstanceState?.getFloat(STATE_BOTTOM_NAV_TRANSLATION) ?: return
        onBottomNavSlide(ty)
    }

    override fun onBottomNavSlide(bottonNavTranslationY: Float) {
        // Move the dummy view to change bottom edge inset (for snackbars, etc.)
        dummyBottomView.translationY = bottonNavTranslationY
        // Tie the filters bottom sheet to the bottom navigation bar
        val peek = Math.max(0, (dummyBottomView.height - bottonNavTranslationY + .5f).toInt())
        bottomSheetBehavior.peekHeight = basePeekHeight + peek
    }

    override fun onBackPressed(): Boolean {
        if (bottomSheetBehavior.state == STATE_EXPANDED) {
            bottomSheetBehavior.state =
                    if (bottomSheetBehavior.skipCollapsed) STATE_HIDDEN else STATE_COLLAPSED
            return true
        }
        return super.onBackPressed()
    }

    private fun openSessionDetail(id: String) {
        startActivity(SessionDetailActivity.starterIntent(requireContext(), id))
    }

    private fun openSignInDialog() {
        val dialog = SignInDialogFragment()
        dialog.show(requireActivity().supportFragmentManager, DIALOG_NEED_TO_SIGN_IN)
    }

    private fun openRemoveReservationDialog(
        activity: FragmentActivity,
        parameters: RemoveReservationDialogParameters
    ) {
        val dialog = RemoveReservationDialogFragment.newInstance(parameters)
        dialog.show(activity.supportFragmentManager, DIALOG_REMOVE_RESERVATION)
    }

    private fun openSwapReservationDialog(
            activity: FragmentActivity,
            parameters: SwapRequestParameters
    ) {
        val dialog = SwapReservationDialogFragment.newInstance(parameters)
        dialog.show(activity.supportFragmentManager, DIALOG_SWAP_RESERVATION)
    }

    private fun openSignOutDialog() {
        val dialog = SignOutDialogFragment()
        dialog.show(requireActivity().supportFragmentManager, DIALOG_CONFIRM_SIGN_OUT)
    }

    private fun openScheduleUiHintsDialog() {
        val dialog = ScheduleUiHintsDialogFragment()
        dialog.show(requireActivity().supportFragmentManager, DIALOG_SCHEDULE_HINTS)
    }

    /**
     * Adapter that build a page for each conference day.
     */
    inner class ScheduleAdapter(fm: FragmentManager) : FragmentPagerAdapter(fm) {

        override fun getCount() = COUNT

        override fun getItem(position: Int): Fragment {
            return when (position) {
                AGENDA_POSITION -> ScheduleAgendaFragment()
                else -> ScheduleDayFragment.newInstance(ConferenceDay.values()[position])
            }
        }

        override fun getPageTitle(position: Int): CharSequence {
            return when (position) {
                AGENDA_POSITION -> getString(R.string.agenda)
                else -> ConferenceDay.values()[position].formatMonthDay()
            }
        }
    }
}

