package xyz.upperlevel.snowy.opensafe.util

import android.R
import android.app.Activity
import android.content.Context
import android.os.AsyncTask
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import java.lang.Exception

/**
 * Edited from: https://github.com/OldMusa-5H/OldMusaApp/blob/master/app/src/main/java/com/cnr_isac/oldmusa/util/ApiUtil.kt
 */
object AsyncUtil {
    class QueryAsyncTask<R>(val query: RawQuery<R>) : AsyncTask<Void, Void, Void>() {
        var result: R? = null
        var error: Exception? = null

        override fun doInBackground(vararg params: Void?): Void? {
            try {
                result = query.f()
            } catch (e: Exception) {
                error = e
            }
            return null
        }

        override fun onPostExecute(r: Void?) {
            query.completeTask(result, error)
        }

        override fun onCancelled() {
            query.completeTask(null, null)
        }
    }

    class RawQuery<R>(autoexec: Boolean = true, val f: () -> R) {
        private var resultCallbacks: MutableList<(R) -> Unit> = ArrayList()
        private var errorCallbacks: MutableList<(Exception) -> Unit> = ArrayList()
        private var doneCallbacks: MutableList<() -> Unit> = ArrayList()
        private var task: QueryAsyncTask<R> = QueryAsyncTask(this)

        private var done = false
        private var cancelled = false
        private var result: R? = null
        private var error: Exception? = null

        init {
            if (autoexec) task.execute()
        }

        fun onResult(callback: (R) -> Unit): RawQuery<R> {
            when (done) {
                false -> resultCallbacks.add(callback)
                true -> result?.let(callback)
            }
            return this
        }

        fun onRestError(callback: (Exception) -> Unit): RawQuery<R> {
            when (done) {
                false -> errorCallbacks.add(callback)
                true -> error?.let(callback)
            }
            return this
        }

        fun onDone(callback: () -> Unit): RawQuery<R> {
            when (done) {
                false -> doneCallbacks.add(callback)
                true -> callback()
            }
            return this
        }

        fun completeTask(result: R?, error: Exception?) {
            assert(!done) { "Task already completed" }
            this.result = result
            this.error = error
            done = true

            result?.let {res -> resultCallbacks.forEach { cb -> cb(res) } }
            error?.let { err -> errorCallbacks.forEach { cb -> cb(err) } }
            doneCallbacks.forEach { it() }
        }

        fun cancel(mayInterruptIfRunning: Boolean) {
            if (done) return
            this.cancelled = true
            this.task.cancel(mayInterruptIfRunning)
        }

        /**
         * Runs the task, only necessary if autoexec parameter was set to false
         */
        fun execute() {
            task.execute()
        }
    }

    class QueryLifecycleObserver<T>(val query: RawQuery<T>, val mayInterruptIfRunning: Boolean) :
        LifecycleObserver {
        @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        fun cancelQuery() {
            query.cancel(mayInterruptIfRunning)
        }
    }

    fun <R> Context.async(f: () -> R): RawQuery<R> {
        return RawQuery(true, f).onRestError { handleError(this.applicationContext, it) }
    }

    fun <R> Fragment.async(mayInterruptIfRunning: Boolean = false, f: () -> R): RawQuery<R> {
        val query = RawQuery(true, f)

        // Check if the fragment is paused, then cancel it to mitigate exceptions
        val observer = QueryLifecycleObserver(query, mayInterruptIfRunning)
        this.lifecycle.addObserver(observer)
        query.onDone {
            lifecycle.removeObserver(observer)
        }

        query.onRestError { handleError(this.context!!.applicationContext, it) }
        return query
    }

    fun handleError(ctx: Context, e: Exception) {
        Toast.makeText(ctx, e.message, Toast.LENGTH_LONG).show()
        Log.e("ASync", "An exception occurred", e)
    }

    /**
     * Displays a loading bar while the query is running.
     * This also disables user interaction
     */
    fun <P> RawQuery<P>.useLoadingBar(context: Activity): RawQuery<P> {
        val layout = RelativeLayout(context)

        val progressBar = ProgressBar(context, null, R.attr.progressBarStyleLarge)
        val params = RelativeLayout.LayoutParams(400, 400)
        params.addRule(RelativeLayout.CENTER_IN_PARENT)

        layout.addView(progressBar, params)
        context.addContentView(layout, RelativeLayout.LayoutParams(-1, -1))
        progressBar.visibility = View.VISIBLE  // Show ProgressBar
        context.window.setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)


        this.onDone {
            progressBar.visibility = View.GONE // Hide ProgressBar
            (layout.parent as ViewGroup).removeView(layout)
            context.window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        }
        return this
    }

    fun <P> RawQuery<P>.useLoadingBar(context: Fragment): RawQuery<P> {
        return useLoadingBar(context.activity!!)
    }
}