<?php
require_once 'db_connect.php';

// Get the request method
$method = $_SERVER['REQUEST_METHOD'];

// Get the action from query parameters
$action = isset($_GET['action']) ? $_GET['action'] : '';

// Log the request
logError("Received $method request with action: $action");
logError("Request URI: " . $_SERVER['REQUEST_URI']);
logError("Raw input: " . file_get_contents('php://input'));

switch ($method) {
    case 'GET':
        if (isset($_GET['id'])) {
            // Get single resource
            try {
                $stmt = $pdo->prepare("SELECT * FROM resources WHERE id = ?");
                $stmt->execute([$_GET['id']]);
                $resource = $stmt->fetch(PDO::FETCH_ASSOC);
                if ($resource) {
                    // Convert tags from JSON to array
                    if (isset($resource['tags']) && !empty($resource['tags'])) {
                        $resource['tags'] = json_decode($resource['tags']);
                    } else {
                        $resource['tags'] = [];
                    }
                    
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
                
                // Convert tags from JSON to array for each resource
                foreach ($resources as &$resource) {
                    if (isset($resource['tags']) && !empty($resource['tags'])) {
                        $resource['tags'] = json_decode($resource['tags']);
                    } else {
                        $resource['tags'] = [];
                    }
                }
                
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
        $rawData = file_get_contents('php://input');
        $data = json_decode($rawData, true);
        
        if ($data === null) {
            logError("Invalid JSON data received. Raw data: " . $rawData);
            echo json_encode(['success' => false, 'message' => 'Invalid JSON data']);
            break;
        }
        
        logError("Received data: " . json_encode($data));
        
        switch ($action) {
            case 'create':
                try {
                    // Make sure we handle both camelCase and snake_case properties
                    $userId = isset($data['user_id']) ? $data['user_id'] : (isset($data['userId']) ? $data['userId'] : '');
                    $courseId = isset($data['course_id']) ? $data['course_id'] : (isset($data['courseId']) ? $data['courseId'] : '');
                    $courseName = isset($data['course_name']) ? $data['course_name'] : (isset($data['courseName']) ? $data['courseName'] : '');
                    $filePath = isset($data['file_path']) ? $data['file_path'] : (isset($data['filePath']) ? $data['filePath'] : '');
                    $dateAdded = isset($data['date_added']) ? $data['date_added'] : (isset($data['dateAdded']) ? (is_string($data['dateAdded']) ? strtotime($data['dateAdded']) * 1000 : time() * 1000) : (time() * 1000));
                    $lastUpdated = isset($data['last_updated']) ? $data['last_updated'] : (isset($data['lastModified']) ? (is_string($data['lastModified']) ? strtotime($data['lastModified']) * 1000 : time() * 1000) : (time() * 1000));
                    $thumbnailPath = isset($data['thumbnail_path']) ? $data['thumbnail_path'] : (isset($data['thumbnailPath']) ? $data['thumbnailPath'] : '');
                    
                    // Handle tags properly
                    $tags = [];
                    if (isset($data['tags'])) {
                        $tags = is_array($data['tags']) ? $data['tags'] : [$data['tags']];
                    }
                    
                    $tagsJson = json_encode($tags);
                    
                    $stmt = $pdo->prepare("INSERT INTO resources (id, user_id, title, description, course_id, course_name, type, file_path, tags, date_added, last_updated, thumbnail_path) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
                    $params = [
                        $data['id'],
                        $userId,
                        $data['title'],
                        $data['description'],
                        $courseId,
                        $courseName,
                        $data['type'],
                        $filePath,
                        $tagsJson,
                        $dateAdded,
                        $lastUpdated,
                        $thumbnailPath
                    ];
                    
                    logError("Executing insert with params: " . json_encode($params));
                    $stmt->execute($params);
                    logError("Resource created successfully: " . $data['id']);
                    
                    echo json_encode([
                        'success' => true, 
                        'message' => 'Resource created successfully',
                        'data' => [
                            'id' => $data['id'],
                            'user_id' => $userId,
                            'title' => $data['title'],
                            'description' => $data['description'],
                            'course_id' => $courseId,
                            'course_name' => $courseName,
                            'type' => $data['type'],
                            'file_path' => $filePath,
                            'tags' => $tags,
                            'date_added' => $dateAdded,
                            'last_updated' => $lastUpdated,
                            'thumbnail_path' => $thumbnailPath
                        ]
                    ]);
                } catch (PDOException $e) {
                    logError("Database error: " . $e->getMessage());
                    logError("SQL State: " . $e->getCode());
                    echo json_encode(['success' => false, 'error' => $e->getMessage(), 'code' => $e->getCode()]);
                }
                break;

            case 'update':
                try {
                    // Make sure we handle both camelCase and snake_case properties
                    $courseId = isset($data['course_id']) ? $data['course_id'] : (isset($data['courseId']) ? $data['courseId'] : '');
                    $courseName = isset($data['course_name']) ? $data['course_name'] : (isset($data['courseName']) ? $data['courseName'] : '');
                    $filePath = isset($data['file_path']) ? $data['file_path'] : (isset($data['filePath']) ? $data['filePath'] : '');
                    $lastUpdated = isset($data['last_updated']) ? $data['last_updated'] : (isset($data['lastModified']) ? (is_string($data['lastModified']) ? strtotime($data['lastModified']) * 1000 : time() * 1000) : (time() * 1000));
                    $thumbnailPath = isset($data['thumbnail_path']) ? $data['thumbnail_path'] : (isset($data['thumbnailPath']) ? $data['thumbnailPath'] : '');
                    
                    // Handle tags properly
                    $tags = [];
                    if (isset($data['tags'])) {
                        $tags = is_array($data['tags']) ? $data['tags'] : [$data['tags']];
                    }
                    
                    $tagsJson = json_encode($tags);
                    
                    $stmt = $pdo->prepare("UPDATE resources SET title = ?, description = ?, course_id = ?, course_name = ?, type = ?, file_path = ?, tags = ?, last_updated = ?, thumbnail_path = ? WHERE id = ?");
                    $params = [
                        $data['title'],
                        $data['description'],
                        $courseId,
                        $courseName,
                        $data['type'],
                        $filePath,
                        $tagsJson,
                        $lastUpdated,
                        $thumbnailPath,
                        $data['id']
                    ];
                    
                    logError("Executing update with params: " . json_encode($params));
                    $stmt->execute($params);
                    logError("Resource updated successfully: " . $data['id']);
                    echo json_encode(['success' => true, 'message' => 'Resource updated successfully']);
                } catch (PDOException $e) {
                    logError("Database error: " . $e->getMessage());
                    echo json_encode(['success' => false, 'error' => $e->getMessage()]);
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
                    echo json_encode(['success' => false, 'error' => $e->getMessage()]);
                }
                break;
            
            default:
                logError("Unknown action: $action");
                echo json_encode(['success' => false, 'message' => 'Unknown action']);
                break;
        }
        break;
        
    default:
        logError("Unsupported HTTP method: $method");
        echo json_encode(['success' => false, 'message' => 'Unsupported HTTP method']);
        break;
}
?>