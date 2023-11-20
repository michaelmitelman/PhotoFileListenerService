package com.example.photofilelistenerservice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.FileObserver
import android.os.IBinder
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException

class PhotoUploadService : Service() {

    private val scope = CoroutineScope(Dispatchers.Default)
    private var fileObserver: FileObserver? = null

    companion object {
        const val CHANNEL_ID = "UploadServiceChannel"
        const val NOTIFICATION_ID = 1
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, createNotification())
        startFileObserver()
        startUploading()
    }

    private fun startFileObserver() {
        val directoryPath = getCameraDirectoryPath()
        fileObserver = object : FileObserver(directoryPath, FileObserver.CREATE) {
            override fun onEvent(event: Int, path: String?) {
                if (event == FileObserver.CREATE) {
                    path?.let {
                        val imagePath = "$directoryPath/$path"
                        val imageFile = File(imagePath)
                        if (imageFile.exists()) {
                            scope.launch {
                                uploadImage(imageFile)
                            }
                        }
                    }
                }
            }
        }
        fileObserver?.startWatching()
    }

    private fun getCameraDirectoryPath(): String {
        val projection = arrayOf(MediaStore.Images.Media.DATA)
        val cursor = contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Images.Media.DATE_TAKEN} DESC"
        )
        cursor?.use {
            if (it.moveToFirst()) {
                val columnIndex = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                return File(it.getString(columnIndex)).parent
            }
        }
        // Default to the DCIM directory if no image is found (modify as needed)
        return "${android.os.Environment.getExternalStorageDirectory()}/DCIM/Camera"
    }

    private fun startUploading() {
        scope.launch {
            while (isActive) {
                // Optionally, you can implement a delay or adjust the frequency of uploads
                delay(5000)
            }
        }
    }

    private suspend fun uploadImage(image: File) {
        withContext(Dispatchers.IO) {
            try {
                val url = "http://localhost:8000/Pictures/"
                val client = OkHttpClient()

                val requestBody: RequestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("image", image.name, image.asRequestBody("image/*".toMediaType()))
                    .build()

                val request: Request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    // Handle server errors
                    throw IOException("Unexpected code $response")
                }

                // Process the successful response if needed

            } catch (e: Exception) {
                // Handle exceptions (e.g., network issues, server errors)
            }
        }
    }

    private fun createNotification(): Notification {
        // Create a notification channel (required for Android 8.0 and above)
        createNotificationChannel()

        // Create a notification intent (you can customize this based on your app's requirements)
        val notificationIntent = Intent(this, PhotoUploadService::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Build the notification
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Uploading Service")
            .setContentText("Service is running")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val name = "UploadServiceChannel"
            val descriptionText = "Channel for Upload Service"
            val channel = NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_LOW).apply {
                description = descriptionText
            }

            // Register the channel with the system
            val notificationManager: NotificationManagerCompat =
                NotificationManagerCompat.from(this)
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        fileObserver?.stopWatching()
        scope.cancel()
    }
}
