document.addEventListener("DOMContentLoaded", function() {
    // Znajdź element obrazu
    var imgElement = document.getElementById("myImage");

    // Funkcja do zmiany obrazu co 60 sekund
    function changeImage(imageList) {
        // Losowo wybierz jeden obraz z listy
        var randomImage = imageList[Math.floor(Math.random() * imageList.length)];

        // Ustaw ścieżkę obrazu
        imgElement.src = randomImage;
    }

    // Pobierz listę obrazów za pomocą XMLHttpRequest
    var xhr = new XMLHttpRequest();
    xhr.onreadystatechange = function() {
        if (xhr.readyState == 4 && xhr.status == 200) {
            var imageList = JSON.parse(xhr.responseText);

            // Uruchom funkcję zmiany obrazu od razu po załadowaniu strony
            changeImage(imageList);

            // Uruchom funkcję zmiany obrazu co 60 sekund
            setInterval(function() {
                changeImage(imageList);
            }, 60000); // 60 000 milisekund = 60 sekund
        }
    };
    xhr.open("GET", "get_images.php", true);
    xhr.send();
});
