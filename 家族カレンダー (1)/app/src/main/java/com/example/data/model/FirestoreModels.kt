package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@IgnoreExtraProperties
data class UserProfile(
  @DocumentId val id: String = "",
  val uid: String = "",
  val email: String = "",
  val displayName: String = "",
  val photoUrl: String? = null,
  val colorHex: String = "#4285F4",
  val avatarSymbol: String = "🏠",
  val avatarImageBase64: String = "",
  val activeFamilyId: String? = null,
  val pendingFamilyId: String? = null,
  val pendingFamilyName: String? = null,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class FamilyGroup(
  @DocumentId val id: String = "",
  val name: String = "",
  val iconSymbol: String = "📅",
  val colorHex: String = "#D97757",
  val iconImageBase64: String = "",
  val calendarType: String = TYPE_SHARED,
  val ownerUid: String = "",
  val ownerEmail: String = "",
  val inviteCode: String = "",
  val memberUids: List<String> = emptyList(),
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  fun isSharedCalendar(): Boolean = calendarType != TYPE_PERSONAL

  /**
   * 自分用カレンダー、または共有カレンダーでもメンバーが自分1人以下の場合は「消す」、
   * 他のメンバーがいる共有カレンダーの場合は「退会」とする。
   */
  fun shouldDeleteInsteadOfLeave(): Boolean = !isSharedCalendar() || memberUids.size <= 1

  companion object {
    const val TYPE_PERSONAL = "personal"
    const val TYPE_SHARED = "shared"
  }
}

@IgnoreExtraProperties
data class FamilyMember(
  @DocumentId val id: String = "",
  val uid: String = "",
  val familyId: String = "",
  val displayName: String = "",
  val email: String = "",
  val colorHex: String = "#4285F4",
  val avatarSymbol: String = "🏠",
  val avatarImageBase64: String = "",
  val role: String = "member",
  val memberUids: List<String> = emptyList(),
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class JoinRequest(
  @DocumentId val id: String = "",
  val familyId: String = "",
  val familyOwnerUid: String = "",
  val requesterUid: String = "",
  val requesterName: String = "",
  val requesterEmail: String = "",
  val inviteCode: String = "",
  val status: String = "pending",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class CalendarEvent(
  @DocumentId val id: String = "",
  val familyId: String = "",
  val title: String = "",
  val description: String = "",
  val location: String = "",
  val dateKey: String = "",
  val startMinutes: Long = 540L,
  val endMinutes: Long = 600L,
  val isAllDay: Boolean = false,
  val assignedMemberUid: String = ASSIGNED_ALL,
  val reminderMinutesBefore: Long = DEFAULT_REMINDER_MINUTES,
  val createdByUid: String = "",
  val memberUids: List<String> = emptyList(),
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  companion object {
    const val ASSIGNED_ALL = "ALL"
    const val REMINDER_OFF = -1L
    const val DEFAULT_REMINDER_MINUTES = 0L

    val REMINDER_OPTIONS: List<Pair<Long, String>> = listOf(
      0L to "予定時刻",
      10L to "10分前",
      15L to "15分前",
      30L to "30分前",
      60L to "1時間前",
      REMINDER_OFF to "通知しない",
    )

    fun reminderLabel(minutesBefore: Long): String {
      return REMINDER_OPTIONS.firstOrNull { it.first == minutesBefore }?.second
        ?: if (minutesBefore < 0L) "通知しない" else "${minutesBefore}分前"
    }
  }
}

data class InvitePayload(
  val familyId: String,
  val inviteCode: String,
  val familyName: String,
  val ownerUid: String,
  val ownerEmail: String,
) {
  fun toQrUri(): String {
    val enc = StandardCharsets.UTF_8.name()
    return buildString {
      append("kazocal://join")
      append("?fid=").append(URLEncoder.encode(familyId, enc))
      append("&code=").append(URLEncoder.encode(inviteCode, enc))
      append("&name=").append(URLEncoder.encode(familyName, enc))
      append("&oid=").append(URLEncoder.encode(ownerUid, enc))
      append("&email=").append(URLEncoder.encode(ownerEmail, enc))
    }
  }

  fun toShareCodeString(): String = toQrUri()

  companion object {
    fun parse(rawInput: String): InvitePayload? {
      val trimmed = rawInput.trim()
      if (trimmed.isEmpty()) return null
      return try {
        val uri = URI(trimmed)
        if (uri.scheme == "kazocal" && uri.host == "join") {
          val enc = StandardCharsets.UTF_8.name()
          val params = (uri.rawQuery ?: "").split("&")
            .mapNotNull { pair ->
              val idx = pair.indexOf('=')
              if (idx <= 0) null
              else {
                val k = URLDecoder.decode(pair.substring(0, idx), enc)
                val v = URLDecoder.decode(pair.substring(idx + 1), enc)
                k to v
              }
            }
            .toMap()
          val fid = params["fid"].orEmpty().trim()
          val code = params["code"].orEmpty().trim().uppercase()
          val name = params["name"].orEmpty().ifBlank { "家族カレンダー" }
          val oid = params["oid"].orEmpty().trim()
          val email = params["email"].orEmpty().trim()
          if (fid.isNotEmpty() && code.length >= 6 && oid.isNotEmpty()) {
            InvitePayload(fid, code, name, oid, email)
          } else null
        } else {
          null
        }
      } catch (_: Exception) {
        null
      }
    }
  }
}

object MemberColorPalette {
  val googlePresetColors = listOf(
    "#4285F4", // Google Blue
    "#EA4335", // Google Red
    "#34A853", // Google Green
    "#FBBC04", // Google Yellow/Marigold
    "#A142F4", // Plum Purple
    "#24C1E0", // Sky Teal
    "#E07A5F", // Warm Coral
    "#6B9080", // Sage Green
    "#E91E63", // Berry Rose
    "#FF7043", // Tangerine
    "#5C6BC0", // Indigo
    "#26A69A", // Mint Teal
  )

  val avatarSymbols = listOf(
    "🏠", "👨", "👩", "👧", "👦", "👶", "👵", "👴", "🌸", "⭐", "🐾", "🍀"
  )

  fun pickNextColor(usedColors: Set<String>): String {
    return googlePresetColors.firstOrNull { hex ->
      usedColors.none { it.equals(hex, ignoreCase = true) }
    } ?: googlePresetColors[usedColors.size % googlePresetColors.size]
  }
}

object CalendarIconPalette {
  val calendarSymbols = listOf(
    "📅", "🏠", "💼", "🎒", "⚽", "🎵", "🍳", "🚗", "✈️", "🐾", "🏥", "🌸"
  )
}
