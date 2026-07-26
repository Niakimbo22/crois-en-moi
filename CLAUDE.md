# Crois en Moi — consignes de travail

## Déploiement : toujours sur `main`, sans le demander

Toute modification de l'application se commite et se pousse **directement sur
`main`**, sans passer par une branche de travail ni une pull request, et sans
demander confirmation au préalable.

`main` est la branche de production : c'est le seul endroit où le workflow
`.github/workflows/build-apk.yml` se déclenche. Une modification qui n'y est pas
n'atteint jamais le téléphone de l'utilisateur, quel que soit le soin apporté au
code. Pousser fait donc partie de la tâche, au même titre que l'écrire.

- Ne pas créer de pull request, sauf demande explicite.
- Ne pas demander « veux-tu que je pousse ? » : la réponse est oui.
- Ne pas s'arrêter à un commit local : `git push -u origin main`.

## Ne jamais toucher au numéro de version à la main

`version.json`, la constante `APP_VERSION` dans `index.html` et le pied de page
« Crois en Moi — Version X » sont incrémentés **automatiquement par la CI** à
chaque push modifiant `index.html`, `bible/` ou `android/`. Les modifier à la
main provoque un conflit au push suivant.

## Une tâche est finie quand

1. le changement est sur `main` chez `origin` ;
2. le workflow « Build APK Android » est passé au vert.

Tant que le workflow n'est pas vert, la tâche n'est pas terminée : il faut lire
les journaux, corriger et repousser.

## Structure

- `index.html` — l'application entière (interface, prières, chants, lecteur biblique)
- `bible/` — le texte biblique en JavaScript, un fichier par livre (voir README)
- `android/` — l'enveloppe Android : une WebView plus la mise à jour automatique
- `version.json` — numéro publié, lu par l'application pour se mettre à jour
