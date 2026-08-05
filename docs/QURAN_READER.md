# Quran Reader

## Navigation par sourate
La navigation est basée sur le tableau `pages` contenu dans les métadonnées de chaque sourate (`index.json`). 

## Structure de surah.pages
Une sourate est définie par une liste de numéros de pages, par exemple `[2, 3, 4]`. Ce tableau est utilisé pour indexer les pages à charger.

## currentPageIndex
L'état de navigation est géré par `currentPageIndex` (index dans le tableau `pages`), ce qui garantit qu'on ne navigue qu'à travers les pages valides de la sourate.

## Chargement des pages
Le `SurahDetailViewModel` charge dynamiquement la page correspondant à `surah.pages[currentPageIndex]`.

## Cache
- `Qcf4FontManager` : Cache les `Typeface` chargées en mémoire pour éviter les rechargements inutiles.
- `Page` : Les données JSON sont parsées à la demande.

## Gestion des limites
Les boutons de navigation Previous/Next sont désactivés dynamiquement selon `currentPageIndex == 0` ou `currentPageIndex == surah.pages.lastIndex`.

## Pages partagées entre sourates
Le lecteur affiche la page QCF4 complète, même si elle contient des versets d'autres sourates, pour respecter l'intégrité du rendu Mushaf (Madinah).
