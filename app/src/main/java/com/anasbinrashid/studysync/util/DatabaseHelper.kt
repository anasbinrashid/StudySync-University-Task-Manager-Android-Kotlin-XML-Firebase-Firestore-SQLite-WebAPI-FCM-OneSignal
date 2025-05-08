package com.anasbinrashid.studysync.util

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.anasbinrashid.studysync.model.Course
import com.anasbinrashid.studysync.model.Resource
import com.anasbinrashid.studysync.model.Task
import com.anasbinrashid.studysync.model.User
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "studysync.db"
        private const val DATABASE_VERSION = 1

        // Table names
        private const val TABLE_USERS = "users"
        private const val TABLE_TASKS = "tasks"
        private const val TABLE_COURSES = "courses"
        private const val TABLE_RESOURCES = "resources"

        // Common column names
        private const val KEY_ID = "id"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_IS_SYNCED = "is_synced"
        private const val KEY_LAST_MODIFIED = "last_modified"

        // User table columns
        private const val KEY_NAME = "name"
        private const val KEY_EMAIL = "email"
        private const val KEY_UNIVERSITY = "university"
        private const val KEY_CURRENT_SEMESTER = "current_semester"

        // Task table columns
        private const val KEY_TITLE = "title"
        private const val KEY_DESCRIPTION = "description"
        private const val KEY_COURSE_ID = "course_id"
        private const val KEY_COURSE_NAME = "course_name"
        private const val KEY_DUE_DATE = "due_date"
        private const val KEY_PRIORITY = "priority"
        private const val KEY_STATUS = "status"
        private const val KEY_TYPE = "type"
        private const val KEY_REMINDER_SET = "reminder_set"
        private const val KEY_GRADE = "grade"
        private const val KEY_LAST_UPDATED = "last_updated"

        // Course table columns
        private const val KEY_CODE = "code"
        private const val KEY_INSTRUCTOR_NAME = "instructor_name"
        private const val KEY_INSTRUCTOR_EMAIL = "instructor_email"
        private const val KEY_ROOM = "room"
        private const val KEY_DAY_OF_WEEK = "day_of_week"
        private const val KEY_START_TIME = "start_time"
        private const val KEY_END_TIME = "end_time"
        private const val KEY_SEMESTER = "semester"
        private const val KEY_CREDIT_HOURS = "credit_hours"
        private const val KEY_COLOR = "color"

        // Resource table columns
        private const val KEY_FILE_PATH = "file_path"
        private const val KEY_TAGS = "tags"
        private const val KEY_DATE_ADDED = "date_added"
        private const val KEY_THUMBNAIL_PATH = "thumbnail_path"
    }

    override fun onCreate(db: SQLiteDatabase) {
        // Create Users table
        val createUsersTable = """
            CREATE TABLE $TABLE_USERS (
                $KEY_ID TEXT PRIMARY KEY,
                $KEY_NAME TEXT,
                $KEY_EMAIL TEXT,
                $KEY_UNIVERSITY TEXT,
                $KEY_CURRENT_SEMESTER TEXT
            )
        """.trimIndent()

        // Create Tasks table
        val createTasksTable = """
            CREATE TABLE $TABLE_TASKS (
                $KEY_ID TEXT PRIMARY KEY,
                $KEY_USER_ID TEXT NOT NULL,
                $KEY_TITLE TEXT NOT NULL,
                $KEY_DESCRIPTION TEXT,
                $KEY_COURSE_ID TEXT,
                $KEY_COURSE_NAME TEXT,
                $KEY_DUE_DATE INTEGER,
                $KEY_PRIORITY INTEGER DEFAULT 0,
                $KEY_STATUS INTEGER DEFAULT 0,
                $KEY_TYPE INTEGER DEFAULT 0,
                $KEY_REMINDER_SET INTEGER DEFAULT 0,
                $KEY_GRADE REAL DEFAULT 0,
                $KEY_IS_SYNCED INTEGER DEFAULT 0,
                $KEY_LAST_UPDATED INTEGER NOT NULL
            )
        """.trimIndent()

        // Create Courses table
        val createCoursesTable = """
            CREATE TABLE $TABLE_COURSES (
                $KEY_ID TEXT PRIMARY KEY,
                $KEY_USER_ID TEXT NOT NULL,
                $KEY_NAME TEXT NOT NULL,
                $KEY_CODE TEXT,
                $KEY_INSTRUCTOR_NAME TEXT,
                $KEY_INSTRUCTOR_EMAIL TEXT,
                $KEY_ROOM TEXT,
                $KEY_DAY_OF_WEEK TEXT,
                $KEY_START_TIME TEXT,
                $KEY_END_TIME TEXT,
                $KEY_SEMESTER TEXT,
                $KEY_CREDIT_HOURS INTEGER DEFAULT 0,
                $KEY_COLOR INTEGER DEFAULT 0,
                $KEY_IS_SYNCED INTEGER DEFAULT 0,
                $KEY_LAST_MODIFIED INTEGER NOT NULL
            )
        """.trimIndent()

        // Create Resources table
        val createResourcesTable = """
            CREATE TABLE $TABLE_RESOURCES (
                $KEY_ID TEXT PRIMARY KEY,
                $KEY_USER_ID TEXT NOT NULL,
                $KEY_TITLE TEXT NOT NULL,
                $KEY_DESCRIPTION TEXT,
                $KEY_COURSE_ID TEXT,
                $KEY_COURSE_NAME TEXT,
                $KEY_TYPE INTEGER DEFAULT 0,
                $KEY_FILE_PATH TEXT,
                $KEY_TAGS TEXT,
                $KEY_DATE_ADDED INTEGER NOT NULL,
                $KEY_LAST_UPDATED INTEGER NOT NULL,
                $KEY_IS_SYNCED INTEGER DEFAULT 0,
                $KEY_THUMBNAIL_PATH TEXT
            )
        """.trimIndent()

        db.execSQL(createUsersTable)
        db.execSQL(createTasksTable)
        db.execSQL(createCoursesTable)
        db.execSQL(createResourcesTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Drop older tables if they exist
        db.execSQL("DROP TABLE IF EXISTS $TABLE_USERS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_TASKS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_COURSES")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_RESOURCES")

        // Create tables again
        onCreate(db)
    }

    // User CRUD operations
    fun addUser(user: User): Long {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(KEY_ID, user.id)
            put(KEY_NAME, user.name)
            put(KEY_EMAIL, user.email)
            put(KEY_UNIVERSITY, user.university)
            put(KEY_CURRENT_SEMESTER, user.currentSemester)
        }

        val result = db.insert(TABLE_USERS, null, values)
        db.close()
        return result
    }

    fun getUser(userId: String): User? {
        val db = this.readableDatabase
        var user: User? = null

        val cursor = db.query(
            TABLE_USERS,
            arrayOf(KEY_ID, KEY_NAME, KEY_EMAIL, KEY_UNIVERSITY, KEY_CURRENT_SEMESTER),
            "$KEY_ID = ?",
            arrayOf(userId),
            null,
            null,
            null
        )

        if (cursor.moveToFirst()) {
            user = User(
                id = cursor.getString(cursor.getColumnIndexOrThrow(KEY_ID)),
                name = cursor.getString(cursor.getColumnIndexOrThrow(KEY_NAME)),
                email = cursor.getString(cursor.getColumnIndexOrThrow(KEY_EMAIL)),
                university = cursor.getString(cursor.getColumnIndexOrThrow(KEY_UNIVERSITY)),
                currentSemester = cursor.getString(cursor.getColumnIndexOrThrow(KEY_CURRENT_SEMESTER))
            )
        }

        cursor.close()
        return user
    }

    fun updateUser(user: User): Int {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(KEY_NAME, user.name)
            put(KEY_EMAIL, user.email)
            put(KEY_UNIVERSITY, user.university)
            put(KEY_CURRENT_SEMESTER, user.currentSemester)
        }

        val result = db.update(
            TABLE_USERS,
            values,
            "$KEY_ID = ?",
            arrayOf(user.id)
        )

        db.close()
        return result
    }

    // Task CRUD operations
    fun addTask(task: Task): Long {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(KEY_ID, task.id)
            put(KEY_USER_ID, task.userId)
            put(KEY_TITLE, task.title)
            put(KEY_DESCRIPTION, task.description)
            put(KEY_COURSE_ID, task.courseId)
            put(KEY_COURSE_NAME, task.courseName)
            put(KEY_DUE_DATE, task.dueDate?.time)
            put(KEY_PRIORITY, task.priority)
            put(KEY_STATUS, task.status)
            put(KEY_TYPE, task.type)
            put(KEY_REMINDER_SET, if (task.reminderSet) 1 else 0)
            put(KEY_GRADE, task.grade)
            put(KEY_IS_SYNCED, if (task.isSynced) 1 else 0)
            put(KEY_LAST_UPDATED, task.lastUpdated.time)
        }

        val result = db.insertWithOnConflict(TABLE_TASKS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        db.close()
        return result
    }

    fun getTasksForUser(userId: String): List<Task> {
        val taskList = mutableListOf<Task>()
        val db = this.readableDatabase

        val cursor = db.query(
            TABLE_TASKS,
            null,
            "$KEY_USER_ID = ?",
            arrayOf(userId),
            null,
            null,
            "$KEY_DUE_DATE ASC"
        )

        if (cursor.moveToFirst()) {
            do {
                val task = Task(
                    id = cursor.getString(cursor.getColumnIndexOrThrow(KEY_ID)),
                    userId = cursor.getString(cursor.getColumnIndexOrThrow(KEY_USER_ID)),
                    title = cursor.getString(cursor.getColumnIndexOrThrow(KEY_TITLE)),
                    description = cursor.getString(cursor.getColumnIndexOrThrow(KEY_DESCRIPTION)),
                    courseId = cursor.getString(cursor.getColumnIndexOrThrow(KEY_COURSE_ID)),
                    courseName = cursor.getString(cursor.getColumnIndexOrThrow(KEY_COURSE_NAME)),
                    dueDate = cursor.getLong(cursor.getColumnIndexOrThrow(KEY_DUE_DATE)).let { if (it > 0) Date(it) else null },
                    priority = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_PRIORITY)),
                    status = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_STATUS)),
                    type = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_TYPE)),
                    reminderSet = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_REMINDER_SET)) == 1,
                    grade = cursor.getFloat(cursor.getColumnIndexOrThrow(KEY_GRADE)),
                    isSynced = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_IS_SYNCED)) == 1,
                    lastUpdated = Date(cursor.getLong(cursor.getColumnIndexOrThrow(KEY_LAST_UPDATED)))
                )
                taskList.add(task)
            } while (cursor.moveToNext())
        }

        cursor.close()
        return taskList
    }

    fun getTaskById(taskId: String): Task? {
        val db = this.readableDatabase
        var task: Task? = null

        val cursor = db.query(
            TABLE_TASKS,
            null,
            "$KEY_ID = ?",
            arrayOf(taskId),
            null,
            null,
            null
        )

        if (cursor.moveToFirst()) {
            task = Task(
                id = cursor.getString(cursor.getColumnIndexOrThrow(KEY_ID)),
                userId = cursor.getString(cursor.getColumnIndexOrThrow(KEY_USER_ID)),
                title = cursor.getString(cursor.getColumnIndexOrThrow(KEY_TITLE)),
                description = cursor.getString(cursor.getColumnIndexOrThrow(KEY_DESCRIPTION)),
                courseId = cursor.getString(cursor.getColumnIndexOrThrow(KEY_COURSE_ID)),
                courseName = cursor.getString(cursor.getColumnIndexOrThrow(KEY_COURSE_NAME)),
                dueDate = cursor.getLong(cursor.getColumnIndexOrThrow(KEY_DUE_DATE)).let { if (it > 0) Date(it) else null },
                priority = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_PRIORITY)),
                status = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_STATUS)),
                type = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_TYPE)),
                reminderSet = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_REMINDER_SET)) == 1,
                grade = cursor.getFloat(cursor.getColumnIndexOrThrow(KEY_GRADE)),
                isSynced = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_IS_SYNCED)) == 1,
                lastUpdated = Date(cursor.getLong(cursor.getColumnIndexOrThrow(KEY_LAST_UPDATED)))
            )
        }

        cursor.close()
        return task
    }

    fun updateTask(task: Task): Int {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(KEY_TITLE, task.title)
            put(KEY_DESCRIPTION, task.description)
            put(KEY_COURSE_ID, task.courseId)
            put(KEY_COURSE_NAME, task.courseName)
            put(KEY_DUE_DATE, task.dueDate?.time)
            put(KEY_PRIORITY, task.priority)
            put(KEY_STATUS, task.status)
            put(KEY_TYPE, task.type)
            put(KEY_REMINDER_SET, if (task.reminderSet) 1 else 0)
            put(KEY_GRADE, task.grade)
            put(KEY_IS_SYNCED, if (task.isSynced) 1 else 0)
            put(KEY_LAST_UPDATED, task.lastUpdated.time)
        }

        val result = db.update(
            TABLE_TASKS,
            values,
            "$KEY_ID = ?",
            arrayOf(task.id)
        )

        db.close()
        return result
    }

    fun deleteTask(taskId: String): Int {
        val db = this.writableDatabase
        val result = db.delete(
            TABLE_TASKS,
            "$KEY_ID = ?",
            arrayOf(taskId)
        )
        db.close()
        return result
    }

    fun getTasksForCourse(courseId: String): List<Task> {
        val taskList = mutableListOf<Task>()
        val db = this.readableDatabase

        val cursor = db.query(
            TABLE_TASKS,
            null,
            "$KEY_COURSE_ID = ?",
            arrayOf(courseId),
            null,
            null,
            "$KEY_DUE_DATE ASC"
        )

        if (cursor.moveToFirst()) {
            do {
                val task = Task(
                    id = cursor.getString(cursor.getColumnIndexOrThrow(KEY_ID)),
                    userId = cursor.getString(cursor.getColumnIndexOrThrow(KEY_USER_ID)),
                    title = cursor.getString(cursor.getColumnIndexOrThrow(KEY_TITLE)),
                    description = cursor.getString(cursor.getColumnIndexOrThrow(KEY_DESCRIPTION)),
                    courseId = cursor.getString(cursor.getColumnIndexOrThrow(KEY_COURSE_ID)),
                    courseName = cursor.getString(cursor.getColumnIndexOrThrow(KEY_COURSE_NAME)),
                    dueDate = cursor.getLong(cursor.getColumnIndexOrThrow(KEY_DUE_DATE)).let { if (it > 0) Date(it) else null },
                    priority = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_PRIORITY)),
                    status = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_STATUS)),
                    type = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_TYPE)),
                    reminderSet = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_REMINDER_SET)) == 1,
                    grade = cursor.getFloat(cursor.getColumnIndexOrThrow(KEY_GRADE)),
                    isSynced = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_IS_SYNCED)) == 1,
                    lastUpdated = Date(cursor.getLong(cursor.getColumnIndexOrThrow(KEY_LAST_UPDATED)))
                )
                taskList.add(task)
            } while (cursor.moveToNext())
        }

        cursor.close()
        return taskList
    }

    // Course CRUD operations
    fun addCourse(course: Course): Long {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(KEY_ID, course.id)
            put(KEY_USER_ID, course.userId)
            put(KEY_NAME, course.name)
            put(KEY_CODE, course.code)
            put(KEY_INSTRUCTOR_NAME, course.instructorName)
            put(KEY_INSTRUCTOR_EMAIL, course.instructorEmail)
            put(KEY_ROOM, course.room)
            put(KEY_DAY_OF_WEEK, course.dayOfWeek.joinToString(","))
            put(KEY_START_TIME, course.startTime)
            put(KEY_END_TIME, course.endTime)
            put(KEY_SEMESTER, course.semester)
            put(KEY_CREDIT_HOURS, course.creditHours)
            put(KEY_COLOR, course.color)
            put(KEY_IS_SYNCED, if (course.isSynced) 1 else 0)
            put(KEY_LAST_MODIFIED, course.lastModified.time)
        }

        val result = db.insertWithOnConflict(TABLE_COURSES, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        db.close()
        return result
    }

    fun getCoursesForUser(userId: String): List<Course> {
        val courseList = mutableListOf<Course>()
        val db = this.readableDatabase

        val cursor = db.query(
            TABLE_COURSES,
            null,
            "$KEY_USER_ID = ?",
            arrayOf(userId),
            null,
            null,
            "$KEY_NAME ASC"
        )

        if (cursor.moveToFirst()) {
            do {
                val course = Course(
                    id = cursor.getString(cursor.getColumnIndexOrThrow(KEY_ID)),
                    userId = cursor.getString(cursor.getColumnIndexOrThrow(KEY_USER_ID)),
                    name = cursor.getString(cursor.getColumnIndexOrThrow(KEY_NAME)),
                    code = cursor.getString(cursor.getColumnIndexOrThrow(KEY_CODE)),
                    instructorName = cursor.getString(cursor.getColumnIndexOrThrow(KEY_INSTRUCTOR_NAME)),
                    instructorEmail = cursor.getString(cursor.getColumnIndexOrThrow(KEY_INSTRUCTOR_EMAIL)),
                    room = cursor.getString(cursor.getColumnIndexOrThrow(KEY_ROOM)),
                    dayOfWeek = cursor.getString(cursor.getColumnIndexOrThrow(KEY_DAY_OF_WEEK))
                        .split(",")
                        .map { it.toInt() },
                    startTime = cursor.getString(cursor.getColumnIndexOrThrow(KEY_START_TIME)),
                    endTime = cursor.getString(cursor.getColumnIndexOrThrow(KEY_END_TIME)),
                    semester = cursor.getString(cursor.getColumnIndexOrThrow(KEY_SEMESTER)),
                    creditHours = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_CREDIT_HOURS)),
                    color = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_COLOR)),
                    isSynced = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_IS_SYNCED)) == 1,
                    lastModified = Date(cursor.getLong(cursor.getColumnIndexOrThrow(KEY_LAST_MODIFIED)))
                )
                courseList.add(course)
            } while (cursor.moveToNext())
        }

        cursor.close()
        return courseList
    }

    fun getCourseById(courseId: String): Course? {
        val db = this.readableDatabase
        var course: Course? = null

        val cursor = db.query(
            TABLE_COURSES,
            null,
            "$KEY_ID = ?",
            arrayOf(courseId),
            null,
            null,
            null
        )

        if (cursor.moveToFirst()) {
            course = Course(
                id = cursor.getString(cursor.getColumnIndexOrThrow(KEY_ID)),
                userId = cursor.getString(cursor.getColumnIndexOrThrow(KEY_USER_ID)),
                name = cursor.getString(cursor.getColumnIndexOrThrow(KEY_NAME)),
                code = cursor.getString(cursor.getColumnIndexOrThrow(KEY_CODE)),
                instructorName = cursor.getString(cursor.getColumnIndexOrThrow(KEY_INSTRUCTOR_NAME)),
                instructorEmail = cursor.getString(cursor.getColumnIndexOrThrow(KEY_INSTRUCTOR_EMAIL)),
                room = cursor.getString(cursor.getColumnIndexOrThrow(KEY_ROOM)),
                dayOfWeek = cursor.getString(cursor.getColumnIndexOrThrow(KEY_DAY_OF_WEEK))
                    .split(",")
                    .map { it.toInt() },
                startTime = cursor.getString(cursor.getColumnIndexOrThrow(KEY_START_TIME)),
                endTime = cursor.getString(cursor.getColumnIndexOrThrow(KEY_END_TIME)),
                semester = cursor.getString(cursor.getColumnIndexOrThrow(KEY_SEMESTER)),
                creditHours = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_CREDIT_HOURS)),
                color = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_COLOR)),
                isSynced = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_IS_SYNCED)) == 1,
                lastModified = Date(cursor.getLong(cursor.getColumnIndexOrThrow(KEY_LAST_MODIFIED)))
            )
        }

        cursor.close()
        return course
    }

    fun updateCourse(course: Course): Int {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(KEY_NAME, course.name)
            put(KEY_CODE, course.code)
            put(KEY_INSTRUCTOR_NAME, course.instructorName)
            put(KEY_INSTRUCTOR_EMAIL, course.instructorEmail)
            put(KEY_ROOM, course.room)
            put(KEY_DAY_OF_WEEK, course.dayOfWeek.joinToString(","))
            put(KEY_START_TIME, course.startTime)
            put(KEY_END_TIME, course.endTime)
            put(KEY_SEMESTER, course.semester)
            put(KEY_CREDIT_HOURS, course.creditHours)
            put(KEY_COLOR, course.color)
            put(KEY_IS_SYNCED, if (course.isSynced) 1 else 0)
            put(KEY_LAST_MODIFIED, course.lastModified.time)
        }

        val result = db.update(
            TABLE_COURSES,
            values,
            "$KEY_ID = ?",
            arrayOf(course.id)
        )

        db.close()
        return result
    }

    fun deleteCourse(courseId: String): Int {
        val db = this.writableDatabase
        val result = db.delete(
            TABLE_COURSES,
            "$KEY_ID = ?",
            arrayOf(courseId)
        )
        db.close()
        return result
    }

    // Resource CRUD operations
    fun addResource(resource: Resource): Long {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(KEY_ID, resource.id)
            put(KEY_USER_ID, resource.userId)
            put(KEY_TITLE, resource.title)
            put(KEY_DESCRIPTION, resource.description)
            put(KEY_COURSE_ID, resource.courseId)
            put(KEY_COURSE_NAME, resource.courseName)
            put(KEY_TYPE, resource.type)
            put(KEY_FILE_PATH, resource.filePath)
            put(KEY_TAGS, resource.tags.joinToString(","))
            put(KEY_DATE_ADDED, resource.dateAdded.time)
            put(KEY_LAST_UPDATED, resource.lastModified.time)
            put(KEY_IS_SYNCED, if (resource.isSynced) 1 else 0)
            put(KEY_THUMBNAIL_PATH, resource.thumbnailPath)
        }

        val result = db.insertWithOnConflict(TABLE_RESOURCES, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        db.close()
        return result
    }

    fun getResourcesForUser(userId: String): List<Resource> {
        val resourceList = mutableListOf<Resource>()
        val db = this.readableDatabase

        val cursor = db.query(
            TABLE_RESOURCES,
            null,
            "$KEY_USER_ID = ?",
            arrayOf(userId),
            null,
            null,
            "$KEY_DATE_ADDED DESC"
        )

        if (cursor.moveToFirst()) {
            do {
                val resource = Resource(
                    id = cursor.getString(cursor.getColumnIndexOrThrow(KEY_ID)),
                    userId = cursor.getString(cursor.getColumnIndexOrThrow(KEY_USER_ID)),
                    title = cursor.getString(cursor.getColumnIndexOrThrow(KEY_TITLE)),
                    description = cursor.getString(cursor.getColumnIndexOrThrow(KEY_DESCRIPTION)),
                    courseId = cursor.getString(cursor.getColumnIndexOrThrow(KEY_COURSE_ID)),
                    courseName = cursor.getString(cursor.getColumnIndexOrThrow(KEY_COURSE_NAME)),
                    type = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_TYPE)),
                    filePath = cursor.getString(cursor.getColumnIndexOrThrow(KEY_FILE_PATH)),
                    tags = cursor.getString(cursor.getColumnIndexOrThrow(KEY_TAGS))
                        .split(",")
                        .filter { it.isNotEmpty() },
                    dateAdded = Date(cursor.getLong(cursor.getColumnIndexOrThrow(KEY_DATE_ADDED))),
                    lastModified = Date(cursor.getLong(cursor.getColumnIndexOrThrow(KEY_LAST_UPDATED))),
                    isSynced = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_IS_SYNCED)) == 1,
                    thumbnailPath = cursor.getString(cursor.getColumnIndexOrThrow(KEY_THUMBNAIL_PATH))
                )
                resourceList.add(resource)
            } while (cursor.moveToNext())
        }

        cursor.close()
        return resourceList
    }

    fun getResourceById(resourceId: String): Resource? {
        val db = this.readableDatabase
        var resource: Resource? = null

        val cursor = db.query(
            TABLE_RESOURCES,
            null,
            "$KEY_ID = ?",
            arrayOf(resourceId),
            null,
            null,
            null
        )

        if (cursor.moveToFirst()) {
            resource = Resource(
                id = cursor.getString(cursor.getColumnIndexOrThrow(KEY_ID)),
                userId = cursor.getString(cursor.getColumnIndexOrThrow(KEY_USER_ID)),
                title = cursor.getString(cursor.getColumnIndexOrThrow(KEY_TITLE)),
                description = cursor.getString(cursor.getColumnIndexOrThrow(KEY_DESCRIPTION)),
                courseId = cursor.getString(cursor.getColumnIndexOrThrow(KEY_COURSE_ID)),
                courseName = cursor.getString(cursor.getColumnIndexOrThrow(KEY_COURSE_NAME)),
                type = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_TYPE)),
                filePath = cursor.getString(cursor.getColumnIndexOrThrow(KEY_FILE_PATH)),
                tags = cursor.getString(cursor.getColumnIndexOrThrow(KEY_TAGS))
                    .split(",")
                    .filter { it.isNotEmpty() },
                dateAdded = Date(cursor.getLong(cursor.getColumnIndexOrThrow(KEY_DATE_ADDED))),
                lastModified = Date(cursor.getLong(cursor.getColumnIndexOrThrow(KEY_LAST_UPDATED))),
                isSynced = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_IS_SYNCED)) == 1,
                thumbnailPath = cursor.getString(cursor.getColumnIndexOrThrow(KEY_THUMBNAIL_PATH))
            )
        }

        cursor.close()
        return resource
    }

    fun updateResource(resource: Resource): Int {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(KEY_TITLE, resource.title)
            put(KEY_DESCRIPTION, resource.description)
            put(KEY_COURSE_ID, resource.courseId)
            put(KEY_COURSE_NAME, resource.courseName)
            put(KEY_TYPE, resource.type)
            put(KEY_FILE_PATH, resource.filePath)
            put(KEY_TAGS, resource.tags.joinToString(","))
            put(KEY_LAST_UPDATED, resource.lastModified.time)
            put(KEY_IS_SYNCED, if (resource.isSynced) 1 else 0)
            put(KEY_THUMBNAIL_PATH, resource.thumbnailPath)
        }

        val result = db.update(
            TABLE_RESOURCES,
            values,
            "$KEY_ID = ?",
            arrayOf(resource.id)
        )

        db.close()
        return result
    }

    fun deleteResource(resourceId: String): Int {
        val db = this.writableDatabase
        val result = db.delete(
            TABLE_RESOURCES,
            "$KEY_ID = ?",
            arrayOf(resourceId)
        )
        db.close()
        return result
    }

    fun getResourcesForCourse(courseId: String): List<Resource> {
        val resourceList = mutableListOf<Resource>()
        val db = this.readableDatabase

        val cursor = db.query(
            TABLE_RESOURCES,
            null,
            "$KEY_COURSE_ID = ?",
            arrayOf(courseId),
            null,
            null,
            "$KEY_DATE_ADDED DESC"
        )

        if (cursor.moveToFirst()) {
            do {
                val resource = Resource(
                    id = cursor.getString(cursor.getColumnIndexOrThrow(KEY_ID)),
                    userId = cursor.getString(cursor.getColumnIndexOrThrow(KEY_USER_ID)),
                    title = cursor.getString(cursor.getColumnIndexOrThrow(KEY_TITLE)),
                    description = cursor.getString(cursor.getColumnIndexOrThrow(KEY_DESCRIPTION)),
                    courseId = cursor.getString(cursor.getColumnIndexOrThrow(KEY_COURSE_ID)),
                    courseName = cursor.getString(cursor.getColumnIndexOrThrow(KEY_COURSE_NAME)),
                    type = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_TYPE)),
                    filePath = cursor.getString(cursor.getColumnIndexOrThrow(KEY_FILE_PATH)),
                    tags = cursor.getString(cursor.getColumnIndexOrThrow(KEY_TAGS))
                        .split(",")
                        .filter { it.isNotEmpty() },
                    dateAdded = Date(cursor.getLong(cursor.getColumnIndexOrThrow(KEY_DATE_ADDED))),
                    lastModified = Date(cursor.getLong(cursor.getColumnIndexOrThrow(KEY_LAST_UPDATED))),
                    isSynced = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_IS_SYNCED)) == 1,
                    thumbnailPath = cursor.getString(cursor.getColumnIndexOrThrow(KEY_THUMBNAIL_PATH))
                )
                resourceList.add(resource)
            } while (cursor.moveToNext())
        }

        cursor.close()
        return resourceList
    }

    // Utility methods for synchronization
    fun getUnsyncedTasks(): List<Task> {
        val taskList = mutableListOf<Task>()
        val db = this.readableDatabase
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        val cursor = db.query(
            TABLE_TASKS,
            null,
            "$KEY_IS_SYNCED = ?",
            arrayOf("0"),
            null,
            null,
            null
        )

        if (cursor.moveToFirst()) {
            do {
                val task = Task(
                    id = cursor.getString(cursor.getColumnIndexOrThrow(KEY_ID)),
                    userId = cursor.getString(cursor.getColumnIndexOrThrow(KEY_USER_ID)),
                    title = cursor.getString(cursor.getColumnIndexOrThrow(KEY_TITLE)),
                    description = cursor.getString(cursor.getColumnIndexOrThrow(KEY_DESCRIPTION)),
                    courseId = cursor.getString(cursor.getColumnIndexOrThrow(KEY_COURSE_ID)),
                    courseName = cursor.getString(cursor.getColumnIndexOrThrow(KEY_COURSE_NAME)),
                    dueDate = cursor.getLong(cursor.getColumnIndexOrThrow(KEY_DUE_DATE)).let { if (it > 0) Date(it) else null },
                    priority = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_PRIORITY)),
                    status = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_STATUS)),
                    type = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_TYPE)),
                    reminderSet = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_REMINDER_SET)) == 1,
                    grade = cursor.getFloat(cursor.getColumnIndexOrThrow(KEY_GRADE)),
                    isSynced = false,
                    lastUpdated = Date(cursor.getLong(cursor.getColumnIndexOrThrow(KEY_LAST_UPDATED)))
                )
                taskList.add(task)
            } while (cursor.moveToNext())
        }

        cursor.close()
        return taskList
    }

    fun getUnsyncedCourses(): List<Course> {
        val courseList = mutableListOf<Course>()
        val db = this.readableDatabase

        val cursor = db.query(
            TABLE_COURSES,
            null,
            "$KEY_IS_SYNCED = ?",
            arrayOf("0"),
            null,
            null,
            null
        )

        if (cursor.moveToFirst()) {
            do {
                val daysString = cursor.getString(cursor.getColumnIndexOrThrow(KEY_DAY_OF_WEEK))
                val days = daysString.split(",").map { it.toInt() }

                val course = Course(
                    id = cursor.getString(cursor.getColumnIndexOrThrow(KEY_ID)),
                    userId = cursor.getString(cursor.getColumnIndexOrThrow(KEY_USER_ID)),
                    name = cursor.getString(cursor.getColumnIndexOrThrow(KEY_NAME)),
                    code = cursor.getString(cursor.getColumnIndexOrThrow(KEY_CODE)),
                    instructorName = cursor.getString(cursor.getColumnIndexOrThrow(KEY_INSTRUCTOR_NAME)),
                    instructorEmail = cursor.getString(cursor.getColumnIndexOrThrow(KEY_INSTRUCTOR_EMAIL)),
                    room = cursor.getString(cursor.getColumnIndexOrThrow(KEY_ROOM)),
                    dayOfWeek = days,
                    startTime = cursor.getString(cursor.getColumnIndexOrThrow(KEY_START_TIME)),
                    endTime = cursor.getString(cursor.getColumnIndexOrThrow(KEY_END_TIME)),
                    semester = cursor.getString(cursor.getColumnIndexOrThrow(KEY_SEMESTER)),
                    creditHours = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_CREDIT_HOURS)),
                    color = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_COLOR)),
                    isSynced = false,
                    lastModified = Date(cursor.getLong(cursor.getColumnIndexOrThrow(KEY_LAST_MODIFIED)))
                )
                courseList.add(course)
            } while (cursor.moveToNext())
        }

        cursor.close()
        return courseList
    }

    fun getUnsyncedResources(): List<Resource> {
        val resourceList = mutableListOf<Resource>()
        val db = this.readableDatabase
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        val cursor = db.query(
            TABLE_RESOURCES,
            null,
            "$KEY_IS_SYNCED = ?",
            arrayOf("0"),
            null,
            null,
            null
        )

        if (cursor.moveToFirst()) {
            do {
                val tagsString = cursor.getString(cursor.getColumnIndexOrThrow(KEY_TAGS))
                val tags = if (tagsString.isNotEmpty()) tagsString.split(",") else listOf()

                val resource = Resource(
                    id = cursor.getString(cursor.getColumnIndexOrThrow(KEY_ID)),
                    userId = cursor.getString(cursor.getColumnIndexOrThrow(KEY_USER_ID)),
                    title = cursor.getString(cursor.getColumnIndexOrThrow(KEY_TITLE)),
                    description = cursor.getString(cursor.getColumnIndexOrThrow(KEY_DESCRIPTION)),
                    courseId = cursor.getString(cursor.getColumnIndexOrThrow(KEY_COURSE_ID)),
                    courseName = cursor.getString(cursor.getColumnIndexOrThrow(KEY_COURSE_NAME)),
                    type = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_TYPE)),
                    filePath = cursor.getString(cursor.getColumnIndexOrThrow(KEY_FILE_PATH)),
                    tags = tags,
                    dateAdded = Date(cursor.getLong(cursor.getColumnIndexOrThrow(KEY_DATE_ADDED))),
                    lastModified = Date(cursor.getLong(cursor.getColumnIndexOrThrow(KEY_LAST_UPDATED))),
                    isSynced = false,
                    thumbnailPath = cursor.getString(cursor.getColumnIndexOrThrow(KEY_THUMBNAIL_PATH))
                )
                resourceList.add(resource)
            } while (cursor.moveToNext())
        }

        cursor.close()
        return resourceList
    }

    fun markTaskAsSynced(taskId: String) {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(KEY_IS_SYNCED, 1)
        }

        db.update(
            TABLE_TASKS,
            values,
            "$KEY_ID = ?",
            arrayOf(taskId)
        )

        db.close()
    }

    fun markCourseAsSynced(courseId: String) {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(KEY_IS_SYNCED, 1)
        }

        db.update(
            TABLE_COURSES,
            values,
            "$KEY_ID = ?",
            arrayOf(courseId)
        )

        db.close()
    }

    fun markResourceAsSynced(resourceId: String) {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(KEY_IS_SYNCED, 1)
        }

        db.update(
            TABLE_RESOURCES,
            values,
            "$KEY_ID = ?",
            arrayOf(resourceId)
        )

        db.close()
    }

    // Dashboard related queries
    fun getUpcomingTasksCount(userId: String): Int {

        val db = this.readableDatabase
        val currentTimeMillis = System.currentTimeMillis()

        val query = """
        SELECT COUNT(*) FROM $TABLE_TASKS 
        WHERE $KEY_USER_ID = ? AND $KEY_STATUS != 2 AND $KEY_DUE_DATE > ?
    """.trimIndent()

        val cursor = db.rawQuery(query, arrayOf(userId, currentTimeMillis.toString()))
        var count = 0

        if (cursor.moveToFirst()) {
            count = cursor.getInt(0)
        }

        cursor.close()
        return count
    }

    fun getCoursesCount(userId: String): Int {
        val db = this.readableDatabase

        val query = "SELECT COUNT(*) FROM $TABLE_COURSES WHERE $KEY_USER_ID = ?"
        val cursor = db.rawQuery(query, arrayOf(userId))
        var count = 0

        if (cursor.moveToFirst()) {
            count = cursor.getInt(0)
        }

        cursor.close()
        return count
    }

    fun getResourcesCount(userId: String): Int {
        val db = this.readableDatabase

        val query = "SELECT COUNT(*) FROM $TABLE_RESOURCES WHERE $KEY_USER_ID = ?"
        val cursor = db.rawQuery(query, arrayOf(userId))
        var count = 0

        if (cursor.moveToFirst()) {
            count = cursor.getInt(0)
        }

        cursor.close()
        return count
    }

    fun getUpcomingTasks(userId: String, limit: Int): List<Task> {
        val tasks = mutableListOf<Task>()
        val db = this.readableDatabase
        val currentTimeMillis = System.currentTimeMillis()

        val query = """
        SELECT * FROM $TABLE_TASKS 
        WHERE $KEY_USER_ID = ? AND $KEY_STATUS != 2 AND $KEY_DUE_DATE > ? 
        ORDER BY $KEY_DUE_DATE ASC LIMIT ?
    """.trimIndent()

        val cursor = db.rawQuery(query, arrayOf(userId, currentTimeMillis.toString(), limit.toString()))

        if (cursor.moveToFirst()) {
            do {
                // Your existing code to convert cursor to Task object
                val task = Task(
                    id = cursor.getString(cursor.getColumnIndexOrThrow(KEY_ID)),
                    userId = cursor.getString(cursor.getColumnIndexOrThrow(KEY_USER_ID)),
                    title = cursor.getString(cursor.getColumnIndexOrThrow(KEY_TITLE)),
                    description = cursor.getString(cursor.getColumnIndexOrThrow(KEY_DESCRIPTION)),
                    courseId = cursor.getString(cursor.getColumnIndexOrThrow(KEY_COURSE_ID)),
                    courseName = cursor.getString(cursor.getColumnIndexOrThrow(KEY_COURSE_NAME)),
                    dueDate = Date(cursor.getLong(cursor.getColumnIndexOrThrow(KEY_DUE_DATE))),
                    priority = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_PRIORITY)),
                    status = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_STATUS)),
                    type = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_TYPE)),
                    reminderSet = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_REMINDER_SET)) == 1,
                    grade = cursor.getFloat(cursor.getColumnIndexOrThrow(KEY_GRADE)),
                    isSynced = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_IS_SYNCED)) == 1,
                    lastUpdated = Date(cursor.getLong(cursor.getColumnIndexOrThrow(KEY_LAST_UPDATED)))
                )
                tasks.add(task)
            } while (cursor.moveToNext())
        }

        cursor.close()
        return tasks
    }

    fun getRecentResources(userId: String, limit: Int): List<Resource> {
        val resourceList = mutableListOf<Resource>()
        val db = this.readableDatabase
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        val query = """
            SELECT * FROM $TABLE_RESOURCES 
            WHERE $KEY_USER_ID = ? 
            ORDER BY $KEY_DATE_ADDED DESC LIMIT ?
        """.trimIndent()

        val cursor = db.rawQuery(query, arrayOf(userId, limit.toString()))

        if (cursor.moveToFirst()) {
            do {
                val tagsString = cursor.getString(cursor.getColumnIndexOrThrow(KEY_TAGS))
                val tags = if (tagsString.isNotEmpty()) tagsString.split(",") else listOf()

                val resource = Resource(
                    id = cursor.getString(cursor.getColumnIndexOrThrow(KEY_ID)),
                    userId = cursor.getString(cursor.getColumnIndexOrThrow(KEY_USER_ID)),
                    title = cursor.getString(cursor.getColumnIndexOrThrow(KEY_TITLE)),
                    description = cursor.getString(cursor.getColumnIndexOrThrow(KEY_DESCRIPTION)),
                    courseId = cursor.getString(cursor.getColumnIndexOrThrow(KEY_COURSE_ID)),
                    courseName = cursor.getString(cursor.getColumnIndexOrThrow(KEY_COURSE_NAME)),
                    type = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_TYPE)),
                    filePath = cursor.getString(cursor.getColumnIndexOrThrow(KEY_FILE_PATH)),
                    tags = tags,
                    dateAdded = Date(cursor.getLong(cursor.getColumnIndexOrThrow(KEY_DATE_ADDED))),
                    lastModified = Date(cursor.getLong(cursor.getColumnIndexOrThrow(KEY_LAST_UPDATED))),
                    isSynced = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_IS_SYNCED)) == 1,
                    thumbnailPath = cursor.getString(cursor.getColumnIndexOrThrow(KEY_THUMBNAIL_PATH))
                )
                resourceList.add(resource)
            } while (cursor.moveToNext())
        }

        cursor.close()
        return resourceList
    }

    // Search methods
    fun searchTasks(userId: String, query: String): List<Task> {
        val taskList = mutableListOf<Task>()
        val db = this.readableDatabase
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val searchQuery = "%$query%"

        val sql = """
            SELECT * FROM $TABLE_TASKS 
            WHERE $KEY_USER_ID = ? AND 
            ($KEY_TITLE LIKE ? OR $KEY_DESCRIPTION LIKE ? OR $KEY_COURSE_NAME LIKE ?) 
            ORDER BY $KEY_DUE_DATE ASC
        """.trimIndent()

        val cursor = db.rawQuery(sql, arrayOf(userId, searchQuery, searchQuery, searchQuery))

        if (cursor.moveToFirst()) {
            do {
                val task = Task(
                    id = cursor.getString(cursor.getColumnIndexOrThrow(KEY_ID)),
                    userId = cursor.getString(cursor.getColumnIndexOrThrow(KEY_USER_ID)),
                    title = cursor.getString(cursor.getColumnIndexOrThrow(KEY_TITLE)),
                    description = cursor.getString(cursor.getColumnIndexOrThrow(KEY_DESCRIPTION)),
                    courseId = cursor.getString(cursor.getColumnIndexOrThrow(KEY_COURSE_ID)),
                    courseName = cursor.getString(cursor.getColumnIndexOrThrow(KEY_COURSE_NAME)),
                    dueDate = cursor.getLong(cursor.getColumnIndexOrThrow(KEY_DUE_DATE)).let { if (it > 0) Date(it) else null },
                    priority = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_PRIORITY)),
                    status = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_STATUS)),
                    type = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_TYPE)),
                    reminderSet = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_REMINDER_SET)) == 1,
                    grade = cursor.getFloat(cursor.getColumnIndexOrThrow(KEY_GRADE)),
                    isSynced = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_IS_SYNCED)) == 1,
                    lastUpdated = Date(cursor.getLong(cursor.getColumnIndexOrThrow(KEY_LAST_UPDATED)))
                )
                taskList.add(task)
            } while (cursor.moveToNext())
        }

        cursor.close()
        return taskList
    }

    fun searchCourses(userId: String, query: String): List<Course> {
        val courseList = mutableListOf<Course>()
        val db = this.readableDatabase
        val searchQuery = "%$query%"

        val sql = """
            SELECT * FROM $TABLE_COURSES 
            WHERE $KEY_USER_ID = ? AND 
            ($KEY_NAME LIKE ? OR $KEY_CODE LIKE ? OR $KEY_INSTRUCTOR_NAME LIKE ?) 
            ORDER BY $KEY_NAME ASC
        """.trimIndent()

        val cursor = db.rawQuery(sql, arrayOf(userId, searchQuery, searchQuery, searchQuery))

        if (cursor.moveToFirst()) {
            do {
                val daysString = cursor.getString(cursor.getColumnIndexOrThrow(KEY_DAY_OF_WEEK))
                val days = daysString.split(",").map { it.toInt() }

                val course = Course(
                    id = cursor.getString(cursor.getColumnIndexOrThrow(KEY_ID)),
                    userId = cursor.getString(cursor.getColumnIndexOrThrow(KEY_USER_ID)),
                    name = cursor.getString(cursor.getColumnIndexOrThrow(KEY_NAME)),
                    code = cursor.getString(cursor.getColumnIndexOrThrow(KEY_CODE)),
                    instructorName = cursor.getString(cursor.getColumnIndexOrThrow(KEY_INSTRUCTOR_NAME)),
                    instructorEmail = cursor.getString(cursor.getColumnIndexOrThrow(KEY_INSTRUCTOR_EMAIL)),
                    room = cursor.getString(cursor.getColumnIndexOrThrow(KEY_ROOM)),
                    dayOfWeek = days,
                    startTime = cursor.getString(cursor.getColumnIndexOrThrow(KEY_START_TIME)),
                    endTime = cursor.getString(cursor.getColumnIndexOrThrow(KEY_END_TIME)),
                    semester = cursor.getString(cursor.getColumnIndexOrThrow(KEY_SEMESTER)),
                    creditHours = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_CREDIT_HOURS)),
                    color = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_COLOR)),
                    isSynced = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_IS_SYNCED)) == 1,
                    lastModified = Date(cursor.getLong(cursor.getColumnIndexOrThrow(KEY_LAST_MODIFIED)))
                )
                courseList.add(course)
            } while (cursor.moveToNext())
        }

        cursor.close()
        return courseList
    }

    fun searchResources(userId: String, query: String): List<Resource> {
        val resourceList = mutableListOf<Resource>()
        val db = this.readableDatabase
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val searchQuery = "%$query%"

        val sql = """
            SELECT * FROM $TABLE_RESOURCES 
            WHERE $KEY_USER_ID = ? AND 
            ($KEY_TITLE LIKE ? OR $KEY_DESCRIPTION LIKE ? OR $KEY_COURSE_NAME LIKE ? OR $KEY_TAGS LIKE ?) 
            ORDER BY $KEY_DATE_ADDED DESC
        """.trimIndent()

        val cursor = db.rawQuery(sql, arrayOf(userId, searchQuery, searchQuery, searchQuery, searchQuery))

        if (cursor.moveToFirst()) {
            do {
                val tagsString = cursor.getString(cursor.getColumnIndexOrThrow(KEY_TAGS))
                val tags = if (tagsString.isNotEmpty()) tagsString.split(",") else listOf()

                val resource = Resource(
                    id = cursor.getString(cursor.getColumnIndexOrThrow(KEY_ID)),
                    userId = cursor.getString(cursor.getColumnIndexOrThrow(KEY_USER_ID)),
                    title = cursor.getString(cursor.getColumnIndexOrThrow(KEY_TITLE)),
                    description = cursor.getString(cursor.getColumnIndexOrThrow(KEY_DESCRIPTION)),
                    courseId = cursor.getString(cursor.getColumnIndexOrThrow(KEY_COURSE_ID)),
                    courseName = cursor.getString(cursor.getColumnIndexOrThrow(KEY_COURSE_NAME)),
                    type = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_TYPE)),
                    filePath = cursor.getString(cursor.getColumnIndexOrThrow(KEY_FILE_PATH)),
                    tags = tags,
                    dateAdded = Date(cursor.getLong(cursor.getColumnIndexOrThrow(KEY_DATE_ADDED))),
                    lastModified = Date(cursor.getLong(cursor.getColumnIndexOrThrow(KEY_LAST_UPDATED))),
                    isSynced = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_IS_SYNCED)) == 1,
                    thumbnailPath = cursor.getString(cursor.getColumnIndexOrThrow(KEY_THUMBNAIL_PATH))
                )
                resourceList.add(resource)
            } while (cursor.moveToNext())
        }

        cursor.close()
        return resourceList
    }
}