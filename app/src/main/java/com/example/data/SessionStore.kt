package com.example.data

import android.content.Context
import com.example.model.UserRole
import com.example.model.UserSession

/** Persists the authenticated session so the user only needs to log in again after logout. */
object SessionStore {
  private const val PREFS = "kp_data_sync_session"
  private const val KEY_ID = "id"
  private const val KEY_NAME = "name"
  private const val KEY_EMAIL = "email"
  private const val KEY_ROLE = "role"
  private const val KEY_CLUSTER_ID = "cluster_id"
  private const val KEY_CLUSTER_NAME = "cluster_name"
  private const val KEY_CLUSTER_CODE = "cluster_code"
  private const val KEY_SCHOOL_ID = "school_id"
  private const val KEY_SCHOOL_NAME = "school_name"
  private const val KEY_SCHOOL_CODE = "school_code"
  private const val KEY_TOKEN = "token"
  private const val KEY_STATUS = "status"

  fun save(context: Context, session: UserSession) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
      .putString(KEY_ID, session.id)
      .putString(KEY_NAME, session.name)
      .putString(KEY_EMAIL, session.email)
      .putString(KEY_ROLE, session.role.roleName)
      .putString(KEY_CLUSTER_ID, session.clusterId)
      .putString(KEY_CLUSTER_NAME, session.clusterName)
      .putString(KEY_CLUSTER_CODE, session.clusterCode)
      .putString(KEY_SCHOOL_ID, session.schoolId)
      .putString(KEY_SCHOOL_NAME, session.schoolName)
      .putString(KEY_SCHOOL_CODE, session.schoolCode)
      .putString(KEY_TOKEN, session.token)
      .putString(KEY_STATUS, session.status)
      .apply()
  }

  fun load(context: Context): UserSession? {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val id = prefs.getString(KEY_ID, null) ?: return null
    val name = prefs.getString(KEY_NAME, null) ?: return null
    val email = prefs.getString(KEY_EMAIL, null) ?: return null
    val roleName = prefs.getString(KEY_ROLE, null) ?: return null
    val token = prefs.getString(KEY_TOKEN, null) ?: return null
    val role = UserRole.values().firstOrNull { it.roleName == roleName } ?: return null

    return UserSession(
      id = id,
      name = name,
      email = email,
      role = role,
      clusterId = prefs.getString(KEY_CLUSTER_ID, null),
      clusterName = prefs.getString(KEY_CLUSTER_NAME, null),
      clusterCode = prefs.getString(KEY_CLUSTER_CODE, null),
      schoolId = prefs.getString(KEY_SCHOOL_ID, null),
      schoolName = prefs.getString(KEY_SCHOOL_NAME, null),
      schoolCode = prefs.getString(KEY_SCHOOL_CODE, null),
      token = token,
      status = prefs.getString(KEY_STATUS, "active") ?: "active"
    )
  }

  fun clear(context: Context) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
  }
}
