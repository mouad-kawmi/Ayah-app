# Quran Pager

## Pourquoi un Pager
La navigation par boutons est remplacée par `HorizontalPager` de Jetpack Compose pour offrir une expérience de lecture naturelle, fluide et proche d'un Mushaf physique.

## Navigation Swipe
La navigation est gérée par des swipes horizontaux, supportant nativement le RTL.

## Direction RTL
`HorizontalPager` s'adapte automatiquement à la direction de layout définie dans `AndroidManifest` (`android:supportsRtl="true"`) et au thème de l'application, assurant un swipe naturel pour la lecture du Quran.

## Mapping Sourate → Pages
Le mapping est dérivé de `index.json`. Chaque sourate contient une plage `[firstPage, lastPage]`. Le système génère dynamiquement la liste de toutes les pages concernées : `(firstPage..lastPage).toList()`.

## Pages partagées
Le système respecte l'intégrité des pages QCF4. Si une page contient la fin d'une sourate et le début d'une autre, la page entière est affichée conformément au Mushaf Madinah.

## Lazy Loading
Les fonts sont chargées de manière lazy via `Qcf4FontManager` et mises en cache. Les pages JSON sont chargées uniquement lorsqu'elles entrent dans le champ de vision du `HorizontalPager`.

## Cache
- `Typeface Cache` : Conservé pour éviter les rechargements de polices coûteux.
- `Page JSON` : Chargement asynchrone par page pour limiter l'empreinte mémoire.

## Last Read
Le `currentPageIndex` est sauvegardé (implémentation future), permettant de restaurer la lecture au bon index de page de la sourate.

## Bookmarks
Le système pourra sauvegarder l'ID de la sourate et l'index de la page pour une reprise exacte.
