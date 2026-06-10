# Changelog

Toutes les modifications notables de SkyBlockEvent sont documentees ici.

## [1.0.0] - 2026-06-10

### Ajoute

- Base complete du plugin SkyBlockEvent.
- Commande `/sbevent` avec `help`, `status`, `list`, `top`, `history`, `start`, `stop` et `reload`.
- Events serveur custom: cometes, recoltes de faille, reliques, cache abyssal et surcharge ether.
- Score serveur, score joueur, combos, milestones et classement.
- Recompenses configurables par rang et participation.
- Stockage YAML asynchrone avec historique.
- Configuration et messages modifiables.
- Tests unitaires Maven/JUnit.
- Clarification de l'auteur du projet dans le depot: SkyBlockEvent est cree et maintenu par Shirito.
- Expiration configurable des objets d'event custom non ramasses.
- Nettoyage interne des handles de taches Bukkit ponctuelles pour eviter leur accumulation pendant les broadcasts, sauvegardes et recompenses.

### Notes

- Projet cree et maintenu par Shirito.
- Compatibilite cible: Paper, Spigot et Bukkit 1.20.x+.
- Une validation serveur manuelle reste necessaire avant de publier une release en production.
