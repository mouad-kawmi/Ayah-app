# Mushaf UI

## Header
Un header compact affiche le numéro de page, le nom de la sourate, le numéro de Juz et une icône de paramètres.

## Page Quran
La page est rendue en utilisant un conteneur fixe qui calcule un `scale` uniforme pour que la page QCF4 soit entièrement visible sans déformation ni scroll vertical.

## Navigation
La navigation utilise `HorizontalPager` avec `reverseLayout = true` pour assurer un comportement de swipe naturel :
- Swipe LEFT → RIGHT : Page suivante
- Swipe RIGHT → LEFT : Page précédente

## Données
Le mapping Sourate → Pages est géré dynamiquement par `index.json`, garantissant que toutes les pages d'une sourate sont accessibles. Les données de Juz ne sont pas actuellement présentes dans le dataset, elles sont affichées comme un placeholder en attendant une source de données fiable.
