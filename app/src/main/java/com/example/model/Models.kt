package com.example.model

enum class UserRole(val roleName: String, val displayName: String, val description: String) {
  Admin("Admin", "App Admin", "Full administrative authority across system, all clusters, schools, users, and groups."),
  Cluster_Head("Cluster_Head", "Cluster Head", "Administrative officer for assigned cluster, its schools, and teachers."),
  School_HM("School_HM", "School HM", "Headmaster/Mistress managing teachers and school-level data collection."),
  Teacher("Teacher", "Teacher", "Classroom educator submitting daily reports, student data, and communications.")
}

data class UserSession(
  val id: String,
  val name: String,
  val email: String,
  val role: UserRole,
  val clusterId: String? = null,
  val clusterName: String? = null,
  val clusterCode: String? = null,
  val schoolId: String? = null,
  val schoolName: String? = null,
  val schoolCode: String? = null,
  val token: String,
  val status: String = "active",
  val mobile: String? = null
)

data class UserRecord(
  val id: String,
  val name: String,
  val email: String,
  val mobile: String,
  val role: UserRole,
  val clusterName: String,
  val clusterCode: String,
  val schoolName: String,
  val schoolCode: String,
  val address: String,
  val status: String,
  val createdAt: String
)

data class SchoolItem(
  val id: String,
  val name: String,
  val udiseCode: String,
  val clusterName: String,
  val teacherCount: Int,
  val studentCount: Int,
  val lastSyncTime: String
)

data class SchoolRecord(
  val id: String,
  val schoolName: String,
  val udiseCode: String,
  val clusterName: String,
  val clusterCode: String,
  val taluka: String,
  val district: String,
  val hmName: String,
  val hmMobile: String,
  val schoolType: String,
  val isActive: Boolean
)

class SchoolDirectorySeed(private val items: List<SchoolItem> = emptyList()) : List<SchoolItem> by items

data class ChatGroup(
  val id: String,
  val name: String,
  val lastMessage: String,
  val senderName: String,
  val unreadCount: Int = 0,
  val time: String,
  val scope: String,
  val groupType: String = "general",
  val memberCount: Int = 0,
  val photoKey: String? = null,
  val isActive: Boolean = true
)

data class GroupMemberCandidate(
  val id: String,
  val name: String,
  val email: String,
  val role: UserRole,
  val clusterName: String,
  val clusterCode: String,
  val schoolName: String,
  val schoolCode: String,
  val status: String
)

data class GroupMember(
  val id: String,
  val name: String,
  val email: String,
  val role: UserRole,
  val roleInGroup: String
)

data class GroupInfo(
  val id: String,
  val name: String,
  val description: String?,
  val groupType: String,
  val scopeType: String,
  val clusterCode: String?,
  val schoolCode: String?,
  val createdBy: String,
  val createdAt: String,
  val memberCount: Int,
  val members: List<GroupMember>,
  val isActive: Boolean = true
)

data class GroupCreateResult(
  val id: String,
  val name: String,
  val groupType: String,
  val scopeType: String,
  val clusterCode: String?,
  val schoolCode: String?,
  val description: String?,
  val memberCount: Int,
  val photoKey: String?
)

enum class ChatConnectionState { Connecting, Connected, Disconnected, Reconnecting, Error }

data class AttachmentUploadResult(
  val attachmentKey: String,
  val fileName: String,
  val mimeType: String,
  val fileSize: Long?,
  val messageType: String
)

data class GroupMessage(
  val id: String,
  val groupId: String = "",
  val groupName: String? = null,
  val senderId: String? = null,
  val senderName: String,
  val senderRole: UserRole? = null,
  val text: String? = null,
  val timestamp: String,
  val messageType: String = "text",
  val attachmentKey: String? = null,
  val attachmentName: String? = null,
  val mimeType: String? = null,
  val fileSize: Long? = null,
  val linkUrl: String? = null,
  val isDeleted: Boolean = false,
  val isRead: Boolean = false,
  val updatedAt: String? = null,
  val isMe: Boolean = false,
  val mediaUrl: String? = null,
  val clientMessageId: String? = null,
  val excelStatus: String = "editable",
  val excelVersion: Int = 1,
  val excelPublishedAt: String? = null,
  val excelPublishedBy: String? = null
)

data class SyncLog(
  val id: String,
  val title: String,
  val subtitle: String,
  val time: String,
  val iconType: String
)

data class InfrastructureStatus(
  val d1Status: String = "Online",
  val d1Latency: String = "9ms",
  val r2Status: String = "Online",
  val r2Assets: String = "1.2 GB",
  val honoVersion: String = "v3.12.0",
  val workerEndpoint: String = "kp-data-sync-backend.workers.dev"
)
