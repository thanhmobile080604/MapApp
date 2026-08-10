package com.example.mapapp.viewmodel

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.AuthResult
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Unit tests for [LoginViewModel].
 *
 * The tests focus on the validation logic that does not require a real Firebase backend.
 * They verify that providing empty username or password results in a [LoginResult.Failure]
 * with the expected message.
 */
class LoginViewModelTest {

    @Test
    fun `login with empty username and password should fail`() = runTest {
        // Arrange – use a real ViewModel (FirebaseAuth will be lazily initialised but never used)
        val viewModel = LoginViewModel()

        // Act – attempt login with empty strings
        viewModel.login("", "", context = androidx.test.core.app.ApplicationProvider.getApplicationContext())

        // Assert – the first emitted result should be Failure with the correct message
        val result = viewModel.loginResult.first()
        assertTrue(result is LoginViewModel.LoginResult.Failure)
        if (result is LoginViewModel.LoginResult.Failure) {
            assertTrue(result.message.contains("cannot be empty"))
        }
    }
}
