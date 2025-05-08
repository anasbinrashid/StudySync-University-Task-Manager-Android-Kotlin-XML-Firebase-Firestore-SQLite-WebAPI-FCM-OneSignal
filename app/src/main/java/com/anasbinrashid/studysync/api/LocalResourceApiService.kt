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

    @POST("resources.php?action=create")
    suspend fun createResource(@Body resource: Resource): Response<Resource>

    @POST("resources.php?action=update")
    suspend fun updateResource(@Body resource: Resource): Response<Resource>

    @POST("resources.php?action=delete")
    suspend fun deleteResource(@Body resource: Resource): Response<Unit>

    @Multipart
    @POST("upload.php")
    suspend fun uploadFile(
        @Part("resource_id") resourceId: RequestBody,
        @Part file: MultipartBody.Part
    ): Response<Unit>

    @GET("download.php")
    suspend fun downloadFile(@Query("file_path") filePath: String): Response<ByteArray>
}