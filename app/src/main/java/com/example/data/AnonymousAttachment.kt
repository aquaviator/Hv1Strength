package com.example.data

// A missing Firebase cache key is not evidence of anonymous ownership. Require the
// installation's explicit offline owner, local origin, and an unsynchronized record.
// In particular, never rewrite trusted Human ownership or a downloaded revision.
internal const val ANONYMOUS_ATTACHMENT_PREDICATE =
    "(userId IS NULL OR userId = 'offline') AND humanUserId = :offlineHumanId " +
    "AND originDeviceId = :localDeviceId AND originDeviceId != '' AND globalId != '' " +
    "AND lastSyncedAt IS NULL AND deletedAt IS NULL AND conflictState IS NULL " +
    "AND syncStatus IN ('LOCAL_ONLY', 'PENDING_UPLOAD')"
