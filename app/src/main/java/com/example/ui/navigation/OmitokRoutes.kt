package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
object WelcomeRoute

@Serializable
object LoginRoute

@Serializable
object RegisterRoute

@Serializable
data class FeedRoute(val initialVideoId: String? = null)

@Serializable
object SearchRoute

@Serializable
object UploadRoute

@Serializable
data class ProfileRoute(val username: String? = null)

@Serializable
object SettingsRoute
