@file:Suppress("FunctionName")

package com.google.samples.apps.iosched.ui.schedule

import android.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.samples.apps.iosched.model.TestData
import com.google.samples.apps.iosched.shared.data.session.DefaultSessionRepository
import com.google.samples.apps.iosched.shared.model.Session
import com.google.samples.apps.iosched.shared.usecases.repository.LoadSessionsUseCase
import com.google.samples.apps.iosched.util.SyncTaskExecutorRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test


/**
 * Unit tests for the [ScheduleViewModel].
 */
class ScheduleViewModelTest {

    // Executes tasks in the Architecture Components in the same thread
    @get:Rule var instantTaskExecutorRule = InstantTaskExecutorRule()

    // Executes tasks in a synchronous [TaskScheduler]
    @get:Rule var syncTaskExecutorRule = SyncTaskExecutorRule()

    @Test
    fun testDataIsLoaded_ObservablesUpdated() {
        // Create a test use cases with test data
        val testData = listOf((TestData.session1))
        val loadSessionsUseCase = createUseCase(testData)

        // Create ViewModel with the use case
        val viewModel = ScheduleViewModel(loadSessionsUseCase)

        // Check that data were loaded correctly
        assertEquals(viewModel.sessions.value?.size, testData.size)
        assertEquals(viewModel.numberOfSessions.get(), testData.size)
        assertEquals(viewModel.sessions.value?.get(0), testData[0])
    }

    @Test
    fun exceptionHappensInUseCase_ErrorIsHandled() {
        // Create ViewModel with the failing use case
        val viewModel = ScheduleViewModel(createExceptionUseCase())

        // Check that the exception was handled correctly
        assertEquals(viewModel.sessions.value, null)
        assertEquals(viewModel.numberOfSessions.get(), 0)
    }

    /**
     * Creates a use case that will return the provided list of sessions.
     */
    private fun createUseCase(sessions: List<Session>): LoadSessionsUseCase {
        return object : LoadSessionsUseCase(DefaultSessionRepository) {
            override fun execute(parameters: String): List<Session> {
                return sessions
            }
        }
    }

    /**
     * Creates a use case that throws an exception.
     */
    private fun createExceptionUseCase(): LoadSessionsUseCase {
        return object : LoadSessionsUseCase(DefaultSessionRepository) {
            override fun execute(parameters: String): List<Session> {
                throw Exception("Testing exception")
            }
        }
    }
}
