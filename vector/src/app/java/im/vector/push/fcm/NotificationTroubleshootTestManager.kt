package im.vector.push.fcm

import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.support.v4.app.Fragment
import android.support.v4.app.NotificationManagerCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.iid.FirebaseInstanceId
import im.vector.Matrix
import im.vector.R
import im.vector.VectorApp
import im.vector.adapters.NotificationTroubleshootRecyclerViewAdapter
import im.vector.adapters.TroubleshootTest
import org.matrix.androidsdk.rest.callback.ApiCallback
import org.matrix.androidsdk.rest.model.MatrixError
import org.matrix.androidsdk.util.Log
import java.lang.Exception
import kotlin.properties.Delegates

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
class NotificationTroubleshootTestManager(val frg : Fragment) {

    val testList = ArrayList<TroubleshootTest>()
    var isCancelled = false

    var currentTestIndex by Delegates.observable(0) { _, _, _ ->
        statusListener?.invoke(this)
    }
    val adapter = NotificationTroubleshootRecyclerViewAdapter(testList)


    var statusListener : ((NotificationTroubleshootTestManager) -> Unit)? = null

    var diagStatus: TroubleshootTest.TestStatus by Delegates.observable(TroubleshootTest.TestStatus.NOT_STARTED) { _, _, _ ->
        statusListener?.invoke(this)
    }

    init {
        testList.add(object : TroubleshootTest ("1",frg.getString(R.string.settings_troubleshoot_test_system_settings_title) ){
            override fun perform() {
                status =  if (NotificationManagerCompat.from(frg.context!!).areNotificationsEnabled()) {
                    description = frg.getString(R.string.settings_troubleshoot_test_system_settings_success)
                    quickFix = null
                    TestStatus.SUCCESS
                } else {
                    description = frg.getString(R.string.settings_troubleshoot_test_system_settings_failed)
                    val fix = object: TroubleshootQuickFix(frg.getString(R.string.settings_troubleshoot_test_system_settings_quickfix)) {
                        override fun doFix() {
                            if (diagStatus == TestStatus.RUNNING) return; //wait before all is finished
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                val intent = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                                val uri = Uri.fromParts("package", frg.activity?.packageName, null)
                                intent.data = uri
                                frg.startActivityForResult(intent, REQ_CODE_APK_FIX)
                            } else {
                                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_SETTINGS)
                                intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                                val uri = Uri.fromParts("package", frg.activity?.packageName, null)
                                intent.data = uri
                                frg.startActivityForResult(intent, REQ_CODE_APK_FIX)
                            }
                        }

                    }
                    quickFix = fix
                    TestStatus.FAILED
                }
            }
        });


        testList.add(object : TroubleshootTest ("2",frg.getString(R.string.settings_troubleshoot_test_device_settings_title) ){
            override fun perform() {
                val pushManager = Matrix.getInstance(VectorApp.getInstance().baseContext).pushManager
                status =  if (pushManager.areDeviceNotificationsAllowed()) {
                    description = frg.getString(R.string.settings_troubleshoot_test_device_settings_success)
                    quickFix = null
                    TestStatus.SUCCESS
                } else {
                    val fix = object: TroubleshootQuickFix(frg.getString(R.string.settings_troubleshoot_test_device_settings_quickfix)) {
                        override fun doFix() {
                            pushManager.setDeviceNotificationsAllowed(true)
                            retry()
                        }

                    }
                    quickFix = fix
                    description = frg.getString(R.string.settings_troubleshoot_test_device_settings_failed)
                    TestStatus.FAILED
                }
            }
        });

        testList.add(object : TroubleshootTest ("3",frg.getString(R.string.settings_troubleshoot_test_play_services_title)){
            override fun perform() {
                val apiAvailability = GoogleApiAvailability.getInstance()
                val resultCode = apiAvailability.isGooglePlayServicesAvailable(frg.context)
                if (resultCode == ConnectionResult.SUCCESS) {
                    quickFix = null
                    description = frg.getString(R.string.settings_troubleshoot_test_play_services_success)
                    status = TestStatus.SUCCESS
                } else {
                    if (apiAvailability.isUserResolvableError(resultCode)) {
                        val fix = object: TroubleshootQuickFix(frg.getString(R.string.settings_troubleshoot_test_play_services_quickfix)) {
                            override fun doFix() {
                                frg.activity?.let {
                                    apiAvailability.getErrorDialog(it, resultCode, 9000 /*hey does the magic number*/).show();
                                }
                            }
                        }
                        quickFix = fix

                        Log.e(LOG_TAG,"Play Services apk error $resultCode -> ${apiAvailability.getErrorString(resultCode)}.")
                    }

                    description = frg.getString(R.string.settings_troubleshoot_test_play_services_failed,apiAvailability.getErrorString(resultCode))
                    status =  TestStatus.FAILED
                }
            }
        });
        testList.add(object : TroubleshootTest ("4",frg.getString(R.string.settings_troubleshoot_test_fcm_title) ){
            override fun perform() {
                status = TestStatus.RUNNING
                frg.activity?.let {
                    FirebaseInstanceId.getInstance().instanceId
                            .addOnCompleteListener(it) { task ->
                                if (!task.isSuccessful) {
                                    val errorMsg = if (task.exception == null) "Unknown" else task.exception!!.localizedMessage
                                    description = frg.getString(R.string.settings_troubleshoot_test_fcm_failed,errorMsg)
                                    status = TestStatus.FAILED

                                } else {
                                    val token = task.result?.token?.let {
                                        val tok = it.substring(0, Math.min(8,it.length)) + "********************"
                                        description = frg.getString(R.string.settings_troubleshoot_test_fcm_success,tok)
                                        Log.e(LOG_TAG,"Retrieved FCM token success [$it].")

                                    }
                                    status = TestStatus.SUCCESS
                                }
                            }
                } ?: run {
                    status = TestStatus.FAILED
                }
            }
        });


        testList.add(object : TroubleshootTest ("5",frg.getString(R.string.settings_troubleshoot_test_token_registration_title)){
            override fun perform() {
                status = TestStatus.RUNNING
                Matrix.getInstance(VectorApp.getInstance().baseContext).pushManager.forceSessionsRegistration( object: ApiCallback<Void> {
                    override fun onSuccess(info: Void?) {
                        description = frg.getString(R.string.settings_troubleshoot_test_token_registration_success)
                        status = TestStatus.SUCCESS
                    }

                    override fun onNetworkError(e: Exception?) {
                        description = frg.getString(R.string.settings_troubleshoot_test_token_registration_failed,e?.localizedMessage)
                        status = TestStatus.FAILED }

                    override fun onMatrixError(e: MatrixError?) {
                        description = frg.getString(R.string.settings_troubleshoot_test_token_registration_failed,e?.localizedMessage)
                        status = TestStatus.FAILED
                    }

                    override fun onUnexpectedError(e: Exception?) {
                        description = frg.getString(R.string.settings_troubleshoot_test_token_registration_failed,e?.localizedMessage)
                        status = TestStatus.FAILED
                    }

                })
            }
        });
    }


    fun runDiagnostic() {
        if (isCancelled) return
        currentTestIndex = 0
        val handler = Handler(Looper.getMainLooper())
        diagStatus = if (testList.size > 0) TroubleshootTest.TestStatus.RUNNING else TroubleshootTest.TestStatus.SUCCESS
        var isAllGood = true
        for((index,test) in testList.withIndex()){
            test.statusListener = {
                if (!isCancelled) {
                    adapter.notifyItemChanged(index)
                    if (it.isFinihed()) {
                        isAllGood = isAllGood && (it.status == TroubleshootTest.TestStatus.SUCCESS)
                        currentTestIndex++
                        if (currentTestIndex < testList.size) {
                            val troubleshootTest = testList[currentTestIndex]
                            troubleshootTest.status = TroubleshootTest.TestStatus.RUNNING
                            handler.postDelayed({
                                troubleshootTest.perform()
                            }, 600);
                        } else {
                            //we are done, test global status?
                            diagStatus = if (isAllGood) TroubleshootTest.TestStatus.SUCCESS else TroubleshootTest.TestStatus.FAILED
                        }
                    }
                }
            }
        }

        testList.first()?.perform();
    }

    fun retry() {
        for((index,test) in testList.withIndex()){
            test.description = null
            test.quickFix = null
            test.status = TroubleshootTest.TestStatus.NOT_STARTED
        }
        runDiagnostic()
    }

    fun cancel() {
        isCancelled = true
    }

    companion object {
        private val LOG_TAG = NotificationTroubleshootTestManager::class.java.simpleName
        val REQ_CODE_APK_FIX = 9099
    }
}