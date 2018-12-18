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
package im.vector.fragments.troubleshoot

import android.content.res.ColorStateList
import android.os.Build
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import butterknife.BindView
import butterknife.ButterKnife
import im.vector.R
import im.vector.ui.themes.ThemeUtils

class NotificationTroubleshootRecyclerViewAdapter(tests: ArrayList<TroubleshootTest>)
    : RecyclerView.Adapter<NotificationTroubleshootRecyclerViewAdapter.ViewHolder>() {

    private val mTestList: ArrayList<TroubleshootTest> = tests

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val itemView = inflater.inflate(viewType, parent, false)
        return ViewHolder(itemView)
    }

    override fun getItemViewType(position: Int): Int = R.layout.item_notification_troubleshoot

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val test = mTestList[position]
        holder.bind(test)
    }

    override fun getItemCount(): Int = mTestList.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        @BindView(R.id.troubleshootTestTitle)
        lateinit var titleText: TextView
        @BindView(R.id.troubleshootTestDescription)
        lateinit var descriptionText: TextView
        @BindView(R.id.troubleshootStatusIcon)
        lateinit var statusIconImage: ImageView
        @BindView(R.id.troubleshootProgressBar)
        lateinit var progressBar: ProgressBar
        @BindView(R.id.troubleshootTestButton)
        lateinit var fixButton: Button

        init {
            ButterKnife.bind(this, itemView)
        }

        fun bind(test: TroubleshootTest) {

            val context = itemView.context
            titleText.setTextColor(ThemeUtils.getColor(itemView.context, R.attr.vctr_riot_primary_text_color))
            descriptionText.setTextColor(ThemeUtils.getColor(itemView.context, R.attr.vctr_default_text_hint_color))

            when (test.status) {
                TroubleshootTest.TestStatus.NOT_STARTED -> {
                    titleText.setTextColor(ThemeUtils.getColor(itemView.context, R.attr.vctr_default_text_hint_color))
                    descriptionText.setTextColor(ThemeUtils.getColor(itemView.context, R.attr.vctr_default_text_hint_color))

                    progressBar.visibility = View.INVISIBLE
                    statusIconImage.visibility = View.VISIBLE
                    statusIconImage.setImageResource(R.drawable.abc_btn_check_material)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        val color = ThemeUtils.getColor(itemView.context, R.attr.vctr_default_icon_tint_color)
                        statusIconImage.imageTintList = ColorStateList.valueOf(color)
                    }
                }
                TroubleshootTest.TestStatus.RUNNING -> {
                    progressBar.visibility = View.VISIBLE
                    statusIconImage.visibility = View.INVISIBLE

                }
                TroubleshootTest.TestStatus.FAILED -> {
                    progressBar.visibility = View.INVISIBLE
                    statusIconImage.visibility = View.VISIBLE
                    statusIconImage.setImageResource(R.drawable.error)

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        statusIconImage.imageTintList = null
                    }

                    descriptionText.setTextColor(ThemeUtils.getColor(itemView.context, R.attr.vctr_highlighted_message_text_color))
                }
                TroubleshootTest.TestStatus.SUCCESS -> {
                    progressBar.visibility = View.INVISIBLE
                    statusIconImage.visibility = View.VISIBLE
                    statusIconImage.setImageResource(R.drawable.abc_btn_check_to_on_mtrl_015)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        val color = ThemeUtils.getColor(itemView.context, R.attr.vctr_default_icon_tint_color)
                        statusIconImage.imageTintList = ColorStateList.valueOf(color)
                    }

                }
            }

            val quickFix = test.quickFix
            if (quickFix != null) {
                fixButton.text = test.quickFix!!.title
                fixButton.setOnClickListener { _ ->
                    test.quickFix!!.doFix()
                }
                fixButton.visibility = View.VISIBLE
            } else {
                fixButton.visibility = View.GONE
            }

            titleText.text = test.title
            val description = test.description
            if (description == null) {
                descriptionText.visibility = View.GONE
            } else {
                descriptionText.visibility = View.VISIBLE
                descriptionText.text = description
            }
        }

    }
}