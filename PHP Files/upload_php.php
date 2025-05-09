<?php
require_once 'db_connect.php';

header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: POST');
header('Access-Control-Allow-Headers: Content-Type');

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $resource_id = $_POST['resource_id'];
    
    if (isset($_FILES['file'])) {
        $file = $_FILES['file'];
        $file_name = $file['name'];
        $file_tmp = $file['tmp_name'];
        $file_size = $file['size'];
        $file_error = $file['error'];
        
        // Create uploads directory if it doesn't exist
        $upload_dir = '../uploads/';
        if (!file_exists($upload_dir)) {
            mkdir($upload_dir, 0777, true);
        }
        
        // Generate unique filename
        $file_ext = strtolower(pathinfo($file_name, PATHINFO_EXTENSION));
        $new_file_name = uniqid() . '.' . $file_ext;
        $upload_path = $upload_dir . $new_file_name;
        
        if ($file_error === 0) {
            if (move_uploaded_file($file_tmp, $upload_path)) {
                // Update resource in database with new file path
                $stmt = $pdo->prepare("UPDATE resources SET file_path = ? WHERE id = ?");
                $stmt->execute([$upload_path, $resource_id]);
                
                echo json_encode([
                    'success' => true,
                    'message' => 'File uploaded successfully',
                    'file_path' => $upload_path
                ]);
            } else {
                echo json_encode([
                    'success' => false,
                    'message' => 'Failed to move uploaded file'
                ]);
            }
        } else {
            echo json_encode([
                'success' => false,
                'message' => 'Error uploading file'
            ]);
        }
    } else {
        echo json_encode([
            'success' => false,
            'message' => 'No file uploaded'
        ]);
    }
}
?>