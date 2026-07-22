<?php
$imageDirectory = "english/";
$imageList = [];

// Pobierz listę plików z katalogu
$files = scandir($imageDirectory);

// Usuń kropki reprezentujące aktualny i nadrzędny katalog
$files = array_diff($files, array('.', '..'));

// Dodaj pełne ścieżki do plików obrazów do listy
foreach ($files as $file) {
    $imageList[] = $imageDirectory . $file;
}

// Zwróć listę jako JSON
echo json_encode($imageList);
?>
