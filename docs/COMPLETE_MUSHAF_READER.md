# Complete Mushaf Reader

## Quran Complet (1 à 604)
Le lecteur n'est plus limité à la sourate sélectionnée. Il permet de naviguer continuellement de la page 1 jusqu'à la page 604 à travers l'ensemble du Mushaf.

## Header Mushaf Minimaliste
Le header contient uniquement :
- À gauche : `صفحة {pageNumber}`
- Au centre : `{surahNameArabic}` (mis à jour dynamiquement selon la page active)
- À droite : `جزء {juzNumber}` (calculé par page)

Aucun bouton de settings, aucune Bottom Navigation, aucune information de debug.

## Suppression de la Bottom Navigation
Sur l'écran `SurahDetailScreen`, la Bottom Navigation de l'application est totalement masquée pour offrir une expérience de lecture immersive de type Mushaf.

## Navigation par Swipe
- Swipe GAUCHE → DROITE : Page suivante
- Swipe DROITE → GAUCHE : Page précédente
(géré via `HorizontalPager` avec `reverseLayout = true`).

## Rendu Pleine Page
Suppression de tout scroll vertical. Chaque page est rendue comme une surface fixe et dimensionnée pour tenir entièrement dans l'espace disponible.
