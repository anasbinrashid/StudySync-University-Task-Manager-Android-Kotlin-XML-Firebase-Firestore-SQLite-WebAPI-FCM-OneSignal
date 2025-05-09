<?php
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type, Authorization');

// Handle preflight OPTIONS requests
if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(200);
    exit();
}

header('Content-Type: application/json');

// Enable error reporting
error_reporting(E_ALL);
ini_set('display_errors', 1);

// Log file path
$logFile = __DIR__ . '/error.log';

// Function to log errors
function logError($message) {
    global $logFile;
    $timestamp = date('Y-m-d H:i:s');
    file_put_contents($logFile, "[$timestamp] $message\n", FILE_APPEND);
}

$host = 'localhost';
$dbname = 'studysync_db';
$username = 'root';
$password = '';

try {
    $pdo = new PDO("mysql:host=$host;dbname=$dbname", $username, $password);
    $pdo->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
    logError("Database connection successful");

    // Create resources table if it doesn't exist
    $createTableSQL = "CREATE TABLE IF NOT EXISTS resources (
        id VARCHAR(255) PRIMARY KEY,
        user_id VARCHAR(255) NOT NULL,
        title VARCHAR(255) NOT NULL,
        description TEXT,
        course_id VARCHAR(255),
        course_name VARCHAR(255),
        type INT,
        file_path TEXT,
        tags TEXT,
        date_added BIGINT,
        last_updated BIGINT,
        thumbnail_path TEXT
    )";
    
    $pdo->exec($createTableSQL);
    logError("Resources table checked/created successfully");
} catch(PDOException $e) {
    logError("Database connection failed: " . $e->getMessage());
    echo json_encode(['error' => $e->getMessage()]);
    exit();
}
?>