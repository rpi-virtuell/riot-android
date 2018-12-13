package im.vector.adapters

import android.content.res.ColorStateList
import android.os.Build
import android.support.transition.TransitionManager
import android.support.v4.content.ContextCompat
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import im.vector.R
import im.vector.ui.themes.ThemeUtils
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

abstract class TroubleshootTest(val id: String, val title: String) {

    enum class TestStatus {
        NOT_STARTED,
        RUNNING,
        FAILED,
        SUCCESS
    }

    var description: String? = null

    var status: TestStatus by Delegates.observable(TestStatus.NOT_STARTED) { _, _, _ ->
       statusListener?.invoke(this)
    }

    var statusListener : ((TroubleshootTest) -> Unit)? = null

    abstract fun perform()

    fun isFinihed() : Boolean {
        return status == TroubleshootTest.TestStatus.FAILED || status == TroubleshootTest.TestStatus.SUCCESS
    }

    var quickFix: TroubleshootQuickFix? = null


    abstract class TroubleshootQuickFix(val title: String) {
        abstract fun doFix()
    }
}




class NotificationTroubleshootRecyclerViewAdapter(tests: ArrayList<TroubleshootTest>) : RecyclerView.Adapter<NotificationTroubleshootRecyclerViewAdapter.ViewHolder>() {

    val mTestList : ArrayList<TroubleshootTest> = tests

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val itemView = inflater.inflate(R.layout.item_notification_troubleshoot,parent,false)
        return ViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val test = mTestList[position]
        holder.bind(test)
    }

    override fun getItemCount(): Int {
        return mTestList.size
    }


    class ViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {

        var titleText: TextView = itemView.findViewById(R.id.titleView)
        var descriptionText: TextView = itemView.findViewById(R.id.descriptionView)
        var statusIconImage: ImageView = itemView.findViewById(R.id.imageView)
        var progressBar: ProgressBar = itemView.findViewById(R.id.progressBar)
        var fixButton: Button = itemView.findViewById(R.id.button)


        fun bind(test: TroubleshootTest) {

            val context = itemView.context
            titleText.setTextColor(ContextCompat.getColor(context, R.color.riot_primary_background_color_black))
            descriptionText.setTextColor(ContextCompat.getColor(context, R.color.default_text_light_color_light))

            when (test.status) {
                TroubleshootTest.TestStatus.NOT_STARTED -> {
                    titleText.setTextColor(ContextCompat.getColor(context, R.color.primary_text_disabled_material_light))
                    descriptionText.setTextColor(ContextCompat.getColor(context, R.color.primary_text_disabled_material_light))

                    progressBar.visibility = View.INVISIBLE
                    statusIconImage.visibility = View.VISIBLE
                    statusIconImage.setImageResource(R.drawable.abc_btn_check_material)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        val color =  ContextCompat.getColor(context, R.color.notification_icon_bg_color)
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

                    descriptionText.setTextColor(ContextCompat.getColor(context, R.color.error_color_material))
                }
                TroubleshootTest.TestStatus.SUCCESS -> {
                    progressBar.visibility = View.INVISIBLE
                    statusIconImage.visibility = View.VISIBLE
                    statusIconImage.setImageResource(R.drawable.abc_btn_check_to_on_mtrl_015)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        val color =  ContextCompat.getColor(context, R.color.notification_icon_bg_color)
                        statusIconImage.imageTintList = ColorStateList.valueOf(color)
                    }

                }
            }

            val qf = test.quickFix?.let {
                // If b is not null.
                fixButton.text = it.title
                fixButton.setOnClickListener { _ ->
                    it.doFix()
                }
                fixButton.visibility = View.VISIBLE
            } ?: run {
                fixButton.visibility = View.GONE
            }

            titleText.text = test.title
            val description = test.description
//            (itemView as? ViewGroup)?.let {
//                TransitionManager.beginDelayedTransition(it)
//            }
            if (description == null) {
                descriptionText.visibility = View.GONE
            } else {
                descriptionText.visibility = View.VISIBLE
                descriptionText.text = description
            }
        }

    }
}