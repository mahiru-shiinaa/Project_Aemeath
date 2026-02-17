package com.aemeath.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "web_apps")
data class WebAppEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,           // "Facebook", "Gmail", v.v.
    val iconEmoji: String = "🌐", // Emoji icon mặc định
    val iconBase64: String? = null, // Ảnh icon tùy chỉnh (base64)
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "accounts",
    foreignKeys = [
        androidx.room.ForeignKey(
            entity = WebAppEntity::class,
            parentColumns = ["id"],
            childColumns = ["webAppId"],
            onDelete = androidx.room.ForeignKey.CASCADE
        )
    ]
)
data class AccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val webAppId: Long,
    val title: String,              // "Tài khoản chính", v.v.
    val username: String,
    val encryptedPassword: String,  // Đã mã hóa AES-256
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// ─── App Settings (lưu trong Room, không phải DataStore) ─────────────────────
@Entity(tableName = "app_settings")
data class AppSettingEntity(
    @PrimaryKey
    val key: String,
    val value: String
)