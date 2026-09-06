package com.example.data

import com.example.model.ChatGroup
import com.example.model.GroupMessage
import com.example.model.SchoolItem
import com.example.model.SyncLog
import com.example.model.UserRole
import com.example.model.UserSession

object SyncRepository {

  val demoAccounts = listOf(
    UserSession(
      id = "user-admin-01",
      name = "System App Admin",
      email = "admin@kpdatasync.com",
      role = UserRole.Admin,
      clusterId = null,
      clusterName = "All Clusters",
      schoolId = null,
      schoolName = "All Schools",
      token = "jwt-admin-token-kp-data-sync-01"
    ),
    UserSession(
      id = "user-cluster-01",
      name = "Rajesh Kumar",
      email = "clusterhead@kpdatasync.com",
      role = UserRole.Cluster_Head,
      clusterId = "cluster-01",
      clusterName = "North Cluster 01",
      schoolId = null,
      schoolName = "Cluster Assigned Schools",
      token = "jwt-cluster-token-kp-data-sync-02"
    ),
    UserSession(
      id = "user-hm-01",
      name = "Suresh Mehta (HM)",
      email = "schoolhm@kpdatasync.com",
      role = UserRole.School_HM,
      clusterId = "cluster-01",
      clusterName = "North Cluster 01",
      schoolId = "school-01",
      schoolName = "Govt. Model High School North",
      token = "jwt-hm-token-kp-data-sync-03"
    ),
    UserSession(
      id = "user-teacher-01",
      name = "Priya Sharma (Teacher)",
      email = "teacher@kpdatasync.com",
      role = UserRole.Teacher,
      clusterId = "cluster-01",
      clusterName = "North Cluster 01",
      schoolId = "school-01",
      schoolName = "Govt. Model High School North",
      token = "jwt-teacher-token-kp-data-sync-04"
    )
  )

  val initialGroups = listOf(
    ChatGroup(
      id = "grp-1",
      name = "Group 1",
      lastMessage = "Hello Sir",
      senderName = "Test Teacher1",
      unreadCount = 0,
      time = "12:15 PM",
      scope = "cluster"
    ),
    ChatGroup(
      id = "grp-2",
      name = "Group 2",
      lastMessage = "Submit Your Report",
      senderName = "School HM",
      unreadCount = 0,
      time = "11:45 AM",
      scope = "school"
    ),
    ChatGroup(
      id = "grp-3",
      name = "Group 3",
      lastMessage = "UDISE Data verification deadline tomorrow",
      senderName = "Cluster Head",
      unreadCount = 2,
      time = "10:30 AM",
      scope = "administrative"
    ),
    ChatGroup(
      id = "grp-4",
      name = "Group 4",
      lastMessage = "Saturday Updates are as below..........",
      senderName = "Test Teacher5",
      unreadCount = 0,
      time = "Yesterday",
      scope = "general"
    )
  )

  val initialSchools = listOf(
    SchoolItem(
      id = "sch-1",
      name = "Government Model High School North",
      udiseCode = "27010100101",
      clusterName = "North Cluster 01",
      teacherCount = 18,
      studentCount = 420,
      lastSyncTime = "10 min ago"
    ),
    SchoolItem(
      id = "sch-2",
      name = "Government Primary School South",
      udiseCode = "27010200202",
      clusterName = "South Cluster 02",
      teacherCount = 9,
      studentCount = 195,
      lastSyncTime = "35 min ago"
    ),
    SchoolItem(
      id = "sch-3",
      name = "Central Secondary Vidyalaya",
      udiseCode = "27010300303",
      clusterName = "North Cluster 01",
      teacherCount = 24,
      studentCount = 580,
      lastSyncTime = "1 hour ago"
    ),
    SchoolItem(
      id = "sch-4",
      name = "East Municipal Girls High School",
      udiseCode = "27010400404",
      clusterName = "East Cluster 03",
      teacherCount = 14,
      studentCount = 310,
      lastSyncTime = "3 hours ago"
    )
  )

  val initialSyncLogs = listOf(
    SyncLog(
      id = "log-1",
      title = "UDISE Report #9012",
      subtitle = "Submitted by HM S. Mehta • School_HM",
      time = "12:04 PM",
      iconType = "report"
    ),
    SyncLog(
      id = "log-2",
      title = "New Group Created",
      subtitle = "'Science_Cluster_B' by Admin",
      time = "11:50 AM",
      iconType = "group"
    ),
    SyncLog(
      id = "log-3",
      title = "Android App Heartbeat",
      subtitle = "Kotlin SDK v2.4 Handshake",
      time = "11:32 AM",
      iconType = "sync"
    )
  )

  fun getMessagesForGroup(groupId: String): List<GroupMessage> {
    return listOf(
      GroupMessage(
        id = "m1",
        senderName = "Cluster Head",
        senderRole = UserRole.Cluster_Head,
        text = "Welcome everyone. Please ensure student enrollment spreadsheets are synchronized with Cloudflare R2 before 5:00 PM.",
        timestamp = "09:30 AM"
      ),
      GroupMessage(
        id = "m2",
        senderName = "School HM",
        senderRole = UserRole.School_HM,
        text = "Noted sir. Our teachers have completed 90% verification on the D1 database.",
        timestamp = "10:15 AM"
      ),
      GroupMessage(
        id = "m3",
        senderName = "Test Teacher1",
        senderRole = UserRole.Teacher,
        text = "Hello Sir! Just submitted Class 9 Attendance Excel via the R2 portal.",
        timestamp = "11:45 AM",
        attachmentName = "Class9_Attendance_Report.xlsx"
      )
    )
  }
}
