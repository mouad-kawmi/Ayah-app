# Tafsir Download System — Conception & Migration

> **Source de vérité unique** pour la fonctionnalité « Tafsirs téléchargeables ».
> Toute discussion future concernant les tafsirs **met à jour ce fichier**.
> Ne jamais créer un second document de conception pour cette fonctionnalité.
>
> **⚠ ARCHITECTURE GELÉE** — v1 : sections 1–11 ; **v2 : §13 « Politique officielle des sources, licences et attributions »** (provenance obligatoire, interdiction des API tierces au runtime, classification des tafsirs, écran d'attribution, cycle de vie des ressources §13.5).
> Les idées futures ne s'ajoutent **que** dans la section « 12. Future Ideas » (§12)
> et ne modifient le cœur de l'architecture **que si absolument nécessaire** (avec justification).
> Toute déviation aux règles de §13 (provenance, sources, attribution) suit la même procédure de justification.

---

## 1. Objectif

| Exigence | Règle |
|---|---|
| Tafsir intégré | **Tafsir Al-Muyassar uniquement** est embarqué dans l'APK. |
| Disponibilité | Al-Muyassar est disponible **immédiatement après installation**, sans internet. |
| Suppression | Al-Muyassar ne peut **jamais** être supprimé. |
| Sélection | Al-Muyassar est le tafsir **sélectionné par défaut**. |
| Autres tafsirs | Tous les autres tafsirs sont des **téléchargements optionnels** (As-Saadi, Ibn Kathir, At-Tabari, Al-Qurtubi, Al-Baghawi, …). |
| Extensibilité | L'architecture permet d'ajouter un nouveau tafsir **sans modification de code** (catalogue distant). |
| Source des contenus | Le catalogue et les fichiers de tafsir sont hébergés par **notre propre infrastructure** (`catalog.json` + `.db`). **Aucune API tierce n'est appelée au runtime** (politique formelle §13.2). Les données tierces (alquran.cloud, Tanzil…) servent uniquement à la **génération build-time** des fichiers. |
| Provenance | Chaque entrée du catalogue porte des **métadonnées complètes de provenance** — `source`, `edition`, `publisher`, `author`, `license`, `website`, `copyrightNotice` (§13.1). Une ressource sans provenance complète **n'entre jamais** dans le catalogue officiel. |
| Classification | Tafsirs classés officiellement : **Bundled** (الميسر — permanent, jamais supprimable), **Tier A / Tier B** téléchargeables, **Permission requise** (§13.3). |
| États | Chaque tafsir a 3 états : `NOT_INSTALLED`, `DOWNLOADING`, `INSTALLED`. |
| Actions utilisateur | Télécharger · Annuler le téléchargement · Supprimer · Sélectionner comme tafsir courant. |
| Suppression | La suppression d'un tafsir libère l'espace **immédiatement** et n'affecte **jamais** les autres. |
| Stockage | Les tafsirs téléchargés vivent dans le **stockage privé de l'application**, indépendamment les uns des autres. |
| Mise à jour | Un tafsir individuel peut être mis à jour **sans mise à jour de l'application**. |
| Hors-ligne | Un tafsir téléchargé fonctionne **100 % hors ligne**. |
| Performance | Aucun délai au démarrage ; chargement paresseux (lazy) ; changement de tafsir rapide ; mémoire maîtrisée. |
| Réutilisabilité | La couche de téléchargement est **générique** : réutilisable plus tard pour d'autres ressources (traductions, audio, dictionnaires, packs d'apprentissage) **sans refonte**. |
| Multilingue | L'architecture **ne suppose pas** des tafsirs uniquement arabes : les métadonnées portent `language`, `author`, `license`, `lastUpdated`, `minAppVersion` — tafsirs anglais, français, indonésien, turc… possibles **sans changement d'architecture**. |
| Compatibilité app | Chaque ressource déclare `minAppVersion` : si la version de l'application est inférieure, l'UI **bloque l'installation** et explique que l'application doit être mise à jour. |
| Compatibilité base | Chaque base SQLite déclare son `schemaVersion` ; l'application **vérifie la compatibilité avant d'activer** un tafsir ; les migrations futures sont possibles **sans refonte**. |
| Schéma standard | Toutes les bases de tafsir suivent **le même schéma** (`meta`, `tafsir`, FTS optionnel). **Aucun tafsir ne requiert de code personnalisé** — le lecteur est unique et commun. |
| Sauvegarde / restauration | L'architecture est **compatible avec un futur Export / Import** (restauration sur un autre appareil sans re-téléchargement). |
| Multi-installation | De **nombreux tafsirs peuvent être installés simultanément** ; un seul est sélectionné aujourd'hui, mais le stockage permet déjà la comparaison côte à côte ou le changement instantané. |

---

## 2. Audit de l'existant (état actuel)

### 2.1 Classes identifiées liées au tafsir / à la lecture

| Fichier | Rôle actuel | Verdict |
|---|---|---|
| `core/utils/TafsirManager.kt` | Objet avec 7 versets codés en dur + texte de repli. | **Code mort** — `getTafsir()` n'a **aucun appelant** (vérifié par grep). À supprimer. |
| `data/translation/TranslationRepository.kt` | « Éditions » : `ar.muyassar` (tafsir), `fr.hamidullah`, `en.sahih`. Téléchargement JSON depuis `api.alquran.cloud/v1/quran/<id>` vers `filesDir/translation_<id>.json`. Lecture : relit **tout le fichier JSON + parse complet à chaque verset**. | À **remplacer** pour la partie tafsir ; à **conserver** pour les traductions fr/en. |
| `data/translation/TranslationModels.kt` | `Edition`, `TranslationResponse`, … | Conservé (traductions). `Edition` n'est plus utilisé pour les tafsirs. |
| `presentation/translation/TranslationViewModel.kt` | États `downloadedEditions` / `downloadingEditions` ; pas de progression, pas de cancel, pas d'erreur. | Conservé pour les traductions uniquement. |
| `presentation/quran/SurahDetailScreen.kt` (l. 550–879) | Bouton « تفسير » → tab inline des éditions ; `selectedTab` non persistant (`remember` local) ; bouton téléchargement sans progression ; `textAlign` RTL pour l'arabe. | **Réécriture partielle** : l'onglet tafsir bascule sur le nouveau système ; l'onglet traduction reste inchangé. |
| `core/utils/QuranPreferences.kt` | Prefs clés/valeurs (adhan, khatma, reciters…). | À **étendre** : `selected_tafsir_id`. |
| `data/audio/AudioDownloadManager.kt` | Précurseur utile : StateFlow de progression, fichier `.tmp` + `renameTo`, `deleteDownload`. | **Référence de patterns** (pas de réutilisation directe). |

### 2.2 Déficiences de l'existant (motivations du projet)

1. **Al-Muyassar n'est PAS embarqué** : premier affichage du tafsir exige internet (téléchargement complet via API).
2. **Lecture lente** : re-parse de l'intégralité du JSON (≈1 Mo) à chaque verset affiché.
3. **Aucune progression** de téléchargement (UI : simple spinner).
4. **Pas d'annulation**, **pas de reprise**, **pas de suppression** de téléchargement.
5. **Pas de métadonnées** (auteur, version, langue, taille) — impossible d'afficher le Manager demandé.
6. **Pas de persistance** de la sélection (l'onglet se réinitialise à chaque ouverture).
7. **Pas de mécanisme de mise à jour** individuelle.
8. **Aucune vérification d'intégrité** des fichiers téléchargés.
9. **Pas de gestion d'espace disque**.
10. **Dépendance runtime à une API tierce** (`api.alquran.cloud`) pour tout contenu — hors périmètre pour la cible.
11. `TafsirManager` = placeholder codé en dur (code mort).

### 2.3 Infrastructure existante réutilisable

- OkHttp (`libs.okhttp`) — téléchargements.
- kotlinx-serialization (`libs.kotlinx.serialization.json`) — catalogues et index.
- Coroutines + StateFlow — progression.
- SQLite natif (`android.database.sqlite`, inclus dans le SDK) — **aucune nouvelle dépendance** (pas de Room).
- `filesDir` = stockage privé (pattern `AudioDownloadManager`).
- `DebugLogger` / `Instrumentation` / `LogCategory` — observabilité du projet (catégorie `TAFSIR` + `DOWNLOAD` à ajouter).
- Compose Material3 — écrans.
- Permission `INTERNET` déjà déclarée dans le manifest.
- `assets/` déjà utilisé (pages du Coran) — accueillera `assets/tafsir/muyassar.db`.

---

## 3. Architecture cible

### 3.1 Vue d'ensemble (couches)

```
┌──────────────────────────────────────────────────────────────┐
│  UI  SurahDetailScreen · TafsirManagerScreen                 │
│      (presentation/tafsir/TafsirManagerViewModel)            │
├──────────────────────────────────────────────────────────────┤
│  Tafsir (spécifique)                                         │
│      TafsirReader (SQLite + LRU) · TafsirSelectionStore      │
├──────────────────────────────────────────────────────────────┤
│  Ressources (GÉNÉRIQUE — réutilisable)                       │
│      ResourceCatalogRepository · ResourceDownloadManager     │
│      ResourceIndexStore · ResourceFileStore                  │
├──────────────────────────────────────────────────────────────┤
│  Stockage  files/resources/<type>/<id>.<ext> · .downloads/   │
│            assets/tafsir/muyassar.db                         │
├──────────────────────────────────────────────────────────────┤
│  Réseau  {BASE}/resources/catalog.json · {type}/{id}.file    │
│          (notre infrastructure — aucune API tierce)          │
└──────────────────────────────────────────────────────────────┘
```

**Principe** : la couche « Ressources » est **agnostique au contenu** — elle télécharge, reprend, vérifie, installe, supprime des fichiers opaques (octets). La couche « Tafsir » définit le format (SQLite), la sémantique d'affichage et la sélection. Toute future ressource (traductions, audio, dictionnaires, packs d'apprentissage) s'ajoute **uniquement** au-dessus de la couche générique, sans la modifier.

### 3.2 Composants détaillés

#### 3.2.1 Stockage local — `ResourceFileStore` (générique)

- Racine : `File(context.filesDir, "resources")`, sous-dossiers **par type** : `resources/tafsir/`, `resources/translation/`, `resources/audio/`, …
- **Un fichier par ressource** → l'indépendance (suppression d'un tafsir = suppression d'un seul fichier + entrée d'index).
- Le tafsir embarqué (`assets/tafsir/muyassar.db`) est **copié paresseusement** vers `files/resources/tafsir/muyassar.db` à la **première lecture** (jamais au démarrage de l'app). Auto-réparation : si le fichier copié manque/corrompu, re-copie depuis l'asset.
- Écritures atomiques : fichier partiel → vérification → `renameTo` → index.
- Suppression : `deleteFile()` de `<id>.<ext>` + nettoyage de `.downloads/<id>.*` + retrait de l'index + fermeture du lecteur si ouvert. **Libération immédiate**.

#### 3.2.2 Gestionnaire de téléchargements — `ResourceDownloadManager` (générique)

- **Reprise** : requête `GET` avec entête `Range: bytes=<done>-` sur le fichier `.part` existant ; métadonnées de reprise dans `.part.meta` (JSON : type, id, version, url, bytesDone, taille attendue). Si le serveur ignore `Range` (réponse 200), recommencer à zéro.
- **Progression** : `StateFlow<Map<String, DownloadProgress>>` (0..1, bytesDone, bytesTotal, état) — pattern `AudioDownloadManager` amélioré.
- **Annulation** : annule l'appel OkHttp ; **conserve le `.part`** pour une reprise ultérieure (l'annulation ≠ suppression). Un second « Download » après annulation reprend automatiquement.
- **Intégrité** : SHA-256 calculé **en continu pendant le téléchargement** (MessageDigest streaming, pas de passe supplémentaire) ; comparaison avec `sha256` du catalogue ; échec → suppression du `.part`, état `ERROR`, bouton Réessayer.
- **Installation atomique** : `.part` vérifié → `renameTo("<id>.<ext>")` → écriture de l'index → mise à jour du StateFlow.
- **Espace disque** : avant le démarrage, vérification `StatFs` (espace libre ≥ taille estimée + marge 10 Mo) ; sinon état `ERROR` avec message dédié.
- **Concurrence** : un seul téléchargement à la fois (file d'attente sérialisée) ; documenté et facile à paralléliser plus tard.
- **Cycle de vie** : téléchargement lancé depuis le Manager en coroutine applicative (comme l'audio existant). Travail en arrière-plan multi-processus (WorkManager) : option Phase 3.
- **Indépendance du contenu** : la couche ne connaît ni le schéma SQLite, ni le format audio, ni le contenu — uniquement type, id, version, URL, taille, SHA-256.

#### 3.2.3 Gestionnaire de fichiers / index — `ResourceIndexStore` (générique)

- Fichier `files/resources/index.json` (écrit atomiquement : `index.json.tmp` → `renameTo`).
- Contenu : `{ "schemaVersion": 1, "resources": { "<type>:<id>": { "type", "version", "schemaVersion", "installedAt", "sizeBytes", "sha256", "bundled" } } }`.
- **Clé composite `type:id`** → les types de ressources coexistent sans collision et se suppriment indépendamment.
- **Flag installé** = fichier présent **et** entrée d'index présente (la version vérifie les mises à jour).
- Al-Muyassar embarqué : entrée d'index écrite à la première copie avec `"bundled": true` (non supprimable, version = version embarquée dans l'APK).
- L'index est le **seul point de vérité** de l'installation ; il ne stocke **aucun contenu** (le contenu vit dans les fichiers).

#### 3.2.4 Catalogue — `ResourceCatalogRepository` (générique)

- **Catalogue distant** : `GET {BASE}/resources/catalog.json` (timeout 5 s), mis en cache dans `files/resources/catalog-cache.json`. Hébergé par **notre infrastructure** (CDN/object storage) — **aucune API tierce**.
- **Catalogue embarqué** : entrées minimales codées en dur pour les ressources embarquées (`muyassar` pour le type `tafsir`) — garantit le fonctionnement hors-ligne total.
- **Fusion** : le catalogue distant **remplace/augmente** l'embarqué ; les entrées `bundled: true` restent toujours présentes et non supprimables ; si le serveur publie une **version supérieure** pour `muyassar`, un bouton « Mettre à jour » apparaît (téléchargement de la nouvelle version — Al-Muyassar reste non supprimable).
- **Filtrage par type** : le module tafsir consomme uniquement les entrées `type=tafsir` ; un futur module traduction consomme `type=translation`, etc. Le même catalogue sert tous les types.
- **Champs de compatibilité** : chaque entrée du catalogue porte `minAppVersion` (version minimale de l'app) et `schemaVersion` (schéma de la base) — l'UI du Manager les vérifie **avant** d'autoriser un téléchargement.
- **Ajout d'une ressource sans code** : ajouter l'entrée JSON + le fichier sur le serveur. L'application se contente de lire le catalogue.
- Hors-ligne : le Manager affiche le cache + l'embarqué avec une bannière « hors connexion » ; les boutons de téléchargement sont désactivés.

**Provenance obligatoire (exigence architecturelle — §13.1) :** chaque entrée du catalogue porte les 7 champs de provenance, en plus des champs techniques (`type`, `version`, `sha256`, `minAppVersion`, `schemaVersion`, …) :

```json
{
  "id": "ibn_kathir",
  "name": "تفسير ابن كثير",
  "author": "ابن كثير",
  "source": "Shamela",
  "edition": "دار طيبة",
  "publisher": "...",
  "license": "Public Domain",
  "website": "...",
  "copyrightNotice": "...",
  "version": "1.0.0",
  "sha256": "..."
}
```

- Ces champs sont **documentaires ET architecturels** : ils alimentent l'écran « Sources & licences » (§13.4) et sont exigés pour toute entrée du catalogue officiel.
- L'entrée ci-dessus illustre les champs de provenance ; le modèle complet `ResourceMeta` (§5) ajoute `type`, `nameLatin`, `language`, `lastUpdated`, `minAppVersion`, `schemaVersion`, `downloadSizeBytes`, `downloadUrl`, `bundled`.

#### 3.2.5 Format standard des bases de tafsir (contrat de schéma)

La couche générique traite des octets opaques ; le **format est un contrat par type de ressource**. Pour le type `tafsir`, le format est **strictement standardisé** : toute base, embarquée ou téléchargée, suit **exactement le même schéma**. **Aucun tafsir ne peut requérir du code personnalisé** ; le lecteur est unique et commun.

Schéma SQLite imposé à toutes les bases de tafsir :

```sql
-- Table du contenu (obligatoire)
CREATE TABLE tafsir (
    verse_key TEXT PRIMARY KEY,      -- "surah:ayah" ex: "2:255"
    text      TEXT NOT NULL
);

-- Table des métadonnées (obligatoire) — clés REQUISES :
--   schema_version (Int)   → version du schéma de la base
--   name / name_ar         → nom du tafsir (latin / arabe)
--   author                 → auteur
--   version                → version du CONTENU
--   language               → langue (BCP 47 : ar, en, fr, id, tr…)
--   license                → licence du contenu (ex. Public Domain, CC BY 3.0)
--   source                 → source de préparation (ex. Shamela) — jamais backend runtime (§13.2)
--   edition                → édition de référence (ex. دار طيبة)
--   publisher              → éditeur / organisme émetteur
--   website                → site officiel / page de provenance
--   copyright_notice       → mention de droits (affichée dans « Sources & licences », §13.4)
--   generated_at           → date de génération de la base
CREATE TABLE meta (
    key   TEXT PRIMARY KEY,
    value TEXT NOT NULL
);

-- Recherche plein texte (OPTIONNEL — uniquement si la base la propose)
-- CREATE VIRTUAL TABLE tafsir_fts USING fts5(verse_key, text, content='tafsir', content_rowid='rowid');
```

- **Déclaration du schéma** : la version du schéma figure **à trois endroits** (cohérence vérifiée à l'activation) :
  1. `meta['schema_version']` (row obligatoire dans la base) ;
  2. `PRAGMA user_version` (redondance de sécurité) ;
  3. `ResourceMeta.schemaVersion` (déclaré dans le catalogue, avant téléchargement).
- **Compatibilité** : l'application ne connaît que `SUPPORTED_TAFSIR_SCHEMA_VERSIONS = {1}` (à ce jour). Règles :
  - `schemaVersion` (catalogue) **non supporté** → l'UI **bloque le téléchargement** avec explication (base trop récente pour cette version de l'app) ;
  - schéma de base **inférieur** au maximum supporté → activation après **migration** (voir ci-dessous) ;
  - schéma de base **supérieur/inconnu** → activation **refusée**, repli sur `muyassar`, log `ErrorCode` dédié, re-téléchargement possible après mise à jour de l'app.
- **Migrations futures sans refonte** : l'app embarque une table de migrations chaînées `Map<Int, (SQLiteDatabase) -> Unit>` (ex. `1 → 2`), appliquées sur une **copie** du fichier avant activation (les fichiers installés restent en lecture seule). Ajouter une migration = nouvelle entrée de la map dans une future version de l'app — l'architecture ne change pas.
- `verse_key` identique au format utilisé aujourd'hui (`"${surah.number}:${ayah.numberInSurah}"`).
- Lecture : requête point `SELECT text FROM tafsir WHERE verse_key = ?` (instruction préparée) → **ms**, pas de parse de fichier entier.
- La table `meta` rend le fichier auto-descriptif (le lecteur peut afficher auteur/version/langue sans le catalogue) — **requis pour le futur Export/Import**.
- Aucune dépendance Room : `android.database.sqlite.SQLiteDatabase.openDatabase(...)`.

#### 3.2.6 Lecture — `TafsirReader` (paresseux + cache, spécifique tafsir)

- **Singleton**. N'ouvre **aucune base** au démarrage de l'app.
- **Activation vérifiée** : avant d'activer une base, le lecteur lit `meta['schema_version']` + `PRAGMA user_version`, les confronte à `ResourceMeta.schemaVersion`, puis :
  - schéma connu + inférieur au max → **migration chaînée** sur une copie du fichier ;
  - schéma inconnu/supérieur → **refus d'activation**, repli silencieux sur `muyassar`, log `TAFSIR_SCHEMA_UNSUPPORTED` ;
  - base illisible (`SQLITE_NOTADB`) → signalé, re-téléchargement possible.
- `open(tafsirId)` à la demande : ouvre `resources/tafsir/<id>.db` en lecture seule, **ferme la précédente** (une seule base ouverte à la fois — mémoire minimale ; option : garder la base « sélectionnée » chaude). **Aucune exclusivité de stockage** : N tafsirs installés coexistent ; le lecteur n'ouvre que ce dont l'UI a besoin, et peut ouvrir n'importe quel sous-ensemble (futur comparateur côte à côte) sans modification.
- **Cache mémoire LRU** (LinkedHashMap accessOrder, ~256 entrées) `"<id>:<verseKey>" → texte` → navigation instantanée.
- Verset absent → `null` → l'UI affiche « لا يوجد تفسير لهذه الآية في هذا التفسير ».
- Al-Muyassar : si `resources/tafsir/muyassar.db` absent → copie depuis l'asset (auto-réparation).

#### 3.2.7 Sélection — `TafsirSelectionStore`

- Persistance : `QuranPreferences.saveSelectedTafsirId(context, id)` / `getSelectedTafsirId` (défaut `"muyassar"`) — clé `selected_tafsir_id` dans `quran_prefs`.
- Contrainte UI : seuls les tafsirs **installés** sont sélectionnables.

#### 3.2.8 UI — `TafsirManagerScreen` (+ `TafsirManagerViewModel`)

Nouvel écran, route `tafsir_manager`, entrée depuis l'écran **Paramètres**.

Chaque ligne du Manager :

| Champ | Source |
|---|---|
| Nom (arabe) + nom latin | Catalogue |
| Auteur | Catalogue |
| Langue | Catalogue |
| Version | Catalogue (état) / Index (installé) |
| Taille estimée | Catalogue (`downloadSizeBytes`) — affichée **avant** le téléchargement : « ≈ 8,4 Mo » |
| Taille réelle | Index (`installedSizeBytes`) — une fois installé |
| État | `مثبت` / `غير مثبت` / `جاري التحميل… %` |
| Sélection | « ✓ محدد حالياً » sur le tafsir courant |

Boutons contextuels :

- `NOT_INSTALLED` → **Télécharger** (avec taille estimée affichée)
- `DOWNLOADING` → barre de progression + **Annuler**
- `INSTALLED` → **Supprimer** + **Sélectionner**
- `ERROR` → message + **Réessayer**
- Version distante > version installée → **Mettre à jour**
- Al-Muyassar : badge « مدمج », jamais de bouton Supprimer.

États bloquants (installation empêchée avec explication) :

- **`minAppVersion` > version de l'app** → pas de bouton Télécharger ; message : « Ce tafsir nécessite la version X de l'application — veuillez mettre à jour l'application » (comparaison sémantique de versions `major.minor.patch`).
- **`schemaVersion` (catalogue) non supporté** → pas de bouton Télécharger ; message : « Cette base nécessite une version plus récente de l'application ».
- Hors-ligne → boutons désactivés + bannière « hors connexion ».

Note de réutilisation : un futur Manager pour un autre type (traductions, audio…) reproduit ce schéma (ViewModel + lignes + états) en consommant la même couche générique — la logique métier du Manager est un simple habillage de la couche Ressources.

#### 3.2.9 Intégration lecture (SurahDetailScreen)

- L'onglet « تفسير » affiche désormais le tafsir **sélectionné** (`TafsirReader`).
- Sélecteur de tafsir dans l'onglet : liste des tafsirs **installés simultanément** (muyassar toujours présent ; N tafsirs téléchargés coexistent sans aucune hypothèse de singleton dans le stockage) → **changement instantané** (fermeture/ouverture de base ~ms + cache). Un futur affichage côte à côte de deux tafsirs réutilise le même lecteur et le même stockage, sans refonte.
- Si le tafsir sélectionné est supprimé pendant la session → repli silencieux sur `muyassar`.
- L'onglet « ترجمة » (fr/en) reste branché sur l'ancien `TranslationRepository` — **aucun changement**.
- Boutons de la barre d'outils : « تفسير » sélectionne le tafsir courant ; « ترجمة » conserve l'ancien comportement.

#### 3.2.10 Observabilité

- Nouvelles catégories `LogCategory.TAFSIR` et `LogCategory.DOWNLOAD`.
- Logs (pattern `DebugLogger` + `Instrumentation.line`) : copie de l'asset, ouverture du lecteur (timing via `Timings`), téléchargement démarré/terminé/annulé/échoué (avec `ErrorCode` génériques dédiés, ex. `DOWNLOAD_FAILED`, `DOWNLOAD_INTEGRITY_MISMATCH`, `DOWNLOAD_NO_SPACE`), suppression, mise à jour.

---

## 4. Structure des dossiers (définitif)

```
<filesDir>/                          (stockage privé de l'application)
└── resources/                       (racine GÉNÉRIQUE — un sous-dossier par type)
    ├── tafsir/                      (type : tafsir)
    │   ├── muyassar.db              (copie de l'asset — embarqué, non supprimable)
    │   ├── ibn_kathir.db            (téléchargé)
    │   ├── saadi.db                 (téléchargé)
    │   ├── tabari.db                (téléchargé)
    │   └── .downloads/              (invisible — téléchargements en cours / reprise)
    │       ├── saadi.db.part        (fichier partiel, repris avec Range)
    │       └── saadi.db.part.meta   (JSON : type, id, version, url, bytesDone, total)
    ├── translation/                 (type futur : traductions — même mécanique)
    ├── audio/                       (type futur : audio — même mécanique)
    ├── index.json                   (état d'installation : type:id → version, taille, sha256)
    └── catalog-cache.json           (dernier catalogue distant récupéré)

<assetsDir>/
└── tafsir/
    └── muyassar.db                  (SEUL tafsir embarqué — compressé dans l'APK)
```

- **Rien d'autre** n'est stocké par ressource : un tafsir = un fichier `.db` + une entrée d'index (`tafsir:saadi`).
- Supprimer `saadi` ⇒ `saadi.db` + `saadi.db.part*` + entrée d'index supprimés ; `ibn_kathir.db` intact.
- L'ajout d'un type futur (ex. `dictionary/`) ne change rien à cette structure : un dossier par type.
- **Préparation Export / Import (future)** : une ressource est **autonome** — un seul fichier, dont le contenu et les métadonnées (`meta`) se suffisent à eux-mêmes ; l'entrée d'index est **recalculable** (version/langue/auteur depuis `meta`, taille et SHA-256 du fichier). Exporter = copier le fichier ; importer = copier + SHA-256 + relire `meta` + reconstruire l'index. **Aucun changement de structure requis** pour cette fonctionnalité future.

---

## 5. Modèle de données

```kotlin
// data/resource/ResourceModels.kt  (générique)

@Serializable
enum class ResourceType { TAFSIR, TRANSLATION, AUDIO, DICTIONARY, LEARNING_PACK }

@Serializable
data class ResourceMeta(
    val id: String,                 // "muyassar", "ibn_kathir", "saadi", ...
    val type: ResourceType,         // TAFSIR — filtre du module consommateur
    val name: String,               // "التفسير الميسر"
    val nameLatin: String,          // "Tafsir Al-Muyassar"
    val author: String,             // "مجموعة من العلماء"
    val license: String,            // licence du contenu, ex. "Public Domain", "CC BY 3.0", "CC BY-SA 4.0"
    val source: String,             // source de préparation (ex. "Shamela", "alquran.cloud", "QuranEnc") — JAMAIS un backend runtime (§13.2)
    val edition: String,            // édition de référence (ex. "دار طيبة")
    val publisher: String,          // éditeur / organisme émetteur (ex. "مجمع الملك فهد لطباعة المصحف الشريف")
    val website: String,            // site officiel / page de provenance
    val copyrightNotice: String,    // mention de droits — affichée dans l'écran « Sources & licences » (§13.4)
    val version: String,            // "1.0.0" — version du CONTENU
    val language: String,           // BCP 47 — "ar", "en", "fr", "id", "tr"… (aucune hypothèse d'arabe)
    val lastUpdated: String,        // ISO-8601 — date de publication de cette version
    val minAppVersion: String,      // version minimale de l'APP requise (ex. "1.4.0")
    val schemaVersion: Int = 1,     // schéma SQLite de la base (contrat §3.2.5)
    val downloadSizeBytes: Long,    // taille ESTIMÉE du fichier — affichée avant téléchargement (« ≈ 8,4 Mo »)
    val downloadUrl: String,        // {BASE}/resources/{type}/{id}/{version}.{ext}
    val sha256: String,             // intégrité
    val bundled: Boolean = false,   // true ⇔ embarqué, non supprimable
    val description: String? = null
)

@Serializable
data class ResourceCatalog(
    val schemaVersion: Int,         // 1
    val generatedAt: String,        // ISO-8601
    val resources: List<ResourceMeta>   // tous types confondus — le client filtre par type
)

enum class ResourceInstallState { NOT_INSTALLED, DOWNLOADING, INSTALLED, ERROR }

data class DownloadProgress(
    val resourceKey: String,        // "tafsir:saadi"
    val state: ResourceInstallState,
    val progress: Float,            // 0f..1f
    val bytesDone: Long,
    val bytesTotal: Long,
    val errorMessage: String? = null
)

data class ResourceListItem(        // vue Manager (générique)
    val meta: ResourceMeta,
    val state: ResourceInstallState,
    val progress: Float = 0f,
    val installedVersion: String? = null,
    val installedSizeBytes: Long = 0L,  // taille RÉELLE, issue de l'index
    val isSelected: Boolean = false,
    val updateAvailable: Boolean = false,
    val appUpdateRequired: Boolean = false,  // meta.minAppVersion > version de l'app → install bloquée
    val schemaSupported: Boolean = true      // meta.schemaVersion ∈ SUPPORTED_TAFSIR_SCHEMA_VERSIONS
)

@Serializable
data class ResourceIndex(
    val schemaVersion: Int = 1,
    val resources: Map<String, ResourceIndexEntry> = emptyMap()   // clé "type:id"
)

@Serializable
data class ResourceIndexEntry(
    val type: ResourceType,
    val version: String,
    val installedAt: Long,          // epoch ms
    val sizeBytes: Long,            // taille réelle du fichier installé
    val sha256: String,
    val schemaVersion: Int = 1,     // schéma de la base installée (vérifié à l'activation)
    val bundled: Boolean = false
)

@Serializable
data class ResumeMeta(              // .part.meta
    val type: ResourceType,
    val resourceId: String,
    val version: String,
    val url: String,
    val bytesDone: Long,
    val bytesTotal: Long
)
```

```kotlin
// data/tafsir/TafsirModels.kt  (spécifique tafsir — habillage du modèle générique)

typealias TafsirMeta = ResourceMeta                          // type = TAFSIR
data class TafsirListItem(
    val meta: TafsirMeta,
    val state: ResourceInstallState,
    val progress: Float = 0f,
    val installedVersion: String? = null,
    val installedSizeBytes: Long = 0L,
    val isSelected: Boolean = false,
    val updateAvailable: Boolean = false,
    val appUpdateRequired: Boolean = false,
    val schemaSupported: Boolean = true
)
```

---

## 6. API publique

### 6.1 Catalogue et fichiers (notre infrastructure)

| Endpoint | Méthode | Description |
|---|---|---|
| `{BASE}/resources/catalog.json` | GET | Catalogue global (JSON `ResourceCatalog`) — tous types de ressources. |
| `{BASE}/resources/{type}/{id}/{version}.{ext}` | GET | Fichier de la ressource (ex. `tafsir/muyassar/1.0.0.db`). **Doit supporter `Range`** (reprise). |

- `BASE` : `BuildConfig.RESOURCE_BASE_URL` (placeholder à définir par le propriétaire du projet — aucun hôte réel n'est codé en dur dans l'app).
- **Exigence d'infrastructure** : catalogue et fichiers sont servis par **notre propre infrastructure** (CDN / object storage statique). Aucune API tierce n'est appelée au runtime ; les données tierces (alquran.cloud, Tanzil…) ne servent qu'à la **génération build-time** des fichiers.
- En-têtes : `Content-Length` obligatoire (progression + contrôle espace), `Accept-Ranges` recommandé.

### 6.2 Application (interfaces Kotlin)

```kotlin
// Couche GÉNÉRIQUE — data/resource/

interface ResourceCatalogSource {
    suspend fun getCatalog(): ResourceCatalog                 // distant + embarqué fusionnés
}

interface ResourceDownloader {
    val progress: StateFlow<Map<String, DownloadProgress>>    // clés "type:id"
    suspend fun download(type: ResourceType, resourceId: String)   // reprend automatiquement si .part
    fun cancel(type: ResourceType, resourceId: String)         // conserve le .part
    suspend fun delete(type: ResourceType, resourceId: String) // libération immédiate
    suspend fun update(type: ResourceType, resourceId: String) // télécharge la nouvelle version, remplace
}

interface ResourceInstallationStore {
    suspend fun installedVersion(type: ResourceType, resourceId: String): String?
    fun isBundled(type: ResourceType, resourceId: String): Boolean
    suspend fun installedSize(type: ResourceType, resourceId: String): Long
    fun resourceFile(type: ResourceType, resourceId: String): File?
}

// Implémentations génériques :
// ResourceCatalogRepository · ResourceDownloadManager · ResourceIndexStore · ResourceFileStore

// Couche SPÉCIFIQUE TAFSIR — data/tafsir/

interface TafsirTextSource {
    suspend fun getText(tafsirId: String, verseKey: String): String?  // null = verset absent
}

interface TafsirSelection {
    fun selectedTafsirId(): String                                // défaut "muyassar"
    fun selectTafsir(tafsirId: String)
}

// TafsirReader (TafsirTextSource, singleton, LRU + base ouverte paresseuse)
// TafsirSelectionStore (TafsirSelection, QuranPreferences)
// TafsirManagerViewModel (filtre le catalogue sur type=TAFSIR, aggrège → StateFlow<List<TafsirListItem>>)
```

### 6.3 Comportements contractuels

- `download` alors qu'une reprise `.part` existe → reprend ; version du `.part` ≠ catalogue → purge + reprise propre.
- **`minAppVersion` > version installée de l'app** → téléchargement **refusé** (l'UI explique que l'application doit être mise à jour). Comparaison sémantique `major.minor.patch` (`BuildConfig.VERSION_NAME`).
- **`schemaVersion` (catalogue) ∉ `SUPPORTED_TAFSIR_SCHEMA_VERSIONS`** → téléchargement **refusé** ; à l'**activation** d'une base déjà présente, le schéma est re-vérifié (défense en profondeur) ; schéma inférieur au max → migration chaînée sur copie ; schéma inconnu → refus + repli `muyassar`.
- `delete` pendant `DOWNLOADING` → annule ET purge (suppression d'abord).
- `select` sur un tafsir non installé → refus (le lecteur retombe sur `muyassar`).
- Fichier `.db` corrompu à l'ouverture (SQLite `SQLITE_NOTADB`) → signalé, re-téléchargement possible.
- Extension du système à une nouvelle ressource : nouveau `ResourceType`, entrées `catalog.json`, fichiers hébergés — **aucun changement de la couche générique**.
- **Multi-installation** : aucune opération ne suppose un tafsir unique — l'index, le stockage et le lecteur traitent N ressources ; seule la sélection de lecture pointe sur un seul id.

---

## 7. Migration (sans rupture de lecture)

1. **Al-Muyassar embarqué** : le `.db` est généré **au build** (script `tools/tafsir/build_muyassar_db.py`) depuis une source de contenu choisie (ex. édition `ar.muyassar` d'alquran.cloud **ou données Tanzil**) — c'est une dépendance **build-time uniquement**, jamais runtime ; l'objectif est un contenu **identique à l'existant** pour éviter tout changement visible.
2. **Premier lancement** après mise à jour : rien ne se passe au démarrage (paresseux). À la première ouverture du tafsir : copie de l'asset → `files/resources/tafsir/muyassar.db`, écriture de l'index, et **suppression de l'ancien fichier** `filesDir/translation_ar.muyassar.json` (espace libéré ; plus jamais requis — hors-ligne garanti).
3. **Traductions** : `TranslationRepository` conserve `fr.hamidullah` et `en.sahih` (inchangées). Seule l'entrée `ar.muyassar` est retirée de `supportedEditions`. Les anciens fichiers `translation_fr.hamidullah.json` / `translation_en.sahih.json` restent gérés par l'ancien code (aucune migration).
4. **SurahDetailScreen** : l'onglet tafsir bascule sur `TafsirReader` + sélecteur de tafsirs installés ; l'onglet traduction ne bouge pas ; le rendu RTL existant est conservé (langue `ar` → `TextAlign.Right`).
5. **`TafsirManager.kt`** (code mort) : supprimé en Phase 1.
6. **Utilisateurs avec ancien téléchargement** de muyassar : ignorés (le contenu embarqué prend le relais), fichier nettoyé à la première copie réussie.
7. **Aucun état de lecture existant modifié** : `verseKey` inchangé, le chemin « sélection → lecture » reste identique, défaut = muyassar.

---

## 8. Performance & mémoire (exigences)

| Exigence | Mesure |
|---|---|
| Démarrage | **Zéro** travail tafsir au démarrage (pas d'ouverture de base, pas de copie d'asset, pas de fetch catalogue). |
| Premier affichage | Copie d'asset unique (~moins de 100 ms pour un .db typique) puis requêtes SQLite ponctuelles (~ms). |
| Changement de tafsir | Fermeture de la base précédente + ouverture paresseuse (~10–50 ms) ; cache LRU 256 entrées → réaffichage instantané. |
| Mémoire | **Une seule** base ouverte à la fois ; aucun chargement complet de fichier en mémoire (finis les JSON de 1 Mo parsés à chaque verset) ; cache borné. |
| Catalogue | Fetch uniquement à l'ouverture du Manager (5 s de timeout, non bloquant, cache disque). |

---

## 9. Règles d'extension future (Future Extension Rules)

Ces règles sont des **contraintes permanentes** de l'architecture. Toute évolution doit les respecter :

| # | Règle |
|---|---|
| 1 | **Tafsirs illimités** : le nombre de tafsirs n'a aucune limite en code — tout est piloté par le catalogue. |
| 2 | **Architecture pilotée par catalogue** : une ressource = une entrée `catalog.json` + un fichier hébergé. Aucune modification de code pour ajouter/retirer un tafsir. |
| 3 | **Téléchargements indépendants** : chaque ressource est téléchargée dans son propre fichier ; un téléchargement en cours n'affecte jamais un autre (file sérialisée, états disjoints). |
| 4 | **Suppression indépendante** : supprimer une ressource ne touche que son fichier + son entrée d'index (`type:id`) — jamais les autres. |
| 5 | **Mises à jour indépendantes** : un tafsir se met à jour seul ; les autres restent intacts (remplacement atomique du seul fichier concerné). |
| 6 | **Versionnement indépendant** : la version est une propriété de chaque ressource (catalogue + index), indépendante de la version de l'application. |
| 7 | **Vérification SHA-256** : tout téléchargement est vérifié avant installation ; en cas d'incohérence, aucun fichier n'est installé (purge + retry). |
| 8 | **Reprise des téléchargements** : toute interruption est reprise via `Range` + `.part` ; aucune donnée téléchargée n'est perdue hors cas d'erreur d'intégrité. |
| 9 | **Hors-ligne** : une ressource installée est lisible sans réseau ; le catalogue embarqué et le cache local garantissent le fonctionnement de l'UI. |
| 10 | **Al-Muyassar embarqué est permanent** : jamais supprimable, toujours disponible, toujours sélectionnable par défaut ; l'asset reste le repli même après une mise à jour téléchargée. |
| 11 | **Réutilisation de la couche de téléchargement** : les futurs types de ressources (traductions, audio, dictionnaires, packs d'apprentissage) réutilisent `ResourceDownloadManager` / `ResourceIndexStore` / `ResourceFileStore` **sans refonte** — seul leur consommateur (lecteur, UI) est spécifique. |
| 12 | **Multilingue** : les métadonnées (`language`, `author`, `license`, `lastUpdated`) rendent tout tafsir — arabe, anglais, français, indonésien, turc… — présentable et lisible sans changement d'architecture. |
| 13 | **Compatibilité applicative** : `minAppVersion` est une barrière de téléchargement ; une ressource trop récente pour l'app n'est jamais installée (message explicatif à l'utilisateur). |
| 14 | **Compatibilité de schéma** : `schemaVersion` est vérifié avant téléchargement **et** avant activation ; les migrations de schéma se font par table de migrations chaînées sans refonte ; aucune base non standard n'est acceptée. |
| 15 | **Schéma standard** : `meta` + `tafsir` (+ FTS optionnel) — aucun tafsir ne requiert de code personnalisé ; le lecteur unique fonctionne avec toutes les bases. |
| 16 | **Prêt pour Export / Import** : fichier unique + autonome + entrée d'index recalculable ⇒ la restauration sur un nouvel appareil est possible plus tard sans refonte du stockage. |
| 17 | **Multi-installation** : N tafsirs installés simultanément, stockage et index sans hypothèse de singleton — comparaison côte à côte ou changement instantané possibles à terme sans modifier la couche stockage. |
| 18 | **Provenance obligatoire** : toute entrée du catalogue porte `source`, `edition`, `publisher`, `author`, `license`, `website`, `copyrightNotice` ; une entrée incomplète est **refusée** dans le catalogue officiel (§13.1). |
| 19 | **Aucune API tierce au runtime** : l'application ne lit que notre infrastructure ; api.alquran.cloud, QuranEnc, quran.com ou toute API publique ne servent **jamais** de backend de production — uniquement de source de préparation des données (§13.2). |
| 20 | **Attribution obligatoire** : chaque tafsir affiche ses crédits complets (écran « Sources & licences », §13.4) ; aucune ressource à licence introuvable ou à provenance non traçable n'entre au catalogue. |
| 21 | **Suppression sécurisée de la ressource sélectionnée** : on ne supprime **jamais** la ressource courante sans avoir basculé d'abord la sélection vers un repli valide (Muyassar) ; les fichiers ne sont supprimés qu'**après** la bascule (§13.6). |

---

## 10. Roadmap

### Phase 1 — Fondations (embarqué + lecture)
- `assets/tafsir/muyassar.db` + script de génération `tools/tafsir/build_muyassar_db.py` (source build-time : édition `ar.muyassar` ou Tanzil — contenu identique à l'existant).
- Couche générique minimale : `ResourceFileStore` (copie paresseuse + auto-réparation), `ResourceIndexStore`, `ResourceCatalogRepository` (catalogue **embarqué** — le distant arrive en Phase 2).
- `TafsirReader` (SQLite + LRU + **vérification d'activation** : `meta['schema_version']` / `PRAGMA user_version` / `ResourceMeta.schemaVersion`, refus sur schéma inconnu), `TafsirSelectionStore` (+ clé `selected_tafsir_id`).
- Intégration SurahDetailScreen (onglet tafsir → lecteur, sélecteur des installés) ; retrait de `ar.muyassar` de `TranslationRepository` ; suppression de `TafsirManager`.
- `LogCategory.TAFSIR` + `DOWNLOAD` + logs + `ErrorCode` dédiés.
- **Livrable** : le tafsir fonctionne hors-ligne immédiatement après installation, même contenu qu'avant, aucune régression sur les traductions.

### Phase 2 — Téléchargements + Manager
- Infrastructure serveur : `catalog.json` + fichiers `.db` (notre CDN/object storage) ; serveur de test local pour le développement (ex. `python -m http.server`) — respecte `Range`.
- `ResourceDownloadManager` (progression, annulation, reprise `Range`, SHA-256 streaming, installation atomique, contrôle `StatFs`).
- `TafsirManagerScreen` + `TafsirManagerViewModel` + route `tafsir_manager` + entrée Paramètres.
- États `NOT_INSTALLED / DOWNLOADING / INSTALLED / ERROR` ; actions télécharger / annuler / supprimer / sélectionner ; affichage de la **taille estimée avant téléchargement** ; cache du catalogue hors-ligne ; bannière hors connexion.
- **Livrable** : télécharger/annuler/supprimer/retélécharger un tafsir (validé sur émulateur avec notre serveur de test), espace libéré immédiatement à la suppression, lecture hors-ligne.

### Phase 3 — Mises à jour & finitions
- Détection de version (catalogue vs index) → bouton « Mettre à jour » par tafsir ; mise à jour individuelle sans mise à jour d'application ; Al-Muyassar mis à jour = téléchargement (jamais supprimable, l'asset reste le repli).
- Travail en arrière-plan optionnel (WorkManager) pour terminer/relancer un téléchargement interrompu (connectivité).
- **Écran « Sources & licences »** (Paramètres → À propos) : affiche nom, auteur, source, éditeur, licence, site officiel et version de chaque tafsir, à partir des champs de provenance du catalogue et du `meta` des bases installées (§13.4).
- Optionnel : migration des traductions fr/en vers le même moteur (nouveau `ResourceType.TRANSLATION`) ; recherche plein texte dans un tafsir (FTS5) ; tests unitaires (reprise, intégrité, suppression) ; métriques de stockage par tafsir dans le Manager.

---

## 11. Critères d'acceptation (vérification)

1. APK installé → « تفسير » affiche Al-Muyassar **sans internet**, contenus identiques à l'existant.
2. Al-Muyassar : aucun bouton Supprimer ; toujours sélectionnable ; badge « مدمج ».
3. Téléchargement d'un tafsir : progression visible ; **taille estimée affichée avant téléchargement** ; annulation → reprise au même pourcentage ; suppression → espace libre immédiat et autres tafsirs intacts.
4. `.db` tronqué pendant le téléchargement → échec d'intégrité signalé, aucun fichier corrompu installé.
5. Version supérieure au catalogue → bouton Mettre à jour ; seul ce tafsir est remplacé.
6. Hors-ligne total après installation (avion) : lecture + Manager fonctionnels.
7. Ajout d'un tafsir : uniquement une entrée `catalog.json` + un fichier `.db` côté serveur — aucune modification de l'app.
8. Démarrage de l'app : aucune opération tafsir au premier plan visible (log : rien au démarrage ; premier travail à la première lecture).
9. **Aucun appel runtime à une API tierce** : le catalogue et les fichiers proviennent exclusivement de notre infrastructure (vérifiable dans les logs réseau / avion).
10. **Blocage `minAppVersion`** : un tafsir déclarant une version d'app supérieure à l'installée n'offre aucun bouton Télécharger et affiche le message « mise à jour de l'application requise ».
11. **Blocage `schemaVersion`** : une base au schéma inconnu est refusée à l'activation (repli `muyassar`, log `TAFSIR_SCHEMA_UNSUPPORTED`) ; une base au schéma inférieur est migrée automatiquement.
12. **Multi-installation** : 2+ tafsirs installés simultanément, sélection indépendante, suppression de l'un sans effet sur l'autre.
13. **Tafsir non arabe** : un tafsir `language=en|fr` s'affiche (métadonnées et texte) sans changement de code.
14. **Provenance** : chaque entrée du catalogue porte les 7 champs de provenance (§13.1) ; le catalogue officiel ne contient aucune entrée incomplète.
15. **Écran « Sources & licences »** : accessible hors-ligne via Paramètres → À propos ; affiche pour chaque tafsir nom, auteur, source, éditeur, licence, site officiel et version (catalogue + `meta` de la base installée).

---

## 12. Future Ideas (hors périmètre — n'altère pas l'architecture §1–11)

> L'architecture est **gelée**. Les idées ci-dessous sont des extensions possibles qui **ne modifient pas le cœur du système** ; si l'une d'elles l'exigeait, la justification et la proposition de modification seraient d'abord documentées ici.

- **Comparaison côte à côte** de deux tafsirs (lecture simultanée) — le stockage et le lecteur le permettent déjà (§3.2.6, règle 17) ; seule l'UI est à créer.
- **Export / Import des ressources** (sauvegarde locale ou dans le cloud, restauration sur un autre appareil sans re-téléchargement) — prérequis garantis : fichier autonome + index recalculable (§4, règle 16).
- **Recherche plein texte** dans un tafsir (FTS5) — les bases pourront embarquer une table `tafsir_fts` optionnelle (§3.2.5) ; l'app proposera la recherche quand elle est présente.
- **Téléchargements en arrière-plan** (WorkManager) pour poursuivre/relancer un téléchargement après interruption ou changement de connectivité.
- **Migration des traductions fr/en** vers la couche générique (nouveau `ResourceType.TRANSLATION`) — le moteur est déjà prêt.
- **Métriques de stockage** : total occupé par type, alerte d'espace, tri du Manager par taille.
- **Vérification périodique de version** (au démarrage du Manager seulement — jamais en arrière-plan) avec badge « mise à jour disponible ».
- **Notifications de mise à jour** des tafsirs installés (optionnelle, silencieuse).
- **Tests unitaires et d'intégration** : reprise, intégrité, migration de schéma, suppression, comportements hors-ligne.

---

## 13. Politique officielle des sources, licences et attributions (gelée — v2)

> Ces règles sont **officielles et permanentes** (gel d'architecture v2). Elles complètent les sections 1–11
> et priment sur toute idée de §12 en cas de conflit.

### 13.1 Provenance obligatoire (catalogue)

- Chaque ressource de `catalog.json` porte **obligatoirement** : `source`, `edition`, `publisher`, `author`, `license`, `website`, `copyrightNotice` (§3.2.4, modèle `ResourceMeta` §5).
- Ces métadonnées ne sont pas seulement documentaires : elles font partie de l'architecture officielle — elles alimentent l'écran « Sources & licences » (§13.4) et sont répliquées dans la table `meta` des bases (§3.2.5).
- **Règle de validité** : une entrée de catalogue sans provenance complète est **invalide** — elle est rejetée lors de la préparation des ressources et n'entre jamais dans le catalogue officiel.
- Les bases SQLite répliquent ces champs dans `meta` (auto-description du fichier, §3.2.5) : `source`, `edition`, `publisher`, `website`, `copyright_notice`.

### 13.2 Politique des sources — aucune API tierce au runtime

L'application **ne dépendra jamais d'API tierces au runtime**. Architecture officielle :

1. les textes sont récupérés **une seule fois** depuis une source autorisée (préparation des données) ;
2. ils sont **convertis en SQLite** pendant la préparation des ressources (outillage `tools/tafsir/*`, build-time) ;
3. ils sont **hébergés sur notre propre infrastructure** (`catalog.json` + fichiers `.db`) ;
4. l'application **télécharge uniquement depuis notre catalogue** (jamais ailleurs).

**Aucune lecture directe au runtime depuis** :

- `api.alquran.cloud`
- QuranEnc
- quran.com
- ou toute autre API publique

**Statut des services tiers** : **source de préparation des données uniquement** — jamais un backend de production (vérifiable : critère d'acceptation n°9, §11).

### 13.3 Classification officielle des tafsirs

| Catégorie | Tafsirs | Règles |
|---|---|---|
| **Bundled** (embarqué) | التفسير الميسر | Toujours présent, **jamais supprimable** (règle 10, §9). |
| **Téléchargeables — Tier A** | ابن كثير · الطبري · القرطبي · البغوي · فتح القدير | Textes classiques à licence libre / domaine public ; disponibles au catalogue dès la Phase 2. |
| **Téléchargeables — Tier B** | السعدي · التحرير والتنوير · أضواء البيان | Attribution obligatoire ; usage non commercial par défaut ; mention de droits affichée (§13.4). |
| **Permission requise** | المختصر في التفسير | N'entre au catalogue **qu'après accord écrit** du titulaire des droits (مركز تفسير للدراسات القرآنية, tafsir.net). |

- Toute évolution de cette classification se documente ici (même procédure que le gel v1).

### 13.4 Écran « Sources et licences » (fonctionnalité documentée — non implémentée)

- **Navigation prévue** : Paramètres → À propos → **Sources & licences**.
- **Contenu** : pour chaque tafsir (embarqué et téléchargés) :

| Champ | Source de données |
|---|---|
| Nom | Catalogue (`name` / `nameLatin`) + `meta['name']` |
| Auteur | `author` |
| Source | `source` |
| Éditeur | `publisher` |
| Licence | `license` |
| Site officiel | `website` |
| Version | `version` (catalogue / index) |

- L'écran est accessible **hors-ligne** (catalogue embarqué + cache + `meta` des bases installées).
- Aucune implémentation à ce stade — cette section décrit l'architecture future, figée.

### 13.5 Cycle de vie d'une ressource (Resource Lifecycle)

Toute ressource (tafsir aujourd'hui ; traductions, audio, dictionnaires demain) suit **exactement le même cycle de vie**, indépendamment de son type :

```
Catalog
   ↓
Download
   ↓
Verify SHA-256
   ↓
Install
   ↓
Index
   ↓
Available
   ↓
Selected
   ↓
In Use
   ↓
Delete
   ↓
Fallback (Muyassar)
```

**États & transitions (détaillés) :**

| Étape / État | Ce qui se passe |
|---|---|
| **Catalog** | La ressource est découverte dans `catalog.json` (distant fusionné avec l'embarqué) ; entrée visible dans le Manager (taille estimée, version, provenance, `minAppVersion`, `schemaVersion`). |
| **Download** | Démarrage du téléchargement depuis **notre infrastructure** (jamais une API tierce, §13.2) ; contrôle `StatFs` de l'espace disque avant le démarrage ; un seul téléchargement à la fois (file sérialisée). |
| **Downloading** | État `DOWNLOADING` ; progression publiée (`DownloadProgress`) ; écriture dans `.downloads/<id>.part` + `.part.meta` ; SHA-256 calculé **en continu** (MessageDigest streaming). |
| **Paused / Resume** | Annulation = pause : `.part` et `.meta` sont **conservés** ; un nouvel appel `download` reprend via `Range: bytes=<done>-` (serveur ignorant `Range` → reprise à zéro, §3.2.2). |
| **Verify SHA-256** | Fin du téléchargement : comparaison du hachage streaming avec `sha256` du catalogue ; **échec → purge du `.part`, état `Failed`, bouton Réessayer** — aucun fichier corrompu n'est jamais installé. |
| **Install** | Installation atomique : `.part` vérifié → `renameTo("<id>.<ext>")` → écriture de l'index → mise à jour du StateFlow. |
| **Index** | Entrée `type:id` écrite dans `index.json` (version, taille réelle, sha256, `schemaVersion`, `bundled`) — seul point de vérité de l'installation (§3.2.3). |
| **Available** | État `INSTALLED` ; ressource sélectionnable et **lisible hors-ligne** ; version installée et taille réelle affichées. |
| **Selected** | `TafsirSelectionStore` pointe sur cet id (`selected_tafsir_id`) ; l'onglet tafsir lit cette base. |
| **In Use** | Base ouverte en lecture par `TafsirReader` (lecture seule, cache LRU) — une seule base ouverte à la fois. |
| **Delete** | Suit la règle §13.6 : **bascule de sélection vers un repli valide d'abord**, suppression des fichiers ensuite (`.db` + `.part*` + entrée d'index) ; libération immédiate de l'espace. |
| **Fallback (Muyassar)** | Repli permanent : Al-Muyassar (embarqué, jamais supprimable) reprend la sélection ; utilisé aussi en cas de base corrompue (`SQLITE_NOTADB`) ou de schéma inconnu. |
| **Failed** | État `ERROR` : échec d'intégrité, erreur réseau, espace insuffisant — message dédié, jamais d'installation partielle. |
| **Corrupted** | Base illisible / SHA-256 incohérent à l'activation → désactivation, repli `muyassar`, re-téléchargement possible. |
| **Unsupported Version** | `minAppVersion` > version de l'app → téléchargement **refusé** (message « mise à jour de l'application requise ») ; `schemaVersion` inconnu → refus au téléchargement **et** à l'activation (repli `muyassar`, log `TAFSIR_SCHEMA_UNSUPPORTED`). |
| **Update Available** | `version` catalogue > `version` index → bouton « Mettre à jour » (téléchargement de la nouvelle version ; remplacement atomique du seul fichier concerné). |
| **Updating** | Même mécanique que Download → Verify → Install, sur la nouvelle version ; la ressource reste installée jusqu'au remplacement réussi. |
| **Deleted** | État terminal : plus de fichier, plus d'entrée d'index ; l'entrée catalogue redevient `NOT_INSTALLED`. |

Règle de réutilisabilité : tout futur type de ressource (audio, traductions, dictionnaires…) suit **ce même cycle** — seul le consommateur (lecteur / UI) diffère.

### 13.6 Règle de suppression de la ressource sélectionnée

> **Never delete the currently selected resource without first switching the application to a valid fallback resource.**

Séquence obligatoire (l'ordre protège contre les crashes et les edge cases) :

```
Current = Ibn Kathir

Delete
   ↓
La sélection bascule automatiquement sur Muyassar (repli valide, toujours présent)
   ↓
Suppression des fichiers (db + .part* + entrée d'index)
```

- La bascule vers le repli précède **toujours** la suppression des fichiers.
- Si aucun repli valide n'existe (impossible en pratique : Muyassar est embarqué et non supprimable, règle 10 §9), la suppression est **refusée**.
- S'applique à tout type de ressource future : une traduction / un audio / un dictionnaire sélectionnés ne peuvent pas non plus être supprimés sans bascule préalable.
