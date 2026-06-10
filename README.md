# SkyBlockEvent

[![Build](https://github.com/asuuna/SkyBlockEvent/actions/workflows/build.yml/badge.svg)](https://github.com/asuuna/SkyBlockEvent/actions/workflows/build.yml)

Plugin Java custom pour gerer des evenements serveur inedits sur un SkyBlock: rotation automatique, objets d'event custom, combos de score, milestones serveur/joueur, classements, historique, boosts de drops et recompenses par commandes console.

Createur, auteur et mainteneur: Shirito.

Ce depot contient le code source complet du plugin. L'objectif est de garder un plugin lisible, configurable et maintenable, avec des choix techniques simples a verifier: pas de dependances inutiles, pas d'I/O lourde sur le thread principal, et une separation claire entre configuration, logique d'event, stockage et presentation des messages.

## Auteur

SkyBlockEvent est un projet de Shirito. Le code source, la maintenance et les choix de gameplay de ce depot sont rattaches a Shirito.

## Licence

SkyBlockEvent appartient a Shirito. Le code est publie sous licence proprietaire `All rights reserved`: lecture, compilation et usage serveur personnel autorises, mais redistribution, revente, re-upload, suppression de l'attribution ou revendication d'auteur interdites sans autorisation explicite de Shirito.

## Etat du projet

- Version actuelle: `1.0.0`.
- Branche principale: `main`.
- Build Maven verifie avec Java 17.
- Tests unitaires presents pour la logique de score et le formatage de temps.
- Jar genere localement avec `mvn clean package`.

## Compatibilite

- Java 17 minimum.
- Compile contre Spigot API `1.20.4-R0.1-SNAPSHOT`.
- Cible runtime realiste: Paper, Spigot et Bukkit 1.20.x+.

Le plugin n'integre volontairement aucune API d'ile SkyBlock specifique. Il fonctionne donc sans BentoBox, SuperiorSkyblock, IridiumSkyblock, etc. Une integration d'iles peut etre ajoutee plus tard via adapter dedie.

## Build

```bash
mvn clean package
```

Le jar est genere dans `target/SkyBlockEvent-1.0.0.jar`.

## Installation

1. Placer le jar dans `plugins/`.
2. Demarrer le serveur une premiere fois.
3. Modifier `plugins/SkyBlockEvent/config.yml` et `messages.yml`.
4. Recharger avec `/sbevent reload` ou redemarrer.

## Notes de maintenance

- Les changements de configuration doivent rester documentes dans ce README.
- Les nouvelles commandes doivent etre ajoutees dans `plugin.yml`, `messages.yml` et la section Commandes.
- Les nouvelles mecaniques d'event doivent rester configurables et testables sans imposer un plugin SkyBlock tiers.
- Avant une mise en production, lancer `mvn clean package` puis tester le jar sur un serveur de preproduction.

## Commandes

| Commande | Permission | Description |
| --- | --- | --- |
| `/sbevent help` | `skyblockevent.command` | Affiche l'aide |
| `/sbevent status` | `skyblockevent.command` | Affiche l'evenement actif |
| `/sbevent list` | `skyblockevent.command` | Liste les evenements configures |
| `/sbevent top` | `skyblockevent.command` | Affiche le classement actuel |
| `/sbevent history [limite]` | `skyblockevent.command` | Affiche les derniers evenements termines |
| `/sbevent start <id> [secondes]` | `skyblockevent.admin` | Lance un evenement |
| `/sbevent stop [raison]` | `skyblockevent.admin` | Arrete l'evenement actif |
| `/sbevent reload` | `skyblockevent.admin` | Recharge la configuration |

Alias: `/skyblockevent`, `/sbe`.

## Permissions

| Permission | Defaut | Description |
| --- | --- | --- |
| `skyblockevent.command` | true | Lecture des commandes publiques |
| `skyblockevent.participate` | true | Participation et score |
| `skyblockevent.notify` | true | Reception des annonces |
| `skyblockevent.admin` | op | Gestion complete |

## Types d'evenements

- `COMET_SHOWER`: cassage de blocs qui peut faire tomber des eclats de comete custom a ramasser.
- `RIFT_HARVEST`: recolte de cultures matures avec graines de faille custom.
- `RELIC_HUNT`: mobs tues qui peuvent liberer des reliques instables custom.
- `MINING`: score sur cassage de blocs configures, boost de drops.
- `FARMING`: score sur recolte de cultures matures, boost de drops.
- `MOB_HUNT`: score sur mobs tues par un joueur, boost de drops de mobs.
- `FISHING`: score sur peche reussie, boost de l'item peche.
- `DOUBLE_DROPS`: boost de drops sur blocs configures.

Les events par defaut utilisent les types custom (`comet-shower`, `rift-harvest`, `relic-hunt`, `abyssal-cache`, `ether-overload`) plutot que de simples copies de plugins d'event classiques.

## Configuration rapide

Les evenements sont declares dans `config.yml` sous `events.<id>`.

Champs principaux:

- `enabled`: active ou non l'evenement.
- `display-name`: nom affiche.
- `type`: `COMET_SHOWER`, `RIFT_HARVEST`, `RELIC_HUNT`, `MINING`, `FARMING`, `MOB_HUNT`, `FISHING`, `DOUBLE_DROPS`.
- `duration-seconds`: duree par defaut.
- `target-score`: objectif global. `0` desactive l'arret par objectif.
- `points-per-action`: points gagnes par action valide. `0` permet un event base uniquement sur objets custom ramasses.
- `drop-multiplier`: multiplicateur de drops. `1.0` desactive le boost.
- `materials`: materiaux ou categories (`#ores`, `#crops`, `#logs`, `#skyblock_blocks`).
- `custom-drop`: item custom marque par metadata, chance d'apparition et score au ramassage.
- `custom-drop.despawn-seconds`: duree avant suppression automatique d'un item custom non ramasse. `0` desactive l'expiration forcee.
- `combo`: fenetre de combo, paliers, multiplicateur max et actionbar.
- `milestones.server`: paliers globaux avec message et commandes.
- `milestones.personal`: paliers par joueur avec message et commandes.
- `rewards.top`: commandes par rang.
- `rewards.participation`: recompense des joueurs au-dessus d'un score minimal.

Placeholders de recompenses:

- `%player%`
- `%uuid%`
- `%score%`
- `%rank%`
- `%event%`

Placeholders de milestones:

- `%player%`
- `%uuid%`
- `%event%`
- `%id%`
- `%type%`
- `%points%`
- `%score%`
- `%total%`
- `%streak%`
- `%multiplier%`
- `%threshold%`

## Donnees

Le plugin ecrit `plugins/SkyBlockEvent/data/events.yml` de maniere asynchrone:

- historique des derniers evenements;
- snapshot de l'evenement actif avec fin prevue pour restauration au redemarrage;
- scores et participants.

## Limites connues

- Les recompenses et milestones sont des commandes console: leur comportement depend aussi des plugins appeles par ces commandes.
- Sans API SkyBlock externe, le plugin ne filtre pas par ile ou membre d'ile.
- Les tests automatiques valident la compilation et la logique Java disponible; un test serveur manuel reste necessaire pour confirmer les interactions avec les plugins de votre stack.

## Support

Les bugs et demandes d'amelioration doivent etre ouverts dans les issues GitHub avec la version serveur, la version Java, la version du plugin et les logs utiles. Shirito reste le point de reference pour les decisions de gameplay et de configuration.
