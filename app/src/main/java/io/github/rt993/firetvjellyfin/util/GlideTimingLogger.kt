package io.github.rt993.firetvjellyfin.util

import android.util.Log
import okhttp3.Call
import okhttp3.Connection
import okhttp3.EventListener
import okhttp3.Handshake
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy

private const val TAG = "GlideTiming"

/**
 * Temporary diagnostic instrumentation - logs a millisecond-resolution breakdown (DNS, TCP
 * connect, TLS, request, response) of every Glide image request's OkHttp lifecycle, so the ~10s
 * delay before the first image loads each session can actually be pinned down instead of guessed
 * at. Filter logcat with `adb logcat -s GlideTiming` while reproducing it. Remove once the slow
 * phase is identified and actually fixed - this isn't meant to ship long-term.
 */
object GlideTimingEventListenerFactory : EventListener.Factory {
    override fun create(call: Call): EventListener = TimingEventListener(call.request().url.toString())
}

private class TimingEventListener(private val url: String) : EventListener() {
    private var startNanos = 0L

    private fun elapsedMs() = (System.nanoTime() - startNanos) / 1_000_000

    override fun callStart(call: Call) {
        startNanos = System.nanoTime()
        Log.i(TAG, "callStart $url")
    }

    override fun dnsStart(call: Call, domainName: String) {
        Log.i(TAG, "+${elapsedMs()}ms dnsStart $domainName")
    }

    override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<InetAddress>) {
        Log.i(TAG, "+${elapsedMs()}ms dnsEnd -> $inetAddressList")
    }

    override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
        Log.i(TAG, "+${elapsedMs()}ms connectStart $inetSocketAddress")
    }

    override fun secureConnectStart(call: Call) {
        Log.i(TAG, "+${elapsedMs()}ms secureConnectStart")
    }

    override fun secureConnectEnd(call: Call, handshake: Handshake?) {
        Log.i(TAG, "+${elapsedMs()}ms secureConnectEnd")
    }

    override fun connectEnd(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy, protocol: Protocol?) {
        Log.i(TAG, "+${elapsedMs()}ms connectEnd protocol=$protocol")
    }

    override fun connectFailed(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy, protocol: Protocol?, ioe: IOException) {
        Log.w(TAG, "+${elapsedMs()}ms connectFailed $inetSocketAddress: ${ioe.message}")
    }

    override fun connectionAcquired(call: Call, connection: Connection) {
        Log.i(TAG, "+${elapsedMs()}ms connectionAcquired $connection")
    }

    override fun requestHeadersStart(call: Call) {
        Log.i(TAG, "+${elapsedMs()}ms requestHeadersStart")
    }

    override fun requestHeadersEnd(call: Call, request: Request) {
        Log.i(TAG, "+${elapsedMs()}ms requestHeadersEnd")
    }

    override fun responseHeadersStart(call: Call) {
        Log.i(TAG, "+${elapsedMs()}ms responseHeadersStart")
    }

    override fun responseHeadersEnd(call: Call, response: Response) {
        Log.i(TAG, "+${elapsedMs()}ms responseHeadersEnd code=${response.code}")
    }

    override fun responseBodyStart(call: Call) {
        Log.i(TAG, "+${elapsedMs()}ms responseBodyStart")
    }

    override fun responseBodyEnd(call: Call, byteCount: Long) {
        Log.i(TAG, "+${elapsedMs()}ms responseBodyEnd bytes=$byteCount")
    }

    override fun callEnd(call: Call) {
        Log.i(TAG, "+${elapsedMs()}ms callEnd")
    }

    override fun callFailed(call: Call, ioe: IOException) {
        Log.w(TAG, "+${elapsedMs()}ms callFailed: ${ioe.message}")
    }
}
