/*
 * MIT License
 *
 * Copyright (c) 2020 ultranity
 * Copyright (c) 2019 Perol_Notsfsssf
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE
 */

package com.perol.asdpl.pixivez.services

import android.app.Activity
import android.app.Application
import android.content.SharedPreferences
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import com.arialyy.annotations.Download
import com.arialyy.aria.core.Aria
import com.arialyy.aria.core.task.DownloadTask
import com.google.gson.Gson
//import com.google.android.play.core.missingsplits.MissingSplitsManagerFactory
import com.perol.asdpl.pixivez.BuildConfig
import com.perol.asdpl.pixivez.R
import com.perol.asdpl.pixivez.objects.CrashHandler
import com.perol.asdpl.pixivez.objects.Toasty
import com.tencent.bugly.Bugly
import com.tencent.bugly.beta.Beta
import java.io.File
import com.tencent.mmkv.MMKV

class PxEZApp : Application() {
    lateinit var pre: SharedPreferences

    @Download.onTaskComplete
    fun taskComplete(task: DownloadTask?) {
        task?.let {
            val extendField = it.extendField
            val illustD = Gson().fromJson(extendField, IllustD::class.java)
            val title = illustD.title
            val sourceFile = File(it.filePath)
            if(sourceFile.isFile){
                val needCreateFold = pre.getBoolean("needcreatefold", false)
                val name = illustD.userName?.toLegal()
                val targetFile = File("$storepath/" +
                        (if (R18Folder && sourceFile.name.startsWith("？")) R18FolderPath else "") +
                        if (needCreateFold) "${name}_${illustD.userId}" else "",
                    sourceFile.name.removePrefix("？"))
                sourceFile.copyTo(targetFile, overwrite = true)
                MediaScannerConnection.scanFile(
                    this,
                    arrayOf(targetFile.path),
                    arrayOf(
                        MimeTypeMap.getSingleton()
                            .getMimeTypeFromExtension(targetFile.extension)
                    )
                ) { _, _ ->
                }
                sourceFile.delete()

                if(PxEZApp.ShowDownloadToast) {
                    Toasty.success(
                        this,
                        "${title}${getString(R.string.savesuccess)}",
                        Toast.LENGTH_SHORT
                    )
                        .show()
                }
            }
        }
    }

    override fun onCreate() {
        //https://developer.android.com/guide/app-bundle/sideload-check#missing_splits
        /*if (BuildConfig.ISGOOGLEPLAY)
            if (MissingSplitsManagerFactory.create(this).disableAppIfMissingRequiredSplits()) {
                // Skip app initialization.
                return
            }*/
        super.onCreate()
        //LeakCanary.install(this);
        pre = PreferenceManager.getDefaultSharedPreferences(this)
        Aria.init(this)
        Aria.download(this).register()

        Aria.get(this).apply {
            downloadConfig.apply {
//                queueMod=QueueMod.NOW.tag
                maxTaskNum = pre.getString("max_task_num", "2")!!.toInt()
                threadNum = pre.getString("thread_num", "2")!!.toInt()
            }
            appConfig.apply {
                isNotNetRetry = true
            }
        }

        Thread(Runnable {
            //Aria.download(this).removeAllTask(true)
            Aria.download(this).allCompleteTask?.forEach {
                Aria.download(this).load(it.id).cancel()
            }
            if( pre.getBoolean("resume_unfinished_task",true)
                //&& Aria.download(this).allNotCompleteTask?.isNotEmpty()
            )
            {
                //Toasty.normal(this, getString(R.string.unfinished_task_title), Toast.LENGTH_SHORT).show()
                Aria.download(this).allNotCompleteTask?.forEach {
                    Aria.download(this).load(it.id).cancel(true)
                    val illustD = Gson().fromJson(it.str, IllustD::class.java)
                    Aria.download(this).load(it.url)
                        .setFilePath(it.filePath) //设置文件保存的完整路径
                        .ignoreFilePathOccupy()
                        .setExtendField(Gson().toJson(illustD))
                        .option(Works.option)
                        .create()
                    Thread.sleep(550)
                }
            }
        }).start()
        instance = this
        AppCompatDelegate.setDefaultNightMode(
            pre.getString(
                "dark_mode",
                "-1"
            )!!.toInt()
        )
        animationEnable = pre.getBoolean("animation", true)
        ShowDownloadToast = pre.getBoolean("ShowDownloadToast", true)
        CollectMode = pre.getString("CollectMode", "0")?.toInt() ?: 0
        R18Private = pre.getBoolean("R18Private", true)
        R18Folder = pre.getBoolean("R18Folder", false)
        R18FolderPath = pre.getString("R18FolderPath", "xRestrict/")!!
        TagSeparator = pre.getString("TagSeparator", "#")!!
        language = pre.getString("language", "0")?.toInt() ?: 0
        storepath = pre.getString(
            "storepath1",
            Environment.getExternalStorageDirectory().absolutePath + File.separator + "PxEz"
        )!!
        saveformat = pre.getString("filesaveformat","{illustid}({userid})_{title}_{part}{type}")!!
        if (pre.getBoolean("crashreport", true)) {
            CrashHandler.getInstance().init(this)
        }
        locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            resources.configuration.locales.get(0).language
        } else {
            resources.configuration.locale.language
        }

        Beta.upgradeDialogLayoutId = R.layout.upgrade_dialog
        Beta.enableHotfix = false
        //Beta.autoCheckUpgrade = pre.getBoolean("autocheck",true)
        Beta.storageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        Bugly.init(this, "5f21ff45b7", BuildConfig.DEBUG)

        if(pre.getBoolean("infoCache", true))
            MMKV.initialize(this)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                ActivityCollector.collect(activity)
            }

            override fun onActivityStarted(activity: Activity) {
            }

            override fun onActivityResumed(activity: Activity) {
            }

            override fun onActivityPaused(activity: Activity) {
            }

            override fun onActivityStopped(activity: Activity) {
            }

            override fun onActivityDestroyed(activity: Activity) {
                ActivityCollector.discard(activity)
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
                //
            }
        })
    }

    private val Activity.simpleName get() = javaClass.simpleName

    object ActivityCollector {
        @JvmStatic
        private val activityList = mutableListOf<Activity>()

        @JvmStatic
        fun collect(activity: Activity) {
            activityList.add(activity)
        }

        @JvmStatic
        fun discard(activity: Activity) {
            activityList.remove(activity)
        }

        @JvmStatic
        fun recreate() {
            for (i in activityList.size - 1 downTo 0) {
                activityList[i].recreate()
            }
        }
    }

    companion object {
        @JvmStatic
        var storepath = ""

        @JvmStatic
        var R18Folder: Boolean = false

        @JvmStatic
        var R18FolderPath = "xrestrict"

        @JvmStatic
        var saveformat = ""

        @JvmStatic
        var locale = "zh"

        @JvmStatic
        var language: Int = 0

        @JvmStatic
        var animationEnable: Boolean = false

        @JvmStatic
        var R18Private: Boolean = true

        @JvmStatic
        var ShowDownloadToast: Boolean = true

        @JvmStatic
        var CollectMode: Int = 0

        lateinit var instance: PxEZApp
        var autochecked = false

        @JvmStatic
        var TagSeparator: String = "#"

        private const val TAG = "PxEZApp"
    }
}

