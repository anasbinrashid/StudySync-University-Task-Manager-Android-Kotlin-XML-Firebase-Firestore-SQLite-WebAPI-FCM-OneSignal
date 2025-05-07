package com.anasbinrashid.studysync
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.TextView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.anasbinrashid.studysync.util.DatabaseHelper
import com.anasbinrashid.studysync.databinding.ActivityMainBinding
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var dbHelper: DatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize Firebase Auth and Firestore
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Initialize SQLite helper
        dbHelper = DatabaseHelper(this)

        setupNavigation()
        setupFab()
        loadUserData()
    }

    private fun setupNavigation() {
        // Set up the toolbar
        setSupportActionBar(binding.toolbar)

        // Set up the navigation drawer
        val toggle = ActionBarDrawerToggle(
            this,
            binding.drawerLayout,
            binding.toolbar,
            R.string.app_name,
            R.string.app_name
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        // Set up navigation controller
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // Set up app bar configuration
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.dashboardFragment,
                R.id.tasksFragment,
                R.id.coursesFragment,
                R.id.resourcesFragment,
                R.id.settingsFragment
            ),
            binding.drawerLayout
        )

        // Connect nav controller with app bar and navigation view
        setupActionBarWithNavController(navController, appBarConfiguration)
        binding.navView.setupWithNavController(navController)

        // Set navigation item selected listener
        binding.navView.setNavigationItemSelectedListener(this)

        // Add destination change listener to handle FAB visibility
        navController.addOnDestinationChangedListener { _, destination, _ ->
            updateFabVisibility(destination)
        }
    }

    private fun updateFabVisibility(destination: NavDestination) {
        when (destination.id) {
            R.id.coursesFragment, R.id.tasksFragment, R.id.resourcesFragment -> {
                binding.fabAdd.show()
            }
            else -> {
                binding.fabAdd.hide()
            }
        }
    }

    fun navigateToTab(destinationId: Int) {
        navController.navigate(destinationId)
    }

    private fun setupFab() {
        binding.fabAdd.setOnClickListener {
            // Determine which fragment is currently active and open appropriate dialog
            when (navController.currentDestination?.id) {
                R.id.dashboardFragment -> {
                    // Show add task dialog/fragment
                }
                R.id.tasksFragment -> {
                    // Use the action defined in nav_graph instead of direct navigation
                    navController.navigate(R.id.action_tasksFragment_to_addEditTaskFragment)
                }
                R.id.coursesFragment -> {
                    // Use the action defined in nav_graph instead of direct navigation
                    navController.navigate(R.id.action_coursesFragment_to_addEditCourseFragment)
                }
                R.id.resourcesFragment -> {
                    // Use the action defined in nav_graph instead of direct navigation
                    navController.navigate(R.id.action_resourcesFragment_to_addEditResourceFragment)
                }
            }
        }
    }

    private fun loadUserData() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val uid = currentUser.uid

            val headerView = binding.navView.getHeaderView(0)
            val tvUserName = headerView.findViewById<TextView>(R.id.tv_user_name)
            val tvUserEmail = headerView.findViewById<TextView>(R.id.tv_user_email)
            tvUserEmail.text = currentUser.email

            // Try fetching from Realtime Database first
            val realtimeDbRef = com.google.firebase.database.FirebaseDatabase.getInstance()
                .getReference("users").child(uid)

            realtimeDbRef.get().addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    val name = snapshot.child("name").getValue(String::class.java) ?: "User"
                    tvUserName.text = name
                } else {
                    // If not found in Realtime DB, try Firestore
                    fetchFromFirestore(uid, tvUserName)
                }
            }.addOnFailureListener {
                // If realtime fetch fails (network etc), try Firestore
                fetchFromFirestore(uid, tvUserName)
            }
        }
    }

    private fun fetchFromFirestore(uid: String, tvUserName: TextView) {
        db.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val name = document.getString("name") ?: "User"
                    tvUserName.text = name
                } else {
                    // Fallback to local SQLite
                    fetchFromLocal(uid, tvUserName)
                }
            }
            .addOnFailureListener {
                fetchFromLocal(uid, tvUserName)
            }
    }

    @SuppressLint("SetTextI18n")
    private fun fetchFromLocal(uid: String, tvUserName: TextView) {
        val user = dbHelper.getUser(uid)
        if (user != null) {
            tvUserName.text = user.name
        } else {
            tvUserName.text = "User"
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.dashboardFragment, R.id.settingsFragment -> {
                // Navigate using the Navigation Component
                navController.navigate(item.itemId)
            }
            R.id.tasksFragment, R.id.coursesFragment, R.id.resourcesFragment -> {
                // Navigate using the Navigation Component
                navController.navigate(item.itemId)
            }
            R.id.logout -> {
                // Sign out from Firebase
                auth.signOut()

                // Navigate back to Login Activity
                startActivity(Intent(this, LoginActivity::class.java))
                finish() // Close this activity
            }
        }

        // Close the drawer
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        }
    }
}

//package com.anasbinrashid.studysync
//import android.content.Intent
//import android.os.Bundle
//import android.view.MenuItem
//import android.widget.TextView
//import androidx.appcompat.app.ActionBarDrawerToggle
//import androidx.appcompat.app.AppCompatActivity
//import androidx.core.view.GravityCompat
//import androidx.navigation.NavController
//import androidx.navigation.NavDestination
//import androidx.navigation.fragment.NavHostFragment
//import androidx.navigation.ui.AppBarConfiguration
//import androidx.navigation.ui.navigateUp
//import androidx.navigation.ui.setupActionBarWithNavController
//import androidx.navigation.ui.setupWithNavController
//import com.anasbinrashid.studysync.util.DatabaseHelper
//import com.anasbinrashid.studysync.databinding.ActivityMainBinding
//import com.google.android.material.navigation.NavigationView
//import com.google.firebase.auth.FirebaseAuth
//import com.google.firebase.firestore.FirebaseFirestore
//class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
//
//    private lateinit var binding: ActivityMainBinding
//    private lateinit var navController: NavController
//    private lateinit var appBarConfiguration: AppBarConfiguration
//    private lateinit var auth: FirebaseAuth
//    private lateinit var db: FirebaseFirestore
//    private lateinit var dbHelper: DatabaseHelper
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        binding = ActivityMainBinding.inflate(layoutInflater)
//        setContentView(binding.root)
//
//        // Initialize Firebase Auth and Firestore
//        auth = FirebaseAuth.getInstance()
//        db = FirebaseFirestore.getInstance()
//
//        // Initialize SQLite helper
//        dbHelper = DatabaseHelper(this)
//
//        setupNavigation()
//        setupFab()
//        loadUserData()
//    }
//
//    private fun setupNavigation() {
//        // Set up the toolbar
//        setSupportActionBar(binding.toolbar)
//
//        // Set up the navigation drawer
//        val toggle = ActionBarDrawerToggle(
//            this,
//            binding.drawerLayout,
//            binding.toolbar,
//            R.string.app_name,
//            R.string.app_name
//        )
//        binding.drawerLayout.addDrawerListener(toggle)
//        toggle.syncState()
//
//        // Set up navigation controller
//        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
//        navController = navHostFragment.navController
//
//        // Set up app bar configuration
//        appBarConfiguration = AppBarConfiguration(
//            setOf(
//                R.id.dashboardFragment,
//                R.id.tasksFragment,
//                R.id.coursesFragment,
//                R.id.resourcesFragment,
//                R.id.settingsFragment
//            ),
//            binding.drawerLayout
//        )
//
//        // Connect nav controller with app bar and navigation view
//        setupActionBarWithNavController(navController, appBarConfiguration)
//        binding.navView.setupWithNavController(navController)
//
//        // Set navigation item selected listener
//        binding.navView.setNavigationItemSelectedListener(this)
//
//        // Add destination change listener to handle FAB visibility
//        navController.addOnDestinationChangedListener { _, destination, _ ->
//            updateFabVisibility(destination)
//        }
//    }
//
//    private fun updateFabVisibility(destination: NavDestination) {
//        when (destination.id) {
//            R.id.coursesFragment, R.id.tasksFragment, R.id.resourcesFragment -> {
//                binding.fabAdd.show()
//            }
//            else -> {
//                binding.fabAdd.hide()
//            }
//        }
//    }
//
//    fun navigateToTab(destinationId: Int) {
//        navController.navigate(destinationId)
//    }
//
//    private fun setupFab() {
//        binding.fabAdd.setOnClickListener {
//            // Determine which fragment is currently active and open appropriate dialog
//            when (navController.currentDestination?.id) {
//                R.id.dashboardFragment -> {
//                    // Show add task dialog/fragment
//                }
//                R.id.tasksFragment -> {
//                    // Use the action defined in nav_graph instead of direct navigation
//                    navController.navigate(R.id.action_tasksFragment_to_addEditTaskFragment)
//                }
//                R.id.coursesFragment -> {
//                    // Use the action defined in nav_graph instead of direct navigation
//                    navController.navigate(R.id.action_coursesFragment_to_addEditCourseFragment)
//                }
//                R.id.resourcesFragment -> {
//                    // Use the action defined in nav_graph instead of direct navigation
//                    navController.navigate(R.id.action_resourcesFragment_to_addEditResourceFragment)
//                }
//            }
//        }
//    }
//
//    private fun loadUserData() {
//        val currentUser = auth.currentUser
//        if (currentUser != null) {
//            val uid = currentUser.uid
//
//            val headerView = binding.navView.getHeaderView(0)
//            val tvUserName = headerView.findViewById<TextView>(R.id.tv_user_name)
//            val tvUserEmail = headerView.findViewById<TextView>(R.id.tv_user_email)
//            tvUserEmail.text = currentUser.email
//
//            // Try fetching from Realtime Database first
//            val realtimeDbRef = com.google.firebase.database.FirebaseDatabase.getInstance()
//                .getReference("users").child(uid)
//
//            realtimeDbRef.get().addOnSuccessListener { snapshot ->
//                if (snapshot.exists()) {
//                    val name = snapshot.child("name").getValue(String::class.java) ?: "User"
//                    tvUserName.text = name
//                } else {
//                    // If not found in Realtime DB, try Firestore
//                    fetchFromFirestore(uid, tvUserName)
//                }
//            }.addOnFailureListener {
//                // If realtime fetch fails (network etc), try Firestore
//                fetchFromFirestore(uid, tvUserName)
//            }
//        }
//    }
//
//    private fun fetchFromFirestore(uid: String, tvUserName: TextView) {
//        db.collection("users").document(uid).get()
//            .addOnSuccessListener { document ->
//                if (document != null && document.exists()) {
//                    val name = document.getString("name") ?: "User"
//                    tvUserName.text = name
//                } else {
//                    // Fallback to local SQLite
//                    fetchFromLocal(uid, tvUserName)
//                }
//            }
//            .addOnFailureListener {
//                fetchFromLocal(uid, tvUserName)
//            }
//    }
//
//    private fun fetchFromLocal(uid: String, tvUserName: TextView) {
//        val user = dbHelper.getUser(uid)
//        if (user != null) {
//            tvUserName.text = user.name
//        } else {
//            tvUserName.text = "User"
//        }
//    }
//
//    override fun onNavigationItemSelected(item: MenuItem): Boolean {
//        when (item.itemId) {
//            R.id.dashboardFragment, R.id.settingsFragment -> {
//                // Navigate using the Navigation Component
//                navController.navigate(item.itemId)
//            }
//            R.id.tasksFragment, R.id.coursesFragment, R.id.resourcesFragment -> {
//                // Navigate using the Navigation Component
//                navController.navigate(item.itemId)
//            }
//            R.id.logout -> {
//                // Sign out from Firebase
//                auth.signOut()
//
//                // Navigate back to Login Activity
//                startActivity(Intent(this, LoginActivity::class.java))
//                finish() // Close this activity
//            }
//        }
//
//        // Close the drawer
//        binding.drawerLayout.closeDrawer(GravityCompat.START)
//        return true
//    }
//
//    override fun onSupportNavigateUp(): Boolean {
//        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
//    }
//
//    @Deprecated("This method has been deprecated in favor of using the\n      {@link OnBackPressedDispatcher} via {@link #getOnBackPressedDispatcher()}.\n      The OnBackPressedDispatcher controls how back button events are dispatched\n      to one or more {@link OnBackPressedCallback} objects.")
//    override fun onBackPressed() {
//        super.onBackPressed()
//        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
//            binding.drawerLayout.closeDrawer(GravityCompat.START)
//        }
//    }
//}