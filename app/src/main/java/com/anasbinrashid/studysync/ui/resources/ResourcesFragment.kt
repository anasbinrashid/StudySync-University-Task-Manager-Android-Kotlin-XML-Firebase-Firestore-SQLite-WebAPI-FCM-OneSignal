package com.anasbinrashid.studysync.ui.resources

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.anasbinrashid.studysync.R
import com.anasbinrashid.studysync.databinding.FragmentResourcesBinding
import com.anasbinrashid.studysync.model.Resource
import com.anasbinrashid.studysync.util.DatabaseHelper
import com.google.android.material.tabs.TabLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID

class ResourcesFragment : Fragment() {

    private var _binding: FragmentResourcesBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var resourcesAdapter: ResourcesAdapter

    private var allResources: List<Resource> = listOf()
    private var filteredResources: List<Resource> = listOf()
    private var currentResourceType: Int = -1 // -1 means All types

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentResourcesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        try {
            // Initialize Firebase
            auth = FirebaseAuth.getInstance()
            db = FirebaseFirestore.getInstance()

            // Initialize local database
            dbHelper = DatabaseHelper(requireContext())

            setupRecyclerView()
            setupTabLayout()
            setupSearch()
            loadResources()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error initializing: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    private fun setupRecyclerView() {
        try {
            binding.rvResources.layoutManager = LinearLayoutManager(requireContext())
            resourcesAdapter = ResourcesAdapter { resource ->
                // Handle resource click - Open resource detail/view
                openResource(resource)
            }
            binding.rvResources.adapter = resourcesAdapter
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error setting up RecyclerView: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun setupTabLayout() {
        try {
            binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) {
                    currentResourceType = when (tab.position) {
                        0 -> -1 // All
                        1 -> 0  // Notes
                        2 -> 1  // Images
                        3 -> 2  // Documents
                        else -> -1
                    }
                    filterResources()
                }

                override fun onTabUnselected(tab: TabLayout.Tab) {}
                override fun onTabReselected(tab: TabLayout.Tab) {}
            })
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error setting up TabLayout: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun setupSearch() {
        try {
            binding.etSearch.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    filterResources()
                }
            })
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error setting up search: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun loadResources() {
        try {
            showLoading(true)

            val currentUser = auth.currentUser
            if (currentUser != null) {
                val userId = currentUser.uid

                // Load resources from local database
                try {
                    allResources = dbHelper.getResourcesForUser(userId)
                } catch (e: Exception) {
                    // If database access fails, use empty list
                    allResources = listOf()
                    Toast.makeText(requireContext(), "Error loading resources: ${e.message}", Toast.LENGTH_SHORT).show()
                    e.printStackTrace()
                }

                filteredResources = allResources

                updateResourcesList()
                showLoading(false)

                // Try to sync with Firestore if network is available
                try {
                    syncResourcesWithFirestore(userId)
                } catch (e: Exception) {
                    // Ignore sync errors, continue with local data
                    e.printStackTrace()
                }
            } else {
                showLoading(false)
                Toast.makeText(requireContext(), "User not authenticated", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            showLoading(false)
            Toast.makeText(requireContext(), "Error loading resources: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun syncResourcesWithFirestore(userId: String) {
        try {
            // Get all unsynced resources from local database
            val unsyncedResources = dbHelper.getUnsyncedResources()

            // Upload unsynced resources to Firestore
            for (resource in unsyncedResources) {
                db.collection("resources")
                    .document(resource.id)
                    .set(resource)
                    .addOnSuccessListener {
                        // Mark resource as synced in local database
                        dbHelper.markResourceAsSynced(resource.id)
                    }
                    .addOnFailureListener { e ->
                        // Log failure but don't bother the user
                        // They can continue working offline
                        e.printStackTrace()
                    }
            }

            // Get latest resources from Firestore
            db.collection("resources")
                .whereEqualTo("userId", userId)
                .get()
                .addOnSuccessListener { documents ->
                    var hasNewResources = false

                    for (document in documents) {
                        val firestoreResource = document.toObject(Resource::class.java)

                        // Check if resource exists in local database
                        val localResource = allResources.find { it.id == firestoreResource.id }

                        if (localResource == null || firestoreResource.lastModified > localResource.lastModified) {
                            // Add or update resource in local database
                            dbHelper.addResource(firestoreResource)
                            hasNewResources = true
                        }
                    }

                    if (hasNewResources) {
                        // Reload resources from local database
                        allResources = dbHelper.getResourcesForUser(userId)
                        filterResources()
                    }
                }
                .addOnFailureListener { e ->
                    // Continue with local data, no need to alert user
                    e.printStackTrace()
                }
        } catch (e: Exception) {
            // Continue with local data, no need to alert user
            e.printStackTrace()
        }
    }

    private fun filterResources() {
        try {
            val searchQuery = binding.etSearch.text.toString().trim().lowercase()

            // First filter by type
            filteredResources = if (currentResourceType == -1) {
                allResources
            } else {
                allResources.filter { it.type == currentResourceType }
            }

            // Then filter by search query
            if (searchQuery.isNotEmpty()) {
                filteredResources = filteredResources.filter { resource ->
                    resource.title.lowercase().contains(searchQuery) ||
                            resource.description.lowercase().contains(searchQuery) ||
                            resource.courseName.lowercase().contains(searchQuery) ||
                            resource.tags.any { it.lowercase().contains(searchQuery) }
                }
            }

            updateResourcesList()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error filtering resources: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun updateResourcesList() {
        try {
            if (filteredResources.isEmpty()) {
                binding.rvResources.visibility = View.GONE
                binding.tvNoResources.visibility = View.VISIBLE
            } else {
                binding.rvResources.visibility = View.VISIBLE
                binding.tvNoResources.visibility = View.GONE
                resourcesAdapter.submitList(filteredResources)
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error updating resource list: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun openResource(resource: Resource) {
        try {
            val action = ResourcesFragmentDirections.actionResourcesFragmentToViewResourceFragment(resource.id)
            findNavController().navigate(action)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error opening resource: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun showLoading(loading: Boolean) {
        try {
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

//package com.anasbinrashid.studysync.ui.resources
//
//import android.os.Bundle
//import android.text.Editable
//import android.text.TextWatcher
//import android.view.LayoutInflater
//import android.view.View
//import android.view.ViewGroup
//import android.widget.Toast
//import androidx.fragment.app.Fragment
//import androidx.recyclerview.widget.LinearLayoutManager
//import com.anasbinrashid.studysync.databinding.FragmentResourcesBinding
//import com.anasbinrashid.studysync.model.Resource
//import com.anasbinrashid.studysync.util.DatabaseHelper
//import com.google.android.material.tabs.TabLayout
//import com.google.firebase.auth.FirebaseAuth
//import com.google.firebase.firestore.FirebaseFirestore
//import java.util.UUID
//
//class ResourcesFragment : Fragment() {
//
//    private var _binding: FragmentResourcesBinding? = null
//    private val binding get() = _binding!!
//
//    private lateinit var auth: FirebaseAuth
//    private lateinit var db: FirebaseFirestore
//    private lateinit var dbHelper: DatabaseHelper
//    private lateinit var resourcesAdapter: ResourcesAdapter
//
//    private var allResources: List<Resource> = listOf()
//    private var filteredResources: List<Resource> = listOf()
//    private var currentResourceType: Int = -1 // -1 means All types
//
//    override fun onCreateView(
//        inflater: LayoutInflater,
//        container: ViewGroup?,
//        savedInstanceState: Bundle?
//    ): View {
//        _binding = FragmentResourcesBinding.inflate(inflater, container, false)
//        return binding.root
//    }
//
//    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
//        super.onViewCreated(view, savedInstanceState)
//
//        // Initialize Firebase
//        auth = FirebaseAuth.getInstance()
//        db = FirebaseFirestore.getInstance()
//
//        // Initialize local database
//        dbHelper = DatabaseHelper(requireContext())
//
//        setupRecyclerView()
//        setupTabLayout()
//        setupSearch()
//        loadResources()
//    }
//
//    private fun setupRecyclerView() {
//        binding.rvResources.layoutManager = LinearLayoutManager(requireContext())
//        resourcesAdapter = ResourcesAdapter { resource ->
//            // Handle resource click - Open resource detail/view
//            openResource(resource)
//        }
//        binding.rvResources.adapter = resourcesAdapter
//    }
//
//    private fun setupTabLayout() {
//        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
//            override fun onTabSelected(tab: TabLayout.Tab) {
//                currentResourceType = when (tab.position) {
//                    0 -> -1 // All
//                    1 -> 0  // Notes
//                    2 -> 1  // Images
//                    3 -> 2  // Documents
//                    else -> -1
//                }
//                filterResources()
//            }
//
//            override fun onTabUnselected(tab: TabLayout.Tab) {}
//            override fun onTabReselected(tab: TabLayout.Tab) {}
//        })
//    }
//
//    private fun setupSearch() {
//        binding.etSearch.addTextChangedListener(object : TextWatcher {
//            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
//            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
//            override fun afterTextChanged(s: Editable?) {
//                filterResources()
//            }
//        })
//    }
//
//    private fun loadResources() {
//        showLoading(true)
//
//        val currentUser = auth.currentUser
//        if (currentUser != null) {
//            val userId = currentUser.uid
//
//            // Load resources from local database
//            allResources = dbHelper.getResourcesForUser(userId)
//            filteredResources = allResources
//
//            updateResourcesList()
//            showLoading(false)
//
//            // Try to sync with Firestore if network is available
//            syncResourcesWithFirestore(userId)
//        } else {
//            showLoading(false)
//            Toast.makeText(requireContext(), "User not authenticated", Toast.LENGTH_SHORT).show()
//        }
//    }
//
//    private fun syncResourcesWithFirestore(userId: String) {
//        // Get all unsynced resources from local database
//        val unsyncedResources = dbHelper.getUnsyncedResources()
//
//        // Upload unsynced resources to Firestore
//        for (resource in unsyncedResources) {
//            db.collection("resources")
//                .document(resource.id)
//                .set(resource)
//                .addOnSuccessListener {
//                    // Mark resource as synced in local database
//                    dbHelper.markResourceAsSynced(resource.id)
//                }
//                .addOnFailureListener { e ->
//                    // Log failure but don't bother the user
//                    // They can continue working offline
//                }
//        }
//
//        // Get latest resources from Firestore
//        db.collection("resources")
//            .whereEqualTo("userId", userId)
//            .get()
//            .addOnSuccessListener { documents ->
//                var hasNewResources = false
//
//                for (document in documents) {
//                    val firestoreResource = document.toObject(Resource::class.java)
//
//                    // Check if resource exists in local database
//                    val localResource = allResources.find { it.id == firestoreResource.id }
//
//                    if (localResource == null || firestoreResource.lastModified > localResource.lastModified) {
//                        // Add or update resource in local database
//                        dbHelper.addResource(firestoreResource)
//                        hasNewResources = true
//                    }
//                }
//
//                if (hasNewResources) {
//                    // Reload resources from local database
//                    allResources = dbHelper.getResourcesForUser(userId)
//                    filterResources()
//                }
//            }
//            .addOnFailureListener { e ->
//                // Continue with local data, no need to alert user
//            }
//    }
//
//    private fun filterResources() {
//        val searchQuery = binding.etSearch.text.toString().trim().lowercase()
//
//        // First filter by type
//        filteredResources = if (currentResourceType == -1) {
//            allResources
//        } else {
//            allResources.filter { it.type == currentResourceType }
//        }
//
//        // Then filter by search query
//        if (searchQuery.isNotEmpty()) {
//            filteredResources = filteredResources.filter { resource ->
//                resource.title.lowercase().contains(searchQuery) ||
//                        resource.description.lowercase().contains(searchQuery) ||
//                        resource.courseName.lowercase().contains(searchQuery) ||
//                        resource.tags.any { it.lowercase().contains(searchQuery) }
//            }
//        }
//
//        updateResourcesList()
//    }
//
//    private fun updateResourcesList() {
//        if (filteredResources.isEmpty()) {
//            binding.rvResources.visibility = View.GONE
//            binding.tvNoResources.visibility = View.VISIBLE
//        } else {
//            binding.rvResources.visibility = View.VISIBLE
//            binding.tvNoResources.visibility = View.GONE
//            resourcesAdapter.submitList(filteredResources)
//        }
//    }
//
//    private fun openResource(resource: Resource) {
//        // Handle resource opening based on type
//        when (resource.type) {
//            0 -> openNote(resource) // Note
//            1 -> openImage(resource) // Image
//            2 -> openDocument(resource) // Document
//            3 -> openLink(resource) // Link
//        }
//    }
//
//    private fun openNote(resource: Resource) {
//        // Open note in a detail fragment or dialog
//        // This will be implemented in a future update
//        Toast.makeText(requireContext(), "Opening note: ${resource.title}", Toast.LENGTH_SHORT).show()
//    }
//
//    private fun openImage(resource: Resource) {
//        // Open image in a viewer
//        // This will be implemented in a future update
//        Toast.makeText(requireContext(), "Opening image: ${resource.title}", Toast.LENGTH_SHORT).show()
//    }
//
//    private fun openDocument(resource: Resource) {
//        // Open document in appropriate viewer
//        // This will be implemented in a future update
//        Toast.makeText(requireContext(), "Opening document: ${resource.title}", Toast.LENGTH_SHORT).show()
//    }
//
//    private fun openLink(resource: Resource) {
//        // Open link in browser
//        // This will be implemented in a future update
//        Toast.makeText(requireContext(), "Opening link: ${resource.title}", Toast.LENGTH_SHORT).show()
//    }
//
//    private fun showLoading(loading: Boolean) {
//        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
//    }
//
//    override fun onDestroyView() {
//        super.onDestroyView()
//        _binding = null
//    }
//}