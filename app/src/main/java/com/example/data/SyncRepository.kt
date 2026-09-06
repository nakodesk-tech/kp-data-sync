package com.example.data

import com.example.model.ChatGroup
import com.example.model.GroupMessage
import com.example.model.SchoolDirectorySeed
import com.example.model.SyncLog
import com.example.model.UserRole
import com.example.model.UserSession

object SyncRepository {

  val demoAccounts = listOf(
    UserSession("user-admin-01", "System App Admin", "admin@kpdatasync.com", UserRole.Admin, clusterName = "All Clusters", schoolName = "All Schools", token = "jwt-admin-token-kp-data-sync-01"),
    UserSession("user-cluster-01", "Rajesh Kumar", "clusterhead@kpdatasync.com", UserRole.Cluster_Head, clusterId = "cluster-01", clusterName = "North Cluster 01", schoolName = "Cluster Assigned Schools", token = "jwt-cluster-token-kp-data-sync-02"),
    UserSession("user-hm-01", "Suresh Mehta (HM)", "schoolhm@kpdatasync.com", UserRole.School_HM, clusterId = "cluster-01", clusterName = "North Cluster 01", schoolId = "school-01", schoolName = "Govt. Model High School North", token = "jwt-hm-token-kp-data-sync-03"),
    UserSession("user-teacher-01", "Priya Sharma (Teacher)", "teacher@kpdatasync.com", UserRole.Teacher, clusterId = "cluster-01", clusterName = "North Cluster 01", schoolId = "school-01", schoolName = "Govt. Model High School North", token = "jwt-teacher-token-kp-data-sync-04")
  )

  val initialGroups = listOf(
    ChatGroup("grp-1", "Group 1", "Hello Sir", "Test Teacher1", time = "12:15 PM", scope = "cluster"),
    ChatGroup("grp-2", "Group 2", "Submit Your Report", "School HM", time = "11:45 AM", scope = "school"),
    ChatGroup("grp-3", "Group 3", "UDISE Data verification deadline tomorrow", "Cluster Head", unreadCount = 2, time = "10:30 AM", scope = "administrative"),
    ChatGroup("grp-4", "Group 4", "Saturday Updates are as below..........", "Test Teacher5", time = "Yesterday", scope = "general")
  )

  // No demo/test schools. The Schools tab reads the live D1 schools table.
  val initialSchools = SchoolDirectorySeed()

  val initialSyncLogs = listOf(
    SyncLog("log-1", "UDISE Report #9012", "Submitted by HM S. Mehta • School_HM", "12:04 PM", "report"),
    SyncLog("log-2", "New Group Created", "'Science_Cluster_B' by Admin", "11:50 AM", "group"),
    SyncLog("log-3", "Android App Heartbeat", "Kotlin SDK v2.4 Handshake", "11:32 AM", "sync")
  )

  fun getMessagesForGroup(groupId: String): List<GroupMessage> = listOf(
    GroupMessage("m1", "Cluster Head", UserRole.Cluster_Head, "Welcome everyone. Please ensure student enrollment spreadsheets are synchronized with Cloudflare R2 before 5:00 PM.", "09:30 AM"),
    GroupMessage("m2", "School HM", UserRole.School_HM, "Noted sir. Our teachers have completed 90% verification on the D1 database.", "10:15 AM"),
    GroupMessage("m3", "Test Teacher1", UserRole.Teacher, "Hello Sir! Just submitted Class 9 Attendance Excel via the R2 portal.", "11:45 AM", attachmentName = "Class9_Attendance_Report.xlsx")
  )
}
