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
                val response = localResourceApiService.createResource(resource)
                
                if (response.isSuccessful) {
                    response.body()?.let { savedResource ->
                        Log.d(TAG, "Resource created successfully in API: $savedResource")
                        // Mark as synced in local database
                        dbHelper.markResourceAsSynced(savedResource.id)
                        Result.success(savedResource)
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
            val response = localResourceApiService.updateResource(resource)
            
            if (response.isSuccessful) {
                response.body()?.let {
                    Log.d(TAG, "Resource updated successfully: $it")
                    Result.success(it)
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
            val response = localResourceApiService.deleteResource(resource)
            
            if (response.isSuccessful) {
                Log.d(TAG, "Resource deleted successfully")
                Result.success(Unit)
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