<?php
require_once 'db_connect.php';

// Get the request method
$method = $_SERVER['REQUEST_METHOD'];

// Get the action from query parameters
$action = isset($_GET['action']) ? $_GET['action'] : '';

// Log the request
logError("Received $method request with action: $action");

switch ($method) {
    case 'GET':
        if (isset($_GET['id'])) {
            // Get single resource
            try {
                $stmt = $pdo->prepare("SELECT * FROM resources WHERE id = ?");
                $stmt->execute([$_GET['id']]);
                $resource = $stmt->fetch(PDO::FETCH_ASSOC);
                if ($resource) {
                    logError("Resource found: " . $_GET['id']);
                    echo json_encode($resource);
                } else {
                    logError("Resource not found: " . $_GET['id']);
                    echo json_encode(['error' => 'Resource not found']);
                }
            } catch (PDOException $e) {
                logError("Database error: " . $e->getMessage());
                echo json_encode(['error' => $e->getMessage()]);
            }
        } elseif (isset($_GET['user_id'])) {
            // Get resources by user ID
            try {
                $stmt = $pdo->prepare("SELECT * FROM resources WHERE user_id = ?");
                $stmt->execute([$_GET['user_id']]);
                $resources = $stmt->fetchAll(PDO::FETCH_ASSOC);
                logError("Found " . count($resources) . " resources for user: " . $_GET['user_id']);
                echo json_encode($resources);
            } catch (PDOException $e) {
                logError("Database error: " . $e->getMessage());
                echo json_encode(['error' => $e->getMessage()]);
            }
        }
        break;

    case 'POST':
        // Get JSON data from request body
        $data = json_decode(file_get_contents('php://input'), true);
        
        if ($data === null) {
            logError("Invalid JSON data received");
            echo json_encode(['error' => 'Invalid JSON data']);
            break;
        }
        
        logError("Received data: " . json_encode($data));
        
        switch ($action) {
            case 'create':
                try {
                    $stmt = $pdo->prepare("INSERT INTO resources (id, user_id, title, description, course_id, course_name, type, file_path, tags, date_added, last_updated, thumbnail_path) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
                    $stmt->execute([
                        $data['id'],
                        $data['userId'],
                        $data['title'],
                        $data['description'],
                        $data['courseId'],
                        $data['courseName'],
                        $data['type'],
                        $data['filePath'],
                        json_encode($data['tags']),
                        $data['dateAdded'],
                        $data['lastModified'],
                        $data['thumbnailPath']
                    ]);
                    logError("Resource created successfully: " . $data['id']);
                    echo json_encode(['success' => true, 'message' => 'Resource created successfully']);
                } catch (PDOException $e) {
                    logError("Database error: " . $e->getMessage());
                    echo json_encode(['error' => $e->getMessage()]);
                }
                break;

            case 'update':
                try {
                    $stmt = $pdo->prepare("UPDATE resources SET title = ?, description = ?, course_id = ?, course_name = ?, type = ?, file_path = ?, tags = ?, last_updated = ?, thumbnail_path = ? WHERE id = ?");
                    $stmt->execute([
                        $data['title'],
                        $data['description'],
                        $data['courseId'],
                        $data['courseName'],
                        $data['type'],
                        $data['filePath'],
                        json_encode($data['tags']),
                        $data['lastModified'],
                        $data['thumbnailPath'],
                        $data['id']
                    ]);
                    logError("Resource updated successfully: " . $data['id']);
                    echo json_encode(['success' => true, 'message' => 'Resource updated successfully']);
                } catch (PDOException $e) {
                    logError("Database error: " . $e->getMessage());
                    echo json_encode(['error' => $e->getMessage()]);
                }
                break;

            case 'delete':
                try {
                    $stmt = $pdo->prepare("DELETE FROM resources WHERE id = ?");
                    $stmt->execute([$data['id']]);
                    logError("Resource deleted successfully: " . $data['id']);
                    echo json_encode(['success' => true, 'message' => 'Resource deleted successfully']);
                } catch (PDOException $e) {
                    logError("Database error: " . $e->getMessage());
                    echo json_encode(['error' => $e->getMessage()]);
                }
                break;
        }
        break;
}
?>