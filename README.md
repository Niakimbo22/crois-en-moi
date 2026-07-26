# Crois en Moi

Application catholique francophone (web + APK Android), utilisable entièrement hors ligne.

## Rubriques

- **Accueil** — calendrier liturgique, saint du jour, verset du jour (tiré de la Bible embarquée)
- **Lecture — La Bible** — Bible intégrale, Évangiles, recherche de versets, signets
- **Chapelet guidé** — les quatre séries de mystères, pas à pas
- **Bibliothèque de prières** — prières fondamentales, mariales, pénitentielles
- **Chants de messe** — Ordinaire de la messe avec paroles et commentaires
- **La Trinité** et **Confession** — catéchèse et examen de conscience

## La rubrique Lecture

Le texte biblique est stocké dans `bible/`, un fichier par livre plus un
`index.js` décrivant les livres et le nombre de versets par chapitre. Les
fichiers sont copiés dans les assets Android au moment du build, donc la lecture
et la recherche fonctionnent sans aucune connexion.

Ces fichiers sont du JavaScript (`CEM_BIBLE("jn", [...])`) chargé par balise
`<script>`, et non du JSON : une page ouverte depuis `file:///android_asset/`
ne peut utiliser ni `fetch` ni `XMLHttpRequest`, tous deux bloqués par la
politique d'origine. Le chargement par script est le seul qui fonctionne à la
fois dans l'application et sur le web.

- Lecteur par chapitre, navigation continue d'un livre à l'autre, taille de texte réglable
- Recherche plein texte insensible à la casse et aux accents, avec surlignage
- Saisie directe d'une référence : `Jn 3:16`, `Matthieu 5 3`, `1 Co 13`, `Psaume 23`
- Signets, reprise de la lecture là où elle s'est arrêtée (stockage local)
- Parcours de lecture continu des quatre Évangiles, un chapitre par jour

### Texte utilisé

Traduction **Ostervald (1996)**, dans le domaine public : 66 livres, 1 189
chapitres, 31 172 versets. Les livres deutérocanoniques du canon catholique
(Tobie, Judith, Sagesse, Siracide, Baruch, 1 et 2 Maccabées) ne figurent pas
dans cette édition ; ils pourront être ajoutés si une source française libre
les proposant est trouvée.

## Build

Le workflow `.github/workflows/build-apk.yml` se déclenche à chaque push sur
`main`. Il incrémente la version, copie `index.html` et `bible/` dans
`android/app/src/main/assets/`, produit l'APK et publie la release `latest`.

## Mise à jour automatique

Rien n'est à faire pour publier une nouvelle version : il suffit de pousser sur
`main`.

**Numérotation.** Dès qu'un push touche `index.html`, `bible/` ou `android/`, la
CI incrémente `version.json` ainsi que la constante `APP_VERSION` et le pied de
page dans `index.html`, puis recommite le tout. Le numéro n'est donc jamais à
modifier à la main — c'est lui qui signale la nouveauté aux applications
installées, et l'oublier revient à ne rien publier du tout.

**Application Android.** `MainActivity` compare sa version à `version.json` au
lancement, à chaque retour au premier plan et toutes les demi-heures. Quand une
version plus récente existe, elle télécharge l'APK et l'installe. À partir
d'Android 12, une application qui se met à jour elle-même peut demander à ce que
la confirmation soit omise (`USER_ACTION_NOT_REQUIRED`) : l'installation se fait
alors sans aucune intervention. Sur les versions antérieures, le système impose
sa fenêtre de confirmation, affichée aussitôt.

**Version web.** La page interroge `version.json` toutes les cinq minutes et à
chaque retour d'onglet, puis se recharge d'elle-même sur la nouvelle version.
L'adresse porte le numéro publié, ce qui écarte tout cache. Le rechargement est
différé au retour de l'utilisateur pour ne pas couper une lecture en cours.
