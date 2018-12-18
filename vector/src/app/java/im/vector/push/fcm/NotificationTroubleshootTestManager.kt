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

import android.support.v4.app.Fragment
import android.support.v4.app.NotificationManagerCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.iid.FirebaseInstanceId
import im.vector.Matrix
import im.vector.R
import im.vector.VectorApp
import im.vector.fragments.troubleshoot.ANotificationTroubleshootTestManager
import im.vector.fragments.troubleshoot.TroubleshootTest
import im.vector.util.startNotificationSettingsIntent
import org.matrix.androidsdk.MXSession
import org.matrix.androidsdk.rest.callback.ApiCallback
import org.matrix.androidsdk.rest.model.MatrixError
import org.matrix.androidsdk.rest.model.bingrules.BingRule
import org.matrix.androidsdk.util.BingRulesManager
import org.matrix.androidsdk.util.Log
import java.lang.Exception

class NotificationTroubleshootTestManager(fragment: Fragment, session: MXSession?) : ANotificationTroubleshootTestManager(fragment, session) {

    override fun createTests() {
        testList.add(object : TroubleshootTest(1, fragment.getString(R.string.settings_troubleshoot_test_system_settings_title)) {
            override fun perform() {
                if (NotificationManagerCompat.from(fragment.context!!).areNotificationsEnabled()) {
                    description = fragment.getString(R.string.settings_troubleshoot_test_system_settings_success)
                    quickFix = null
                    status = TestStatus.SUCCESS
                } else {
                    description = fragment.getString(R.string.settings_troubleshoot_test_system_settings_failed)
                    quickFix = object : TroubleshootQuickFix(fragment.getString(R.string.settings_troubleshoot_test_system_settings_quickfix)) {
                        override fun doFix() {
                            if (diagStatus == TestStatus.RUNNING) return; //wait before all is finished
                            startNotificationSettingsIntent(fragment, REQ_CODE_FIX)
                        }

                    }
                    status = TestStatus.FAILED
                }
            }
        })

        if (session != null) {
            testList.add(object : TroubleshootTest(2, fragment.getString(R.string.settings_troubleshoot_test_account_settings_title)) {
                override fun perform() {
                    val defaultRule = session.dataHandler.bingRulesManager.pushRules().findDefaultRule(BingRule.RULE_ID_DISABLE_ALL)
                    if (!defaultRule.isEnabled) {
                        description = fragment.getString(R.string.settings_troubleshoot_test_account_settings_success)
                        quickFix = null
                        status = TestStatus.SUCCESS
                    } else {
                        description = fragment.getString(R.string.settings_troubleshoot_test_account_settings_failed)
                        quickFix = object : TroubleshootQuickFix(fragment.getString(R.string.settings_troubleshoot_test_account_settings_quickfix)) {
                            override fun doFix() {
                                if (diagStatus == TestStatus.RUNNING) return; //wait before all is finished
                                session.dataHandler.bingRulesManager.updateEnableRuleStatus(defaultRule, !defaultRule.isEnabled,
                                        object : BingRulesManager.onBingRuleUpdateListener {
                                            private fun onDone() {
                                                retry()
                                            }

                                            override fun onBingRuleUpdateSuccess() {
                                                retry()
                                            }

                                            override fun onBingRuleUpdateFailure(errorMessage: String) {
                                                retry()
                                            }
                                        })
                            }
                        }
                        status = TestStatus.FAILED
                    }
                }
            })
        }

        testList.add(object : TroubleshootTest(3, fragment.getString(R.string.settings_troubleshoot_test_device_settings_title)) {
            override fun perform() {
                val pushManager = Matrix.getInstance(fragment.activity).pushManager
                if (pushManager.areDeviceNotificationsAllowed()) {
                    description = fragment.getString(R.string.settings_troubleshoot_test_device_settings_success)
                    quickFix = null
                    status = TestStatus.SUCCESS
                } else {
                    quickFix = object : TroubleshootQuickFix(fragment.getString(R.string.settings_troubleshoot_test_device_settings_quickfix)) {
                        override fun doFix() {
                            pushManager.setDeviceNotificationsAllowed(true)
                            retry()
                        }

                    }
                    description = fragment.getString(R.string.settings_troubleshoot_test_device_settings_failed)
                    status = TestStatus.FAILED
                }
            }
        })

        testList.add(object : TroubleshootTest(4, fragment.getString(R.string.settings_troubleshoot_test_play_services_title)) {
            override fun perform() {
                val apiAvailability = GoogleApiAvailability.getInstance()
                val resultCode = apiAvailability.isGooglePlayServicesAvailable(fragment.context)
                if (resultCode == ConnectionResult.SUCCESS) {
                    quickFix = null
                    description = fragment.getString(R.string.settings_troubleshoot_test_play_services_success)
                    status = TestStatus.SUCCESS
                } else {
                    if (apiAvailability.isUserResolvableError(resultCode)) {
                        val fix = object : TroubleshootQuickFix(fragment.getString(R.string.settings_troubleshoot_test_play_services_quickfix)) {
                            override fun doFix() {
                                fragment.activity?.let {
                                    apiAvailability.getErrorDialog(it, resultCode, 9000 /*hey does the magic number*/).show();
                                }
                            }
                        }
                        quickFix = fix

                        Log.e(LOG_TAG, "Play Services apk error $resultCode -> ${apiAvailability.getErrorString(resultCode)}.")
                    }

                    description = fragment.getString(R.string.settings_troubleshoot_test_play_services_failed, apiAvailability.getErrorString(resultCode))
                    status = TestStatus.FAILED
                }
            }
        })

        testList.add(object : TroubleshootTest(5, fragment.getString(R.string.settings_troubleshoot_test_fcm_title)) {
            override fun perform() {
                status = TestStatus.RUNNING
                fragment.activity?.let {
                    FirebaseInstanceId.getInstance().instanceId
                            .addOnCompleteListener(it) { task ->
                                if (!task.isSuccessful) {
                                    val errorMsg = if (task.exception == null) "Unknown" else task.exception!!.localizedMessage
                                    description = fragment.getString(R.string.settings_troubleshoot_test_fcm_failed, errorMsg)
                                    status = TestStatus.FAILED

                                } else {
                                    task.result?.token?.let {
                                        val tok = it.substring(0, Math.min(8, it.length)) + "********************"
                                        description = fragment.getString(R.string.settings_troubleshoot_test_fcm_success, tok)
                                        Log.e(LOG_TAG, "Retrieved FCM token success [$it].")
                                    }
                                    status = TestStatus.SUCCESS
                                }
                            }
                } ?: run {
                    status = TestStatus.FAILED
                }
            }
        })

        testList.add(object : TroubleshootTest(6, fragment.getString(R.string.settings_troubleshoot_test_token_registration_title)) {
            override fun perform() {
                status = TestStatus.RUNNING
                Matrix.getInstance(VectorApp.getInstance().baseContext).pushManager.forceSessionsRegistration(object : ApiCallback<Void> {
                    override fun onSuccess(info: Void?) {
                        description = fragment.getString(R.string.settings_troubleshoot_test_token_registration_success)
                        status = TestStatus.SUCCESS
                    }

                    override fun onNetworkError(e: Exception?) {
                        description = fragment.getString(R.string.settings_troubleshoot_test_token_registration_failed, e?.localizedMessage)
                        status = TestStatus.FAILED
                    }

                    override fun onMatrixError(e: MatrixError?) {
                        description = fragment.getString(R.string.settings_troubleshoot_test_token_registration_failed, e?.localizedMessage)
                        status = TestStatus.FAILED
                    }

                    override fun onUnexpectedError(e: Exception?) {
                        description = fragment.getString(R.string.settings_troubleshoot_test_token_registration_failed, e?.localizedMessage)
                        status = TestStatus.FAILED
                    }

                })
            }
        });
    }


    companion object {
        private val LOG_TAG = NotificationTroubleshootTestManager::class.java.simpleName
    }
}