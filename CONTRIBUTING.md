# Contribuer

SkyBlockEvent est maintenu par Shirito.

## Regles de contribution

- Garder le code en Java uniquement.
- Ne pas ajouter de dependance sans justification claire.
- Ne pas bloquer le thread principal avec de l'I/O, du reseau ou un calcul lourd.
- Garder les messages configurables quand ils sont visibles par les joueurs.
- Documenter toute nouvelle commande, permission ou option de configuration.
- Ajouter ou adapter les tests quand une logique metier change.

## Verification locale

```bash
mvn clean package
```

Le jar doit etre genere dans `target/` sans erreur de compilation.

## Style

- Code simple et explicite.
- Classes separees par responsabilite.
- Pas de refactor massif sans besoin direct.
- Les changements gameplay doivent rester configurables depuis `config.yml`.
