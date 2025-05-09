package com.anasbinrashid.studysync.api

import com.anasbinrashid.studysync.model.Resource
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface LocalResourceApiService {

    @GET("resources.php")
    suspend fun getResourceById(@Query("id") id: String): Response<Resource>

    @GET("resources.php")
    suspend fun getResourcesByUserId(@Query("user_id") userId: String): Response<List<Resource>>

    @POST("resources.php")
    suspend fun createResource(
        @Query("action") action: String = "create",
        @Body resource: Resource
    ): Response<Map<String, Any>>

    @POST("resources.php")
    suspend fun updateResource(
        @Query("action") action: String = "update",
        @Body resource: Resource
    ): Response<Map<String, Any>>

    @POST("resources.php")
    suspend fun deleteResource(
        @Query("action") action: String = "delete",
        @Body resource: Map<String, String>
    ): Response<Map<String, Any>>

    @Multipart
    @POST("upload.php")
    suspend fun uploadFile(
        @Part("resource_id") resourceId: RequestBody,
        @Part file: MultipartBody.Part
    ): Response<Map<String, Any>>

    @GET("download.php")
    suspend fun downloadFile(@Query("file_path") filePath: String): Response<ByteArray>
}