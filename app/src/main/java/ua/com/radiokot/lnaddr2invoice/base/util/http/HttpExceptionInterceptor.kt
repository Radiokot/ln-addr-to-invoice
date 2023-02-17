package ua.com.radiokot.lnaddr2invoice.base.util.http

import okhttp3.Interceptor
import okhttp3.Response
import ua.com.radiokot.lnaddr2invoice.base.util.http.HttpException
import java.net.HttpURLConnection

class HttpExceptionInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        return if (response.code >= HttpURLConnection.HTTP_BAD_REQUEST)
            throw HttpException(response)
        else
            response
    }
}