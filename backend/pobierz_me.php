<?php
include('gps_track_config.php');
include('auth.php');

header('Content-Type: application/json');
echo json_encode([
    'id'                   => $current_user['id'],
    'username'             => $current_user['username'],
    'display_name'         => $current_user['display_name'] ?? $current_user['username'],
    'is_admin'             => (bool)$current_user['is_admin'],
    'must_change_password' => (bool)$current_user['must_change_password'],
    'readonly'             => (bool)$auth_readonly,
    // write_api_key is sensitive — only expose it in interactive sessions,
    // never under a read-only token.
    'write_api_key'        => $auth_readonly ? null : $current_user['write_api_key'],
]);
