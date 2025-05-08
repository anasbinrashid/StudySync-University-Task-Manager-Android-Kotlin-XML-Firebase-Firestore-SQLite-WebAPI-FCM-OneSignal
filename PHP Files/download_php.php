<?php
require_once 'db_connect.php';

header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET');

if ($_SERVER['REQUEST_METHOD'] === 'GET' && isset($_GET['file_path'])) {
    $file_path = $_GET['file_path'];
    
    if (file_exists($file_path)) {
        // Get file info
        $file_name = basename($file_path);
        $file_size = filesize($file_path);
        $file_type = mime_content_type($file_path);
        
        // Set headers for file download
        header('Content-Type: ' . $file_type);
        header('Content-Disposition: attachment; filename="' . $file_name . '"');
        header('Content-Length: ' . $file_size);
        header('Cache-Control: no-cache, must-revalidate');
        header('Pragma: no-cache');
        header('Expires: 0');
        
        // Output file
        readfile($file_path);
        exit;
    } else {
        echo json_encode([
            'success' => false,
            'message' => 'File not found'
        ]);
    }
} else {
    echo json_encode([
        'success' => false,
        'message' => 'Invalid request'
    ]);
}
?>