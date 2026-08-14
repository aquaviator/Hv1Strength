package com.example.data

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore

internal object BuildVariantSyncFirestoreFactory {
    fun create(context: Context): FirebaseFirestore = FirebaseFirestore.getInstance()
}
