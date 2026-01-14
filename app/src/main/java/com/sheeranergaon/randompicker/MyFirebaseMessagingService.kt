package com.sheeranergaon.randompicker

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    // This runs when a new notification arrives
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d("FCM", "From: ${remoteMessage.from}")

        // You can handle the notification data here
        remoteMessage.notification?.let {
            Log.d("FCM", "Message Notification Body: ${it.body}")
        }
    }

    // Every device gets a unique token. You need this to send a message to a specific user
    override fun onNewToken(token: String) {
        Log.d("FCM", "Refreshed token: $token")
        // In a real app, you would save this token to your Firebase Database
    }
}