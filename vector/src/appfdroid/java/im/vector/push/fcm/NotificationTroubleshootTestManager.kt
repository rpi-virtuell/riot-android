/*
 * Copyright 2018 New Vector Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package im.vector.push.fcm

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.Handler
import android.os.Looper
import android.support.v4.app.Fragment
import android.support.v4.net.ConnectivityManagerCompat
import android.support.v7.app.WindowDecorActionBar
import im.vector.R
import im.vector.VectorApp
import im.vector.fragments.troubleshoot.ANotificationTroubleshootTestManager
import im.vector.fragments.troubleshoot.TroubleshootTest
import im.vector.services.EventStreamService
import im.vector.util.PreferencesManager
import im.vector.util.isIgnoringBatteryOptimizations
import im.vector.util.requestDisablingBatteryOptimization
import org.matrix.androidsdk.MXSession
import java.sql.Time
import java.util.*
import kotlin.concurrent.timerTask

class NotificationTroubleshootTestManager(fragment: Fragment, session: MXSession?) : ANotificationTroubleshootTestManager(fragment, session) {

    override fun createTests() {
        var tIndex = 0
        testList.add(object : TroubleshootTest(tIndex, fragment.getString(R.string.settings_troubleshoot_test_foreground_service_started_title)) {
            override fun perform() {
                if (EventStreamService.isStopped()) {
                    description = fragment.getString(R.string.settings_troubleshoot_test_foreground_service_started_failed)
                    status = TestStatus.FAILED
                    quickFix = null
                } else {
                    description = fragment.getString(R.string.settings_troubleshoot_test_foreground_service_startedt_success)
                    quickFix = null
                    status = TestStatus.SUCCESS
                }
            }
        })
        tIndex++

        testList.add(object : TroubleshootTest(tIndex, fragment.getString(R.string.settings_troubleshoot_test_service_restart_title)) {
            var timer : Timer? = null
            override fun perform() {
                status = TestStatus.RUNNING
                EventStreamService.getInstance()?.stopSelf()
                timer = Timer()
                timer?.schedule(timerTask {
                    if (isMyServiceRunning(EventStreamService::class.java)) {
                        fragment.activity?.runOnUiThread {
                            description = fragment.getString(R.string.settings_troubleshoot_test_service_restart_success)
                            quickFix = null
                            status = TestStatus.SUCCESS
                        }
                        timer?.cancel()
                    }
                },0,1000)

                timer?.schedule(timerTask {
                    fragment.activity?.runOnUiThread {
                        status = TestStatus.FAILED
                        description = fragment.getString(R.string.settings_troubleshoot_test_service_restart_failed)
                    }
                    timer?.cancel()
                }, 15000)
            }

            override fun cancel() {
                super.cancel()
                timer?.cancel()
            }
        })
        tIndex++

        testList.add(object : TroubleshootTest(tIndex, fragment.getString(R.string.settings_troubleshoot_test_service_boot_title)) {
            override fun perform() {
                if (PreferencesManager.autoStartOnBoot(fragment.context)) {
                    description = fragment.getString(R.string.settings_troubleshoot_test_service_boot_success)
                    status = TestStatus.SUCCESS
                    quickFix = null
                } else {
                    description = fragment.getString(R.string.settings_troubleshoot_test_service_boot_failed)
                    quickFix = object : TroubleshootQuickFix(fragment.getString(R.string.settings_troubleshoot_test_service_boot_quickfix)) {
                        override fun doFix() {
                            PreferencesManager.setAutoStartOnBoot(fragment.context, true);
                            retry()
                        }
                    }
                    status = TestStatus.FAILED
                }
            }
        })
        tIndex++

        testList.add(object : TroubleshootTest(tIndex, fragment.getString(R.string.settings_troubleshoot_test_bg_restricted_title)) {
            override fun perform() {

                (fragment.context!!.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).apply {
                    // Checks if the device is on a metered network
                    if (isActiveNetworkMetered) {
                        // Checks user’s Data Saver settings.
                        val restrictBackgroundStatus = ConnectivityManagerCompat.getRestrictBackgroundStatus(this)
                        when (restrictBackgroundStatus) {
                            ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED -> {
                                // Background data usage is blocked for this app. Wherever possible,
                                // the app should also use less data in the foreground.
                                description = fragment.getString(R.string.settings_troubleshoot_test_bg_restricted_failed, "RESTRICT_BACKGROUND_STATUS_ENABLED")
                                status = TestStatus.FAILED
                                quickFix = null
                            }
                            ConnectivityManager.RESTRICT_BACKGROUND_STATUS_WHITELISTED -> {
                                // The app is whitelisted. Wherever possible,
                                // the app should use less data in the foreground and background.
                                description = fragment.getString(R.string.settings_troubleshoot_test_bg_restricted_success, "RESTRICT_BACKGROUND_STATUS_WHITELISTED")
                                status = TestStatus.SUCCESS
                                quickFix = null
                            }
                            ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED -> {
                                // Data Saver is disabled. Since the device is connected to a
                                // metered network, the app should use less data wherever possible.
                                description = fragment.getString(R.string.settings_troubleshoot_test_bg_restricted_success, "RESTRICT_BACKGROUND_STATUS_DISABLED")
                                status = TestStatus.SUCCESS
                                quickFix = null
                            }

                        }

                    } else {
                        // The device is not on a metered network.
                        // Use data as required to perform syncs, downloads, and updates.
                        description = fragment.getString(R.string.settings_troubleshoot_test_bg_restricted_success, "")
                        status = TestStatus.SUCCESS
                        quickFix = null
                    }
                }
            }
        })
        tIndex++

        testList.add(object : TroubleshootTest(tIndex, fragment.getString(R.string.settings_troubleshoot_test_battery_title)) {
            override fun perform() {

                if (fragment.context != null && isIgnoringBatteryOptimizations(fragment.context!!)) {
                    description = fragment.getString(R.string.settings_troubleshoot_test_battery_success)
                    status = TestStatus.SUCCESS
                    quickFix = null
                } else {
                    description = fragment.getString(R.string.settings_troubleshoot_test_battery_failed)
                    quickFix = object : TroubleshootQuickFix(fragment.getString(R.string.settings_troubleshoot_test_battery_quickfix)) {
                        override fun doFix() {
                            fragment.activity?.let {
                                requestDisablingBatteryOptimization(it, REQ_CODE_FIX);
                                retry()
                            }
                        }
                    }
                    status = TestStatus.FAILED
                }
            }
        })
    }

    private fun isMyServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = VectorApp.getInstance().baseContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }


    companion object {
        private val LOG_TAG = NotificationTroubleshootTestManager::class.java.simpleName
    }
}