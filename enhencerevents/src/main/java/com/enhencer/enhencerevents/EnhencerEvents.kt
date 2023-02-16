package com.enhencer.enhencerevents

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import com.facebook.appevents.AppEventsLogger
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.random.Random


class EnhencerEvents private constructor(private val applicationContext: Context, private val fbLogger: AppEventsLogger, private val token: String) {

    private val domain: String = "https://collect-app.enhencer.com/api"
    val listingUrl: String = "$domain/listings/"
    val productUrl: String = "$domain/products/"
    val purchaseUrl: String = "$domain/purchases/"
    val customerUrl: String = "$domain/customers/"

    val type = "ecommerce"
    val deviceType = "Android"

    var visitorId = ""

    companion object {
        @Volatile
        private lateinit var instance: EnhencerEvents

        fun getInstance(applicationContext: Context, fbLogger: AppEventsLogger, token: String): EnhencerEvents {
            synchronized(this) {
                if (!::instance.isInitialized) {
                    instance = EnhencerEvents(applicationContext, fbLogger, token)
                }
                return instance;
            }
        }
    }

    fun getVId(): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        var vId: String = prefs.getString("enh_visitor_id", "").toString()
        if(vId == null || vId == "") {
            vId = generateVisitorId()
            val editor = prefs.edit()
            editor.putString("enh_visitor_id", vId)
            editor.commit()
        }
        return vId
    }

    fun generateVisitorId(): String {
        val randNumber: Int = Random.nextInt(999)
        val timeMilli = System.currentTimeMillis() + randNumber
        return randNumber.toString() + timeMilli.toString()
    }

    fun sendFbEvent(response: String, fbLogger: AppEventsLogger) {
        val jsonObject = JSONTokener(response).nextValue() as JSONObject
        val audiences = jsonObject.get("audiences") as JSONArray
        for(i in 0 until audiences.length()) {
            val audience = audiences.getJSONObject(i)
            fbLogger.logEvent(audience.getString("name"), 0.0)
        }
    }

    fun sendRequest(jsonObjectString: String, url: String, requestMethod: String): String {
        val url = URL(url)

        val httpURLConnection = url.openConnection() as HttpURLConnection

        httpURLConnection.requestMethod = requestMethod
        httpURLConnection.setRequestProperty("Content-Type", "application/json") // The format of the content we're sending to the server
        httpURLConnection.setRequestProperty("Accept", "application/json") // The format of response we want to get from the server
        httpURLConnection.doInput = true
        httpURLConnection.doOutput = true

        // Send the JSON we created
        val outputStreamWriter = OutputStreamWriter(httpURLConnection.outputStream)
        outputStreamWriter.write(jsonObjectString)
        outputStreamWriter.flush()


        // Check if the connection is successful
        val responseCode = httpURLConnection.responseCode
        if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_ACCEPTED || responseCode == HttpURLConnection.HTTP_CREATED) {
            val response = httpURLConnection.inputStream.bufferedReader()
                .use { it.readText() }  // defaults to UTF-8
            // Convert raw JSON to pretty JSON using GSON library
            val gson = GsonBuilder().setPrettyPrinting().create()
            // Log.d("Pretty Printed JSON :", result.toString())
            return gson.toJson(JsonParser.parseString(response))

        } else {
            Log.e("HTTPURLCONNECTION_ERROR", responseCode.toString())
            return ""
        }
    }

    fun listingPage(listingCategory1: String, listingCategory2: String) {
        visitorId = getVId()
        val userId = token
        val source = ""

        val jsonObj = JSONObject()
        jsonObj.put("type", type)
        jsonObj.put("visitorID", visitorId)
        jsonObj.put("productCategory1", listingCategory1)
        jsonObj.put("productCategory2", listingCategory2)
        jsonObj.put("deviceType", deviceType)
        jsonObj.put("source", source)
        jsonObj.put("userID", userId)
        jsonObj.put("id", visitorId)
        val jsonObjectString = jsonObj.toString()

        val requestMethod = "POST"

        sendRequest(jsonObjectString, listingUrl, requestMethod)

        sendRequest(jsonObjectString, customerUrl, requestMethod)

        score(visitorId)
    }

    fun productPage(productCategory: String, productId: String, productPrice: Int) {
        visitorId = getVId()
        val userId = token
        val source = ""
        val jsonObj = JSONObject()
        jsonObj.put("type", type)
        jsonObj.put("visitorID", visitorId)
        jsonObj.put("productID", productId)
        jsonObj.put("productCategory2", productCategory)
        jsonObj.put("price", productPrice)
        jsonObj.put("deviceType", deviceType)
        jsonObj.put("productViewer", 1)
        jsonObj.put("source", source)
        jsonObj.put("actionType", "product")
        jsonObj.put("userID", userId)
        jsonObj.put("id", visitorId)
        val jsonObjectString = jsonObj.toString()

        val requestMethod = "POST"

        sendRequest(jsonObjectString, productUrl, requestMethod)

        sendRequest(jsonObjectString, customerUrl, requestMethod)

        score(visitorId)
    }

    fun addToBasket(productId: String) {
        visitorId = getVId()
        val userId = token
        val jsonObj = JSONObject()
        jsonObj.put("type", type)
        jsonObj.put("visitorID", visitorId)
        jsonObj.put("productID", productId)
        jsonObj.put("actionType", "basket")
        jsonObj.put("userID", userId)
        jsonObj.put("id", visitorId)
        val jsonObjectString = jsonObj.toString()

        val requestMethod = "POST"

        sendRequest(jsonObjectString, purchaseUrl, requestMethod)

        sendRequest(jsonObjectString, customerUrl, requestMethod)

        score(visitorId)
    }

    data class PRODUCT(
        val id: String,
        val quantity: Int,
        val price: Double,
    )

    fun purchase() {
        visitorId = getVId()
        val basketId = System.currentTimeMillis()
        val product = JSONObject()
        product.put("id", "no-id")
        product.put("quantity", 1)
        product.put("price", 1)
        val products = JSONArray(listOf(mapOf("id" to "no-id", "quantity" to 1, "price" to 1)))
        val userId = token
        val jsonObj = JSONObject()
        jsonObj.put("type", type)
        jsonObj.put("visitorID", visitorId)
        jsonObj.put("basketID", basketId)
        jsonObj.put("products", products)
        jsonObj.put("actionType", "purchase")
        jsonObj.put("userID", userId)
        jsonObj.put("id", visitorId)
        val jsonObjectString = jsonObj.toString()

        val requestMethod = "POST"

        sendRequest(jsonObjectString, purchaseUrl, requestMethod)

        sendRequest(jsonObjectString, customerUrl, requestMethod)

        score(visitorId)
    }

    fun score(visitorId: String) {
        val deviceOsVersion = android.os.Build.VERSION.RELEASE
        val userId = token
        val source = ""
        val jsonObj = JSONObject()
        jsonObj.put("type", type)
        jsonObj.put("visitorID", visitorId)
        jsonObj.put("deviceType", "a2")
        jsonObj.put("deviceOsVersion", deviceOsVersion)
        jsonObj.put("source", source)
        jsonObj.put("userID", userId)
        jsonObj.put("id", visitorId)
        val jsonObjectString = jsonObj.toString()

        val requestMethod = "PUT"

        val url = customerUrl + visitorId

        val response = sendRequest(jsonObjectString, url, requestMethod)

        sendFbEvent(response, fbLogger)
    }
}