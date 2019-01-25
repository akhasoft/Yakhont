/*
 * Copyright (C) 2015-2019 akha, a.k.a. Alexander Kharitonov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package akha.yakhont.demosimplekotlin.retrofit

import android.os.Handler
import android.os.Looper
import android.util.Log

import java.io.IOException
import java.util.ArrayList
import java.util.concurrent.TimeUnit

import okhttp3.Call
import okhttp3.Callback
import okhttp3.Connection
import okhttp3.Interceptor
import okhttp3.Interceptor.Chain
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.WebSocket
import okhttp3.WebSocketListener

import okio.ByteString

abstract class LocalJsonClient2Base: OkHttpClient() {

    private val mInterceptors = ArrayList<Interceptor>()
    private var mEmulatedNetworkDelay: Int = 0

    protected abstract fun getJson(): String

    fun setEmulatedNetworkDelay(delay: Int): LocalJsonClient2Base {
        mEmulatedNetworkDelay = delay
        return this
    }

    fun add(interceptor: Interceptor): LocalJsonClient2Base {
        return handle(interceptor, true)
    }

    fun remove(interceptor: Interceptor): LocalJsonClient2Base {
        return handle(interceptor, false)
    }

    private fun handle(interceptor: Interceptor?, add: Boolean): LocalJsonClient2Base {
        if (interceptor == null)
            Log.w(TAG, "interceptor == null")
        else {
            val result = if (add) mInterceptors.add(interceptor) else mInterceptors.remove(interceptor)
            if (!result) Log.e(TAG, "can't " + (if (add) "add" else "remove") + " interceptor " + interceptor)
        }
        return this
    }

    private fun handle(request: Request): Response {
        return Response.Builder()
                .code(200)
                .protocol(Protocol.HTTP_1_0)
                .request(request)
                .message("")
                .body(ResponseBody.create(MediaType.parse("application/json"),
                        getJson().toByteArray()))
                .build()
    }

    private inner class CallI constructor(private val mRequest: Request): Call {

        override fun execute(): Response {
            val response = handle(mRequest)
            if (mInterceptors.size > 0) {
                val chain = ChainI(response)
                for (interceptor in mInterceptors)
                    try {
                        interceptor.intercept(chain)
                    }
                    catch (exception: Exception) {
                        Log.e(TAG, "interceptor failed", exception)
                    }
            }
            return response
        }

        override fun enqueue(responseCallback: Callback) {
            val runnable = Runnable {
                object: Thread() {
                    override fun run() {
                        enqueueWrapper(responseCallback)
                    }
                }.start()
            }
            if (mEmulatedNetworkDelay <= 0)
                runnable.run()
            else
                Handler(Looper.getMainLooper()).postDelayed(runnable, (mEmulatedNetworkDelay * 1000).toLong())
        }

        private fun enqueueWrapper(responseCallback: Callback) {
            try {
                responseCallback.onResponse(this, execute())
            }
            catch (exception: IOException) {
                responseCallback.onFailure(this, exception)
            }
        }

        override fun clone     (): Call    { return CallI(mRequest) }
        override fun request   (): Request { return mRequest        }
        override fun cancel    ()          {                        }
        override fun isExecuted(): Boolean { return false           }
        override fun isCanceled(): Boolean { return false           }

        private inner class ChainI constructor(private val mResponse: Response): Chain {

            // for application interceptors this is always null
            override fun connection          (                   ): Connection? { return null       }
            override fun call                (                   ): Call        { return this@CallI }
            override fun request             (                   ): Request     { return mRequest   }
            override fun proceed             (r: Request         ): Response    { return mResponse  }
            override fun connectTimeoutMillis(                   ): Int         { return 0          }
            override fun withConnectTimeout  (i: Int, t: TimeUnit): Chain       { return this       }
            override fun readTimeoutMillis   (                   ): Int         { return 0          }
            override fun withReadTimeout     (i: Int, t: TimeUnit): Chain       { return this       }
            override fun writeTimeoutMillis  (                   ): Int         { return 0          }
            override fun withWriteTimeout    (i: Int, t: TimeUnit): Chain       { return this       }
        }
    }

    override fun newCall(request: Request): Call {
        return CallI(request)
    }

    override fun newWebSocket(request: Request, listener: WebSocketListener): WebSocket {
        return object: WebSocket {
            override fun request  (                  ): Request { return request }
            override fun queueSize(                  ): Long    { return 0       }
            override fun send     (s: String         ): Boolean { return true    }
            override fun send     (b: ByteString     ): Boolean { return true    }
            override fun close    (i: Int, s: String?): Boolean { return true    }
            override fun cancel   (                  )          {                }
        }
    }

    companion object {
        private const val TAG = "LocalJsonClient2"
    }
}
