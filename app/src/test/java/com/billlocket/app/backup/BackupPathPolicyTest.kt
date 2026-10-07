package com.billlocket.app.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupPathPolicyTest {
    @Test fun `accepts only relative normalized archive entries`() {
        assertTrue(isSafeBackupEntry("media/abc.jpg"))
        assertTrue(isSafeBackupEntry("manifest.json"))
        assertFalse(isSafeBackupEntry("../database"))
        assertFalse(isSafeBackupEntry("media/../../database"))
        assertFalse(isSafeBackupEntry("/absolute"))
        assertFalse(isSafeBackupEntry("media\\escape.jpg"))
    }
}
