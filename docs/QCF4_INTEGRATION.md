# Intégration du système QCF4

## Qu'est-ce que `quran-qcf4-main` ?
C'est un moteur de rendu de Quran basé sur le standard QCF v4 (Quran Complex Font, version 4). Il utilise les fontes du Madinah Mushaf (1441 AH) pour un rendu fidèle.

## Structure
- `pages/` : 604 fichiers JSON (1 par page).
- `fonts/` : 47 polices TTF (à intégrer dans les assets).
- `index.json` : Métadonnées des sourates.
- `verses.json` : Mapping verset -> page/ligne.
- `font-map.json` : Mapping page -> police.

## Fonctionnement du rendu
1. Charger le fichier JSON de la page souhaitée.
2. Déterminer la police nécessaire via `font-map.json`.
3. Charger la police correspondante depuis les assets.
4. Parcourir les lignes et les mots du JSON.
5. Rendre chaque mot en utilisant la police spécifiée et le caractère Unicode (`char`) correspondant au glyph QCF4.

## Mapping des données
Le mapping entre une sourate et ses pages est dérivé de `index.json`. Chaque sourate possède un tableau `pages` indiquant sa plage de pages (ex: `[1, 1]` pour Al-Fatiha, `[2, 49]` pour Al-Baqarah).
Le `SurahDetailViewModel` utilise ces informations pour charger la première page de la sourate sélectionnée.
