package im.vector.push.fcm

import android.app.Activity
import android.support.v4.app.Fragment
import im.vector.adapters.NotificationTroubleshootRecyclerViewAdapter
import im.vector.adapters.TroubleshootTest
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
class NotificationTroubleshootTestManager(val frg: Fragment) {

    val testList = ArrayList<TroubleshootTest>()
    val currentTestIndex = 0

    val adapter = NotificationTroubleshootRecyclerViewAdapter(ArrayList<TroubleshootTest>())

    var statusListener : ((NotificationTroubleshootTestManager) -> Unit)? = null

    var diagStatus: TroubleshootTest.TestStatus by Delegates.observable(TroubleshootTest.TestStatus.NOT_STARTED) { _, _, _ ->
        statusListener?.invoke(this)
    }

    fun runDiagnostic() {
        //no op
        diagStatus = TroubleshootTest.TestStatus.SUCCESS
    }

    fun cancel() {
    }

    fun retry() {

    }

    companion object {
        val REQ_CODE_APK_FIX = 9099
    }
}