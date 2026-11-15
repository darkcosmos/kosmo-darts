# Public dataset candidates (camera phase 0b)

Ces jeux de données publics peuvent compléter les photos de Kosmo pour l'entraînement du détecteur. Ils ne servent
qu'après votre vérification de la licence (étape K4 de `docs/plans/42-camera-feasibility-0b-1.md`). Les outils
fonctionnent sans aucun jeu public.

## Acceptance rule

- Seule la licence CC BY 4.0 est acceptée.
- Vous vérifiez la licence vous-même sur la page source du jeu, puis vous notez la mention d'attribution exigée.
- Sont exclus : les licences AGPL, les licences non commerciales (NC), les jeux sans licence, et le jeu DeepDarts.
- Un jeu n'est téléchargé qu'une fois accepté.

## Candidates

| set | link | licence | attribution | status |
|---|---|---|---|---|
| Recherche « dartboard » (Roboflow Universe) | https://universe.roboflow.com/search?q=dartboard | à vérifier | à relever | to check |
| Recherche « dart » (Hugging Face) | https://huggingface.co/datasets?search=dart | à vérifier | à relever | to check |
| Recherche « dartboard » (Kaggle) | https://www.kaggle.com/datasets?search=dartboard | à vérifier | à relever | to check |
| Recherche « dartboard » (Zenodo) | https://zenodo.org/search?q=dartboard | à vérifier | à relever | to check |

Les lignes ci-dessus sont des points d'entrée de recherche, pas des jeux. Chaque jeu retenu aura sa propre ligne avec
son lien direct, sa licence exacte, son attribution et son statut (`to check`, `accepted` ou `rejected`).

## How a set is added

1. Vous donnez dans le chat le lien de la page du jeu, et sa licence telle que vous la lisez.
2. Le squad ajoute une ligne dans ce tableau avec le statut `to check`, puis la passe à `accepted` ou `rejected` après
   votre réponse.
3. Pour un jeu accepté, le squad ajoute la table de correspondance des classes utilisée par le constructeur de
   jeu de données (tâche T9).
