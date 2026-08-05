# Quran Data Mapping

## Problème identifié
Le lecteur de Quran affichait toujours Al-Fatiha (page 1) car il chargeait systématiquement la page 1 indépendamment de la sourate sélectionnée.

## Structure des données
- `index.json` : Contient la liste des sourates et leur mapping de pages (`pages: [startPage, endPage]`).
- `verses.json` : Index des versets vers pages/lignes.
- `pages/NNN.json` : Données de rendu pour une page précise.

## Solution implémentée
1. Ajout de `getSurahById(id: Int)` dans `Qcf4Repository` pour récupérer les métadonnées d'une sourate, incluant sa page de début.
2. Mise à jour de `SurahDetailViewModel` pour charger dynamiquement la première page associée à la `surahId` sélectionnée.

## Gestion des pages contenant plusieurs sourates
Le rendu QCF4 gère les pages de manière séquentielle (Mushaf). Si une sourate commence au milieu d'une page, le système affiche la page entière, incluant éventuellement la fin de la sourate précédente. La navigation commence à la première page définie dans `index.json` pour la sourate cible.
