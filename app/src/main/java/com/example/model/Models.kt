package com.example.model

enum class UserRole(val roleName: String, val displayName: String, val description: String) {
  Admin(
    roleName = "Admin",
    displayName = "App Admin",
    description = "Full administrative authority across system, all clusters, schools, users, and groups."
  ),
  Cluster_Head(
    roleName = "Cluster_Head",
    displayName = "Cluster Head",
    description = "Administrative officer for assigned cluster, its schools, and teachers."
  ),
  School_HM(
    roleName = "School_HM",
    displayName = "School HM",
    description = "Headmaster/Mistress managing teachers and school-level data collection."
  ),
  Teacher(
    roleName = "Teacher",
    displayName = "Teacher",
    description = "Classroom educator submitting daily reports, student data, and communications."
  )
}

data class UserSession(
  val id: String,
  val name: String,
  val email: String,
  val role: UserRole,
  val clusterId: String? = null,
  val clusterName: String? = null,
  val schoolId: String? = null,
  val schoolName: String? = null,
  val token: String,
  val status: String = "active"
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

data class ChatGroup(
  val id: String,
  val name: String,
  val lastMessage: String,
  val senderName: String,
  val unreadCount: Int = 0,
  val time: String,
  val scope: String
)

data class GroupMessage(
  val id: String,
  val senderName: String,
  val senderRole: UserRole,
  val text: String,
  val timestamp: String,
  val isMe: Boolean = false,
  val attachmentName: String? = null
)

data class SyncLog(
  val id: String,
  val title: String,
  val subtitle: String,
  val time: String,
  val iconType: String // report, group, sync
)

data class InfrastructureStatus(
  val d1Status: String = "Online",
  val d1Latency: String = "9ms",
  val r2Status: String = "Online",
  val r2Assets: String = "1.2 GB",
  val honoVersion: String = "v3.12.0",
  val workerEndpoint: String = "kp-data-sync-backend.workers.dev"
)
