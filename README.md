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
- Recherche mot à mot, insensible à la casse et aux accents, avec surlignage :
  chaque mot est cherché **en entier** (« aime » ne ramène pas « aimerai », ni
  « Jean » les versets sur « Jeanne »). Une `"expression entre guillemets"` est
  cherchée telle quelle, `aim*` retrouve toutes les formes d'un mot, et le filtre
  « Contient » revient à la recherche par morceaux de mots
- Saisie directe d'une référence, en français comme en anglais : `Jn 3:16`,
  `John 3:16`, `Matthieu 5 3`, `1 Co 13,4-7`, `I Corinthiens 13`, `Psaume 23`.
  Le verset demandé s'affiche directement dans les résultats ; un chapitre seul
  ouvre le lecteur
- Signets, reprise de la lecture là où elle s'est arrêtée (stockage local)
- Parcours de lecture continu des quatre Évangiles, un chapitre par jour

### Texte utilisé

Traduction **Ostervald (1996)**, dans le domaine public : 66 livres, 1 189
chapitres, 31 172 versets. Les livres deutérocanoniques du canon catholique
(Tobie, Judith, Sagesse, Siracide, Baruch, 1 et 2 Maccabées) ne figurent pas
dans cette édition ; ils pourront être ajoutés si une source française libre
les proposant est trouvée.

## Build

Le workflow `.github/workflows/build-apk.yml` copie `index.html` et `bible/`
dans `android/app/src/main/assets/`, puis produit l'APK. Le fichier
`version.json` sert à proposer la mise à jour dans l'application.
