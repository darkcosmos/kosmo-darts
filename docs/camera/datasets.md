# Public dataset candidates (camera phase 0b)

Ces jeux de données publics peuvent compléter les photos de Kosmo pour l'entraînement du détecteur. Ils ne servent
qu'après votre vérification de la licence (étape K4 de `docs/plans/42-camera-feasibility-0b-1.md`). Les outils
fonctionnent sans aucun jeu public.

## Acceptance rule

- Seule la licence CC BY 4.0 est acceptée.
- Vous vérifiez la licence vous-même sur la page source du jeu, puis vous notez la mention d'attribution exigée.
- Sont exclus : les licences AGPL, les licences non commerciales (NC), les jeux sans licence, et le jeu DeepDarts.
- Un jeu n'est téléchargé qu'une fois accepté.

## Sets

Une ligne par jeu. Le dossier de `~/kosmo-data/public/<slug>/` porte le nom du `slug`. Statuts : `accepted` (utilisé tel
quel), `conditional` (utilisable seulement par la méthode du motif) et `rejected` (jamais ouvert, sans entrée dans la
table de correspondance).

| slug (dossier) | jeu | lien | licence | attribution | statut | motif |
|---|---|---|---|---|---|---|

## How a set is added

1. Vous donnez dans le chat le lien de la page du jeu, et sa licence telle que vous la lisez (avec la date où vous avez
   vu la page).
2. Le squad ajoute une ligne dans le tableau ci-dessus : licence au format `CC BY 4.0 (page vue par Kosmo le AAAA-MM-JJ)`,
   attribution `<jeu> par <auteur>, <lien>, CC BY 4.0 ; modifié : ...`, statut et motif.
3. Pour un jeu `accepted` ou `conditional`, le squad ajoute son entrée dans `tools/camera/config/public-class-maps.json`
   (source name vers cible `tip`, `whole-dart-checked`, `pseudo-tip-box`, `ignore` ou `drop-image`). Un jeu avec une cible
   `pseudo-tip-box` est `conditional`, tout autre est `accepted`. Un jeu `rejected` n'a aucune entrée.
