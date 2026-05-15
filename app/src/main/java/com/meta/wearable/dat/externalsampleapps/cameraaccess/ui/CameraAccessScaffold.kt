/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

// CameraAccessScaffold - DAT Application Navigation Orchestrator
//
// Routing:
// - HomeScreen: when NOT registered
// - NonStreamScreen: when registered but no active device (waiting + unregister)
// - ModeSelectionScreen: when device is active and no mode picked (the new app home)
// - Coach flow (preferences -> monitoring -> alert): when Coach mode is picked
// - Meal flow (preferences -> scan -> result): when Meal mode is picked
//
// Debug menu (DEBUG builds) is available via the FAB to access MockDeviceKitScreen.

package com.meta.wearable.dat.externalsampleapps.cameraaccess.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import com.meta.wearable.dat.externalsampleapps.cameraaccess.AppMode
import com.meta.wearable.dat.externalsampleapps.cameraaccess.BuildConfig
import com.meta.wearable.dat.externalsampleapps.cameraaccess.coach.CoachFlowStep
import com.meta.wearable.dat.externalsampleapps.cameraaccess.coach.CoachModeViewModel
import com.meta.wearable.dat.externalsampleapps.cameraaccess.meal.MealFlowStep
import com.meta.wearable.dat.externalsampleapps.cameraaccess.meal.MealModeViewModel
import com.meta.wearable.dat.externalsampleapps.cameraaccess.wearables.WearablesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraAccessScaffold(
    viewModel: WearablesViewModel,
    onRequestWearablesPermission: suspend (Permission) -> PermissionStatus,
    modifier: Modifier = Modifier,
    mealViewModel: MealModeViewModel = viewModel(),
    coachViewModel: CoachModeViewModel = viewModel(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val mealUiState by mealViewModel.uiState.collectAsStateWithLifecycle()
  val coachUiState by coachViewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }
  val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  LaunchedEffect(uiState.recentError) {
    uiState.recentError?.let { errorMessage ->
      snackbarHostState.showSnackbar(errorMessage)
      viewModel.clearRecentError()
    }
  }

  Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Box(modifier = Modifier.fillMaxSize()) {
      when {
        !uiState.isRegistered ->
            HomeScreen(
                viewModel = viewModel,
            )
        !uiState.hasActiveDevice ->
            NonStreamScreen(
                viewModel = viewModel,
                onRequestWearablesPermission = onRequestWearablesPermission,
            )
        uiState.appMode == AppMode.NONE ->
            ModeSelectionScreen(
                onModeSelected = { mode ->
                  mealViewModel.startOver()
                  coachViewModel.resetFlow()
                  viewModel.selectAppMode(mode)
                },
            )
        uiState.appMode == AppMode.COACH ->
            CoachFlowHost(
                wearablesViewModel = viewModel,
                coachViewModel = coachViewModel,
                isStreaming = uiState.isStreaming,
                coachStep = coachUiState.step,
                onRequestWearablesPermission = onRequestWearablesPermission,
            )
        uiState.appMode == AppMode.MEAL ->
            MealFlowHost(
                wearablesViewModel = viewModel,
                mealViewModel = mealViewModel,
                isStreaming = uiState.isStreaming,
                mealStep = mealUiState.step,
                onRequestWearablesPermission = onRequestWearablesPermission,
            )
      }

      SnackbarHost(
          hostState = snackbarHostState,
          modifier =
              Modifier.align(Alignment.BottomCenter)
                  .navigationBarsPadding()
                  .padding(horizontal = 16.dp, vertical = 32.dp),
          snackbar = { data ->
            Snackbar(
                shape = RoundedCornerShape(24.dp),
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = "Camera Access error",
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(data.visuals.message)
              }
            }
          },
      )

      if (BuildConfig.DEBUG) {
        FloatingActionButton(
            onClick = { viewModel.showDebugMenu() },
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
          Icon(Icons.Default.BugReport, contentDescription = "Debug Menu")
        }

        if (uiState.isDebugMenuVisible) {
          ModalBottomSheet(
              onDismissRequest = { viewModel.hideDebugMenu() },
              sheetState = bottomSheetState,
              modifier = Modifier.fillMaxSize(),
          ) {
            MockDeviceKitScreen(modifier = Modifier.fillMaxSize())
          }
        }
      }
    }
  }
}

@Composable
private fun CoachFlowHost(
    wearablesViewModel: WearablesViewModel,
    coachViewModel: CoachModeViewModel,
    isStreaming: Boolean,
    coachStep: CoachFlowStep,
    onRequestWearablesPermission: suspend (Permission) -> PermissionStatus,
) {
  val coachUiState by coachViewModel.uiState.collectAsStateWithLifecycle()
  when (coachStep) {
    CoachFlowStep.PREFERENCES ->
        CoachIntroScreen(
            avoidances = coachUiState.avoidances,
            onAvoidancesChange = { coachViewModel.updateAvoidances(it) },
            onStart = {
              coachViewModel.goToMonitoring()
              wearablesViewModel.navigateToStreaming(onRequestWearablesPermission)
            },
            onBack = {
              coachViewModel.resetFlow()
              wearablesViewModel.clearAppMode()
            },
        )
    CoachFlowStep.MONITORING -> {
      if (isStreaming) {
        CoachMonitoringScreen(
            wearablesViewModel = wearablesViewModel,
            coachViewModel = coachViewModel,
            onBack = {
              coachViewModel.stopMonitoring()
              wearablesViewModel.navigateToDeviceSelection()
              coachViewModel.goBackToPreferences()
            },
        )
      } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator()
        }
      }
    }
    CoachFlowStep.ALERT ->
        CoachAlertScreen(
            roastMessage = coachUiState.roastMessage.orEmpty(),
            onKeepWatching = { coachViewModel.resumeWatching() },
            onStop = {
              coachViewModel.stopMonitoring()
              wearablesViewModel.navigateToDeviceSelection()
              coachViewModel.resetFlow()
              wearablesViewModel.clearAppMode()
            },
        )
  }
}

@Composable
private fun MealFlowHost(
    wearablesViewModel: WearablesViewModel,
    mealViewModel: MealModeViewModel,
    isStreaming: Boolean,
    mealStep: MealFlowStep,
    onRequestWearablesPermission: suspend (Permission) -> PermissionStatus,
) {
  val mealUiState by mealViewModel.uiState.collectAsStateWithLifecycle()
  when (mealStep) {
    MealFlowStep.PREFERENCES ->
        MealPreferencesScreen(
            preferences = mealUiState.preferences,
            onPreferencesChange = { mealViewModel.updatePreferences(it) },
            onContinue = {
              mealViewModel.startScanning()
              wearablesViewModel.navigateToStreaming(onRequestWearablesPermission)
            },
            onBack = {
              mealViewModel.startOver()
              wearablesViewModel.clearAppMode()
            },
        )
    MealFlowStep.SCANNING -> {
      if (isStreaming) {
        MealScanScreen(
            wearablesViewModel = wearablesViewModel,
            mealViewModel = mealViewModel,
            onBack = {
              wearablesViewModel.navigateToDeviceSelection()
              mealViewModel.backToPreferences()
            },
            onFinish = {
              wearablesViewModel.navigateToDeviceSelection()
              mealViewModel.finishScanningAndAnalyze()
            },
        )
      } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator()
        }
      }
    }
    MealFlowStep.ANALYZING,
    MealFlowStep.RESULT ->
        MealResultScreen(
            state = mealUiState,
            onRetry = { mealViewModel.retryAnalyze() },
            onRescan = {
              mealViewModel.backToScanning()
              wearablesViewModel.navigateToStreaming(onRequestWearablesPermission)
            },
            onStartOver = {
              mealViewModel.startOver()
              wearablesViewModel.clearAppMode()
            },
        )
  }
}
