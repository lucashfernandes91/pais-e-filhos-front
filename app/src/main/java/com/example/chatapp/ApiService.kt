package com.example.chatapp

import retrofit2.http.*

interface ApiService {

    @GET("api/messages/{id}/")
    suspend fun getMessages(
        @Header("Authorization") token: String,
        @Path("id") conversationId: Int,
        @Query("before") before: Int? = null,
        @Query("limit") limit: Int? = null
    ): List<Message>

    @POST("api/send-message/")
    suspend fun sendMessage(
        @Header("Authorization") token: String,
        @Body body: Map<String, Any>
    )

    @GET("api/messages/{messageId}/detail/")
    suspend fun getMessageDetail(
        @Header("Authorization") token: String,
        @Path("messageId") messageId: Int
    ): Map<String, Any>

    @POST("api/messages/{messageId}/read/")
    suspend fun markMessageAsRead(
        @Header("Authorization") token: String,
        @Path("messageId") messageId: Int
    ): Map<String, Any>

    @POST("api/events/")
    suspend fun createEvent(
        @Header("Authorization") token: String,
        @Body body: CreateEventRequest
    ): Event

    @GET("api/events/{conversationId}/")
    suspend fun getEvents(
        @Header("Authorization") token: String,
        @Path("conversationId") conversationId: Int
    ): List<Event>

    @DELETE("api/events/{eventId}/delete/")
    suspend fun deleteEvent(
        @Header("Authorization") token: String,
        @Path("eventId") eventId: Int
    ): Map<String, Any>

    @PUT("api/events/{eventId}/update/")
    suspend fun updateEvent(
        @Header("Authorization") token: String,
        @Path("eventId") eventId: Int,
        @Body body: Map<String, Any?>
    ): Event

    @POST("api/device-token/")
    suspend fun registerDeviceToken(
        @Header("Authorization") token: String,
        @Body body: Map<String, String>
    ): Map<String, Any>

    // Notifications
    @GET("api/notifications/")
    suspend fun getNotifications(
        @Header("Authorization") token: String
    ): List<ApiNotification>

    @POST("api/notifications/mark-all-read/")
    suspend fun markAllNotificationsRead(
        @Header("Authorization") token: String
    ): Map<String, Any>

    @DELETE("api/notifications/delete-all/")
    suspend fun deleteAllNotifications(
        @Header("Authorization") token: String
    ): Map<String, Any>

    @POST("api/notifications/{notificationId}/read/")
    suspend fun markNotificationRead(
        @Header("Authorization") token: String,
        @Path("notificationId") notificationId: Int
    ): Map<String, Any>

    @GET("api/notifications/unread-count/")
    suspend fun getUnreadNotificationsCount(
        @Header("Authorization") token: String
    ): Map<String, Any>

    @GET("api/export/pdf/{conversationId}/")
    suspend fun exportConversationPdf(
        @Header("Authorization") token: String,
        @Path("conversationId") conversationId: Int,
        @Query("type") type: String
    ): okhttp3.ResponseBody

    @POST("api/token/")
    suspend fun obtainToken(
        @Body body: Map<String, String>
    ): Map<String, Any>

    @POST("api/token/refresh/")
    suspend fun refreshToken(
        @Body body: Map<String, String>
    ): Map<String, Any>

    @POST("api/register/")
    suspend fun registerUser(
        @Body body: Map<String, String>
    ): Map<String, Any>

    @POST("api/logout/")
    suspend fun logout(
        @Header("Authorization") token: String,
        @Body body: Map<String, String>
    ): Map<String, Any>

    @POST("api/password-reset/request/")
    suspend fun requestPasswordReset(
        @Body body: Map<String, String>
    ): Map<String, Any>

    @POST("api/password-reset/verify/")
    suspend fun verifyPasswordResetCode(
        @Body body: Map<String, String>
    ): Map<String, Any>

    @POST("api/password-reset/confirm/")
    suspend fun confirmPasswordReset(
        @Body body: Map<String, String>
    ): Map<String, Any>

    @GET("api/profile/")
    suspend fun getProfile(
        @Header("Authorization") token: String
    ): Map<String, Any>

    @POST("api/email/verify/")
    suspend fun verifyEmail(
        @Header("Authorization") token: String,
        @Body body: Map<String, String>
    ): Map<String, Any>

    @POST("api/email/verify/resend/")
    suspend fun resendEmailVerification(
        @Header("Authorization") token: String
    ): Map<String, Any>

    @PUT("api/profile/")
    suspend fun updateProfile(
        @Header("Authorization") token: String,
        @Body body: Map<String, String>
    ): Map<String, Any>

    // Conversations
    @GET("api/conversations/")
    suspend fun getConversations(
        @Header("Authorization") token: String
    ): List<ConversationDetail>

    // Invites
    @POST("api/invites/")
    suspend fun createInvite(
        @Header("Authorization") token: String,
        @Body body: Map<String, Int>
    ): Map<String, Any>

    @POST("api/invites/accept/")
    suspend fun acceptInvite(
        @Header("Authorization") token: String,
        @Body body: Map<String, String>
    ): Map<String, Any>

    // Children
    @GET("api/children/{conversationId}/")
    suspend fun getChildren(
        @Header("Authorization") token: String,
        @Path("conversationId") conversationId: Int
    ): List<Child>

    @POST("api/children/")
    suspend fun createChild(
        @Header("Authorization") token: String,
        @Body body: CreateChildRequest
    ): Child

    @PATCH("api/children/{childId}/update/")
    suspend fun updateChild(
        @Header("Authorization") token: String,
        @Path("childId") childId: Int,
        @Body body: UpdateChildRequest
    ): Child

    @Multipart
    @PATCH("api/children/{childId}/update/")
    suspend fun updateChildWithPhoto(
        @Header("Authorization") token: String,
        @Path("childId") childId: Int,
        @Part("name") name: okhttp3.RequestBody,
        @Part("birth_date") birthDate: okhttp3.RequestBody,
        @Part("cpf") cpf: okhttp3.RequestBody,
        @Part("rg") rg: okhttp3.RequestBody,
        @Part("has_custody") hasCustody: okhttp3.RequestBody,
        @Part photo: okhttp3.MultipartBody.Part?
    ): Child

    @Multipart
    @PATCH("api/children/{childId}/update/")
    suspend fun updateChildPhoto(
        @Header("Authorization") token: String,
        @Path("childId") childId: Int,
        @Part photo: okhttp3.MultipartBody.Part
    ): Child

    @DELETE("api/children/{childId}/delete/")
    suspend fun deleteChild(
        @Header("Authorization") token: String,
        @Path("childId") childId: Int
    ): Map<String, Any>

    // Attachments
    @Multipart
    @POST("api/send-message-attachment/")
    suspend fun sendMessageWithAttachment(
        @Header("Authorization") token: String,
        @Part("conversation_id") conversationId: okhttp3.RequestBody,
        @Part("content") content: okhttp3.RequestBody,
        @Part attachment: okhttp3.MultipartBody.Part?
    ): Message
}

/**
 * API response model for notifications from backend.
 * Maps directly to Django's NotificationSerializer fields.
 */
data class ApiNotification(
    val id: Int,
    val title: String,
    val body: String,
    val sent_at: String,
    val read_at: String?
)
