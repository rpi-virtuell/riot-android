package im.vector.fragments

import android.content.Intent
import android.os.Bundle
import android.support.transition.TransitionManager
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import im.vector.R
import im.vector.activity.MXCActionBarActivity
import im.vector.adapters.TroubleshootTest
import im.vector.push.fcm.NotificationTroubleshootTestManager
import im.vector.util.BugReporter

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
class VectorSettingsNotificationsTroubleshootFragment : VectorBaseFragment() {

    lateinit var mRecyclerView: RecyclerView
    lateinit var mBottomView: View
    lateinit var mSummaryTitle: TextView
    lateinit var mSummaryDescription: TextView
    lateinit var mSummaryButton: Button
    lateinit var mRunButton: Button

    var testManager: NotificationTroubleshootTestManager? = null

    override fun getLayoutResId() = R.layout.fragment_settings_notifications_troubleshoot



    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mRecyclerView = view.findViewById(R.id.troubleshoot_test_recycler_view);
        val layoutManager = LinearLayoutManager(context)
        mRecyclerView.layoutManager = layoutManager

        val dividerItemDecoration = DividerItemDecoration(mRecyclerView.context,
                layoutManager.orientation);
        mRecyclerView.addItemDecoration(dividerItemDecoration)

        mBottomView = view.findViewById(R.id.bottomView)
        mSummaryTitle = view.findViewById(R.id.summ_title)
        mSummaryDescription = view.findViewById(R.id.summ_description)
        mSummaryButton = view.findViewById(R.id.summ_button)
        mRunButton = view.findViewById(R.id.runButton)

        mSummaryButton.setOnClickListener {
            BugReporter.sendBugReport()
        }

        mRunButton.setOnClickListener() {
            testManager?.retry()
        }

        startUI()
    }

    private fun startUI() {

        mSummaryTitle.text = getString(R.string.settings_troubleshoot_diagnostic)
        mSummaryDescription.text = getString(R.string.settings_troubleshoot_diagnostic_running_status)
        mSummaryButton.text = getText(R.string.send_bug_report)

        testManager = NotificationTroubleshootTestManager(this)

        testManager?.statusListener = {
            if (isAdded) {
                (mBottomView as? ViewGroup)?.let {
                    TransitionManager.beginDelayedTransition(it)
                }
                when (it.diagStatus) {
                    TroubleshootTest.TestStatus.NOT_STARTED -> {
                        mSummaryDescription.text = ""
                        mSummaryButton.visibility = View.GONE
                        mRunButton.visibility = View.VISIBLE
                    }
                    TroubleshootTest.TestStatus.RUNNING -> {
                        mSummaryDescription.text = getString(R.string.settings_troubleshoot_diagnostic_running_status, it.currentTestIndex, it.testList.size)
                        mSummaryButton.visibility = View.GONE
                        mRunButton.visibility = View.GONE
                    }
                    TroubleshootTest.TestStatus.FAILED -> {

                        //check if there are quick fixes
                        var hasQuickFix = false
                        testManager?.testList?.let {
                            for (test in it) {
                                if (test.status == TroubleshootTest.TestStatus.FAILED && test.quickFix != null) {
                                    hasQuickFix = true
                                    break
                                }
                            }
                        }

                        if (hasQuickFix) {
                            mSummaryDescription.text = getString(R.string.settings_troubleshoot_diagnostic_failure_status_with_quickfix)
                        } else {
                            mSummaryDescription.text = getString(R.string.settings_troubleshoot_diagnostic_failure_status_no_quickfix)
                        }
                        mSummaryButton.visibility = View.VISIBLE
                        mRunButton.visibility = View.VISIBLE
                    }
                    TroubleshootTest.TestStatus.SUCCESS -> {
                        mSummaryDescription.text = getString(R.string.settings_troubleshoot_diagnostic_success_status)
                        mSummaryButton.visibility = View.VISIBLE
                        mRunButton.visibility = View.VISIBLE
                    }
                }
            }

        }

        mRecyclerView.adapter = testManager?.adapter
        testManager?.runDiagnostic()


    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == NotificationTroubleshootTestManager.REQ_CODE_APK_FIX) {
            testManager?.retry()
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onDetach() {
        testManager?.cancel()
        super.onDetach()
    }

    override fun onResume() {
        super.onResume()
        (activity as? MXCActionBarActivity)?.supportActionBar?.setTitle(R.string.settings_notification_troubleshoot)
    }

    companion object {
        private val LOG_TAG = VectorSettingsNotificationsTroubleshootFragment::class.java.simpleName
    }
}