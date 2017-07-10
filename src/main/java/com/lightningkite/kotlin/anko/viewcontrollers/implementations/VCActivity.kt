package com.lightningkite.kotlin.anko.viewcontrollers.implementations

import android.annotation.TargetApi
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.view.View
import com.lightningkite.kotlin.anko.async.AndroidAsync
import com.lightningkite.kotlin.anko.viewcontrollers.ViewController
import com.lightningkite.kotlin.anko.viewcontrollers.containers.VCContainer
import com.lightningkite.kotlin.runAll
import java.util.*

/**
 * All activities hosting [ViewController]s must be extended from this one.
 * It handles the calling of other activities with [onActivityResult], the attaching of a
 * [VCContainer], and use the back button on the [VCContainer].
 * Created by jivie on 10/12/15.
 */
abstract class VCActivity : Activity() {

    abstract val viewController: ViewController

    var vcView: View? = null
    var savedInstanceState: Bundle? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidAsync.init()
        super.onCreate(savedInstanceState)
        this.savedInstanceState = savedInstanceState

        vcView = viewController.make(this)
        setContentView(vcView!!)
    }

    val onStart = HashSet<() -> Unit>()
    override fun onStart() {
        super.onStart()
        onStart.runAll()
    }

    val onResume = HashSet<() -> Unit>()
    override fun onResume() {
        super.onResume()
        onResume.runAll()
    }

    val onStop = HashSet<() -> Unit>()
    override fun onStop() {
        super.onStop()
        onStop.runAll()
    }

    val onPause = HashSet<() -> Unit>()
    override fun onPause() {
        onPause.runAll()
        super.onPause()
    }

    val onSaveInstanceState = HashSet<(outState: Bundle) -> Unit>()
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        onSaveInstanceState.runAll(outState)
    }

    val onLowMemory = HashSet<() -> Unit>()
    override fun onLowMemory() {
        super.onLowMemory()
        onLowMemory.runAll()
    }

    override fun onBackPressed() {
        viewController.onBackPressed {
            super.onBackPressed()
        }
    }

    val onDestroy = HashSet<() -> Unit>()
    override fun onDestroy() {
        if (vcView != null) {
            viewController.unmake(vcView!!)
            vcView = null
        }
        onDestroy.runAll()
        super.onDestroy()
    }

    val requestReturns: HashMap<Int, (Map<String, Int>) -> Unit> = HashMap()

    companion object {
        val returns: HashMap<Int, (Int, Intent?) -> Unit> = HashMap()
    }

    val onActivityResult = ArrayList<(Int, Int, Intent?) -> Unit>()

    fun prepareOnResult(onResult: (Int, Intent?) -> Unit = { a, b -> }): Int {
        val generated: Int = (Math.random() * Int.MAX_VALUE).toInt()
        returns[generated] = onResult
        return generated
    }

    fun prepareOnResult(presetCode: Int, onResult: (Int, Intent?) -> Unit = { a, b -> }): Int {
        returns[presetCode] = onResult
        return presetCode
    }

    fun startIntent(intent: Intent, options: Bundle = Bundle.EMPTY, onResult: (Int, Intent?) -> Unit = { a, b -> }) {
        val generated: Int = (Math.random() * Int.MAX_VALUE).toInt()
        returns[generated] = onResult
        startActivityForResult(intent, generated, options)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        onActivityResult.runAll(requestCode, resultCode, data)
        returns[requestCode]?.invoke(resultCode, data)
        returns.remove(requestCode)
    }

    /**
     * Requests a bunch of permissions and returns a map of permissions that were previously ungranted and their new status.
     */
    fun requestPermissions(permission: Array<String>, onResult: (Map<String, Int>) -> Unit) {
        val ungranted = permission.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (ungranted.isNotEmpty()) {
            val generated: Int = (Math.random() * Int.MAX_VALUE).toInt()

            requestReturns[generated] = onResult

            ActivityCompat.requestPermissions(this, ungranted.toTypedArray(), generated)

        } else {
            onResult(emptyMap())
        }
    }

    /**
     * Requests a single permissions and returns whether it was granted or not.
     */
    fun requestPermission(permission: String, onResult: (Boolean) -> Unit) {
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {

            val generated: Int = (Math.random() * Int.MAX_VALUE).toInt()
            requestReturns[generated] = {
                onResult(it[permission] == PackageManager.PERMISSION_GRANTED)
            }
            ActivityCompat.requestPermissions(this, arrayOf(permission), generated)

        } else {
            onResult(true)
        }
    }

    @TargetApi(23)
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (Build.VERSION.SDK_INT >= 23) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)

            val map = HashMap<String, Int>()
            for (i in permissions.indices) {
                map[permissions[i]] = grantResults[i]
            }
            requestReturns[requestCode]?.invoke(map)

            requestReturns.remove(requestCode)
        }
    }
}