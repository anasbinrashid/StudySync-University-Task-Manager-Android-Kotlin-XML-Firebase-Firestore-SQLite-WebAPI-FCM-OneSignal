package com.anasbinrashid.studysync.repository

import android.content.Context
import android.util.Log
import com.anasbinrashid.studysync.api.LocalResourceApiService
import com.anasbinrashid.studysync.api.RetrofitClient
import com.anasbinrashid.studysync.model.Resource
import com.anasbinrashid.studysync.util.DatabaseHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class ResourceRepository(private val context: Context) {
    private val TAG = "ResourceRepository"
    private val localResourceApiService: LocalResourceApiService = RetrofitClient.localResourceApiService
    private val dbHelper = DatabaseHelper(context)

    init {
        Log.d(TAG, "ResourceRepository initialized")
    }

    suspend fun getResourceById(id: String): Resource? {
        return try {
            Log.d(TAG, "Getting resource by ID: $id")
            val response = localResourceApiService.getResourceById(id)

            if (response.isSuccessful) {
                response.body()?.let {
                    Log.d(TAG, "Resource found: $it")
                    it
                }
            } else {
                Log.e(TAG, "Failed to get resource. Error: ${response.errorBody()?.string()}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting resource by ID", e)
            null
        }
    }

    suspend fun getResourcesByUserId(userId: String): List<Resource> {
        return try {
            Log.d(TAG, "Getting resources for user: $userId")
            val response = localResourceApiService.getResourcesByUserId(userId)

            if (response.isSuccessful) {
                response.body()?.let {
                    Log.d(TAG, "Found ${it.size} resources")
                    it
                } ?: emptyList()
            } else {
                Log.e(TAG, "Failed to get resources. Error: ${response.errorBody()?.string()}")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting resources by user ID", e)
            emptyList()
        }
    }

    suspend fun createResource(resource: Resource): Result<Resource> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Creating resource: $resource")

            // First try to save to local database
            val localResult = dbHelper.addResource(resource)

            if (localResult > 0) {
                Log.d(TAG, "Resource saved to local database successfully")

                // Then try to save to API
                Log.d(TAG, "Attempting to save to API...")
                val response = localResourceApiService.createResource(action = "create", resource = resource)

                Log.d(TAG, "API Response code: ${response.code()}")
                Log.d(TAG, "API Response body: ${response.body()}")
                Log.d(TAG, "API Error body: ${response.errorBody()?.string()}")

                if (response.isSuccessful) {
                    response.body()?.let { responseMap ->
                        val success = responseMap["success"] as? Boolean ?: false
                        if (success) {
                            Log.d(TAG, "Resource created successfully in API")
                            // Mark as synced in local database
                            dbHelper.markResourceAsSynced(resource.id)
                            Result.success(resource)
                        } else {
                            val message = responseMap["message"] as? String ?: "Unknown error"
                            Log.e(TAG, "API error: $message")
                            Result.failure(Exception("API error: $message"))
                        }
                    } ?: run {
                        Log.e(TAG, "API response body is null")
                        Result.failure(Exception("API response body is null"))
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e(TAG, "Failed to create resource in API. Error: $errorBody")
                    // Return the locally saved resource since API call failed
                    Result.success(resource)
                }
            } else {
                Log.e(TAG, "Failed to save resource to local database")
                Result.failure(Exception("Failed to save resource to local database"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating resource", e)
            Result.failure(e)
        }
    }

    suspend fun updateResource(resource: Resource): Result<Resource> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Updating resource: $resource")
            val response = localResourceApiService.updateResource(action = "update", resource = resource)

            if (response.isSuccessful) {
                response.body()?.let { responseMap ->
                    val success = responseMap["success"] as? Boolean ?: false
                    if (success) {
                        Log.d(TAG, "Resource updated successfully")
                        Result.success(resource)
                    } else {
                        val message = responseMap["message"] as? String ?: "Unknown error"
                        Log.e(TAG, "API error: $message")
                        Result.failure(Exception("API error: $message"))
                    }
                } ?: run {
                    Log.e(TAG, "Response body is null")
                    Result.failure(Exception("Response body is null"))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "Failed to update resource. Error: $errorBody")
                Result.failure(Exception("Failed to update resource: $errorBody"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating resource", e)
            Result.failure(e)
        }
    }

    suspend fun deleteResource(resource: Resource): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Deleting resource: $resource")
            val deleteParams = mapOf("id" to resource.id)
            val response = localResourceApiService.deleteResource(action = "delete", deleteParams)

            Log.d(TAG, "Delete API Response code: ${response.code()}")
            Log.d(TAG, "Delete API Response body: ${response.body()}")
            Log.d(TAG, "Delete API Error body: ${response.errorBody()?.string()}")

            if (response.isSuccessful) {
                response.body()?.let { responseMap ->
                    val success = responseMap["success"] as? Boolean ?: false
                    if (success) {
                        Log.d(TAG, "Resource deleted successfully from API")
                        Result.success(Unit)
                    } else {
                        val message = responseMap["message"] as? String ?: "Unknown error"
                        Log.e(TAG, "API error: $message")
                        Result.failure(Exception("API error: $message"))
                    }
                } ?: run {
                    Log.e(TAG, "Delete response body is null")
                    Result.failure(Exception("Delete response body is null"))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "Failed to delete resource. Error: $errorBody")
                Result.failure(Exception("Failed to delete resource: $errorBody"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting resource", e)
            Result.failure(e)
        }
    }

    suspend fun uploadFile(resourceId: String, file: File): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Uploading file for resource $resourceId: ${file.absolutePath}")

            val requestFile = file.asRequestBody("application/octet-stream".toMediaTypeOrNull())
            val resourceIdBody = resourceId.toRequestBody("text/plain".toMediaTypeOrNull())
            val filePart = MultipartBody.Part.createFormData("file", file.name, requestFile)

            val response = localResourceApiService.uploadFile(resourceIdBody, filePart)

            if (response.isSuccessful) {
                Log.d(TAG, "File uploaded successfully")
                Result.success(Unit)
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "Failed to upload file. Error: $errorBody")
                Result.failure(Exception("Failed to upload file: $errorBody"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading file", e)
            Result.failure(e)
        }
    }

    suspend fun downloadFile(filePath: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Downloading file: $filePath")
            val response = localResourceApiService.downloadFile(filePath)

            if (response.isSuccessful) {
                response.body()?.let {
                    Log.d(TAG, "File downloaded successfully")
                    Result.success(it)
                } ?: run {
                    Log.e(TAG, "Response body is null")
                    Result.failure(Exception("Response body is null"))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "Failed to download file. Error: $errorBody")
                Result.failure(Exception("Failed to download file: $errorBody"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading file", e)
            Result.failure(e)
        }
    }
}