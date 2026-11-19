# Kosmo camera tooling: guide pour Kosmo (phase 0b)

Ce guide décrit les commandes que Kosmo lance sur son PC (WSL2 et station GPU). Les commandes et les noms sont en
anglais, le texte est en français. Les règles citées (CVn) sont dans `docs/rules/camera.md`. Les étapes K1 à K6 sont
dans la section « How Kosmo verifies » du plan `docs/plans/42-camera-feasibility-0b-1.md`.

## Data layout

- `~/kosmo-data/captures/<session>/` : les sessions de capture existantes (lecture seule pour les outils).
- `~/kosmo-data/captures-eval/<session>/` : les sessions de la campagne de confirmation, si elle est demandée plus tard
  (CV17). Ces sessions sont toujours en évaluation.
- `~/kosmo-data/public/<set>/` : les jeux de données publics retenus (voir `docs/camera/datasets.md`).
- `~/kosmo-data/annotations/` : les fichiers d'annotation, un par session (`<session>.json`). Le dossier des captures
  n'est jamais modifié.
- `~/kosmo-runs/` : les fichiers produits par les outils (par exemple le fichier de découpage `split.json`).

## Dev and eval split

Le découpage sépare les sessions en deux ensembles : « dev » (environ 70 %) pour développer et régler, « eval »
(environ 30 %) pour mesurer (CV3). Le découpage se fait par session. Si l'équilibre n'est pas atteint, il se fait par
blocs, et le résultat est alors marqué « plus optimiste » partout où il apparaît.

Étape K1, faite : le découpage est verrouillé et commité dans `tools/camera/config/split-lock.json` (modes, décomptes et
empreinte, aucun nom de session). Il ne change plus : rien à relancer pour cette étape.

- L'ensemble « eval » n'est jamais utilisé pour entraîner, régler ou choisir un seuil (CV4).

## Annotate

Étape K2 : saisir la pointe de chaque fléchette jugée des sessions existantes, avec l'outil. L'outil reprend où vous étiez.

Lancer l'outil dans WSL, depuis le dossier `tools/camera/` du dépôt (comme pour K1) :

```bash
python3 -m kosmo_camera.annotate --captures ~/kosmo-data/captures --annotations ~/kosmo-data/annotations
```

Le terminal affiche `Outil d'annotation prêt : http://127.0.0.1:8765/`. Ouvrez cette adresse dans le navigateur de
Windows. L'outil n'écoute que sur votre machine (127.0.0.1). Si le port 8765 est déjà pris, l'outil le dit et s'arrête ; ajoutez
`--port <numéro>` à la commande pour en choisir un autre. Si Windows n'arrive pas à ouvrir cette adresse depuis WSL,
signalez-le dans le chat avec le message affiché : ne cherchez pas de contournement.

Ce que vous devez voir :

- La liste des sessions, avec « n / N pointes saisies » et l'état « Calibrée » ou « Non calibrée » (lecture seule). Une
  session sans fléchette jugée est marquée « Ignorée : aucune fléchette jugée » et ne s'ouvre pas.
- Les segments de calibration et les contrôles déjà faits ne se modifient plus dans l'outil : ils s'affichent en
  lecture seule (voir K7.C).
- Chaque photo de fléchette affiche sa vraie valeur (par exemple « T20 »). Les entrées annulées sont sautées.
- Un clic pose la pointe et affiche la fléchette suivante. Après chaque action, la barre d'état affiche
  « Enregistré à HH:MM:SS ».
- Fermer puis rouvrir une session reprend à la première fléchette non annotée, et les pointes déjà placées
  réapparaissent aux mêmes endroits.
- Les fichiers apparaissent dans `~/kosmo-data/annotations/<session>.json`. Le dossier des captures reste inchangé.

Touches de l'outil :

| Touche | Action (bouton) |
|---|---|
| `H` | Cachée (fléchette non visible) |
| `O` | Hors cible |
| `U` | Effacer (la pointe de la fléchette courante) |
| `V` | Image vidéo de la fléchette, en lecture seule (K7.3) |
| `N` | Suivante |
| `P` | Précédente |
| `+` ou `=` / `-` | Zoom avant / arrière (sur un clavier AZERTY, `+` et `0` demandent Maj ; `=` évite Maj) |
| `0` | Vue entière |
| Flèches | Déplacer la vue (la molette zoome à l'endroit du curseur : déplacer la vue est rarement utile) |
| `Échap` | Retour à la liste des sessions |

Pour arrêter l'outil, appuyez sur `Ctrl+C` dans le terminal. Pour reprendre, relancez la même commande : le travail
déjà enregistré est repris.

## Build the image

Étape K3, pendant que vous annotez. L'image d'entraînement se construit une fois, avec réseau, depuis la racine du
dépôt :

```bash
docker build -f tools/camera/Dockerfile -t kosmo-camera:gpu tools/camera
```

- Le SHA-256 des poids COCO est déjà inscrit dans le `Dockerfile` (`NANO_SHA256`). Si les poids téléchargés sont
  différents, le build échoue avec `NANO_SHA256 is empty or different` et affiche le SHA-256 reçu : collez-le dans le
  chat, ne le contournez pas.
- Le build vérifie aussi la somme MD5 des poids.

## GPU check

Une fois l'image construite, lancez la vérification du GPU dans le conteneur (accès au GPU par CDI uniquement, sans
réseau, sans capacité système, système de fichiers en lecture seule) :

```bash
docker run --rm --device nvidia.com/gpu=all --network none --cap-drop ALL --security-opt no-new-privileges \
  --read-only --tmpfs /tmp:rw,size=2g -v ~/kosmo-runs:/runs:rw \
  kosmo-camera:gpu python -m kosmo_camera.gpu_check
```

Ce qu'il faut voir : le tableau `nvidia-smi` avec le nom du GPU (RTX 4070, 12 Go), `torch version`, `cuda version`,
la ligne `cuda available: True`, puis `device name:` avec le nom du GPU. Si la ligne dit `cuda available: False`,
collez toute la sortie dans le chat et arrêtez-vous là.

## Visionneuse (K7)

Étape K5 suspendue le 15/11/2026 : la mesure de dérive n'est pas fiable (presque toutes les photos sortent au-delà du seuil, et le déplacement vu à l'œil à partir de la fléchette 57 de la session d'éval de 18h24 n'apparaît pas). Ne recalibrez plus d'après le rapport de dérive. Les recalibrations déjà faites sont gardées. K7 est arrêtée à son tour, voir ci-dessous.

Étapes K7 arrêtées le 18/11/2026 (nouvelle stratégie de l'issue #42) : vos clics de contrôle sont terminés et gardés comme vérité de référence ; aucun nouveau clic de calibration ou de contrôle ne vous sera demandé. Le recalage des points (« recalé », « non recalé ») et la mesure de dérive ne seront plus développés. Les clics de K7.1b et K7.3 (recalage, calibration, contrôle) sont arrêtés. Réponse B à la question Q1 de #42, appliquée par la tâche C1 : l'outil garde l'affichage de vos annotations et la saisie des pointes, pour de futures campagnes courtes (autres types de fléchettes, 10 min au plus) ; il montre en lecture seule vos segments, vos contrôles et les images vidéo. Le recalage, la dérive et la saisie des calibrations et des contrôles n'y sont plus. La suite (phase 1) sera une seule commande, après que vous aurez confirmé sur #42 que le dossier d'images réelles est monté.

K7.0. Dans votre clone : `git fetch origin`, puis `git switch docs/camera-registration-strategy` et `git pull` (la branche de la PR de l'issue #42). (1 min)

```bash
git fetch origin
git switch docs/camera-registration-strategy
git pull
```

K7.1. Reconstruire l'image depuis la racine du dépôt : `docker build -f tools/camera/Dockerfile -t kosmo-camera:gpu tools/camera`. (2 à 5 min)

```bash
docker build -f tools/camera/Dockerfile -t kosmo-camera:gpu tools/camera
```

K7.2. Extraire les images vidéo prises environ 1 s avant chaque saisie (pas de carte graphique nécessaire) :

```bash
docker run --rm --network none --cap-drop ALL --security-opt no-new-privileges --read-only --tmpfs /tmp:rw,size=2g -v ~/kosmo-data:/data:ro -v ~/kosmo-runs:/runs:rw kosmo-camera:gpu python -m kosmo_camera.video_frames --captures /data/captures --annotations /data/annotations --split /runs/split.json --out /runs/frames
```

Elle affiche le nombre d'images extraites, la ligne « Images vidéo absentes : N » et les manquantes par raison ; une
vidéo illisible ne l'arrête pas. Rien à coller. Le fichier `frames.json` et le dossier `~/kosmo-runs/frames` restent sur votre ordinateur : ne les collez jamais. (2 à 5 min)

K7.3. Lancer l'outil dans WSL, depuis `tools/camera/` (la commande `python3` de WSL suffit, rien d'autre à installer) :

```bash
python3 -m kosmo_camera.annotate --captures ~/kosmo-data/captures --annotations ~/kosmo-data/annotations --frames ~/kosmo-runs/frames
```

puis ouvrir `http://127.0.0.1:8765`. Si le port 8765 est pris, ajoutez `--port <numéro>` à la commande.

Dans une session, la touche `V` affiche l'image vidéo de la fléchette (prise environ 1 s avant la saisie), quand elle
existe ; `V` à nouveau revient à la photo. Sans image vidéo, `V` ne change rien et affiche « Pas d'image vidéo pour cette
fléchette. » : le bouton « Image vidéo (V) » est alors grisé. Sans `--frames`, aucune image vidéo n'est proposée. Si
`frames.json` est illisible, l'outil affiche « Images vidéo illisibles : frames.json est invalide ou illisible (relancer
l'extraction, K7.2) ». Rien à coller.


K7.4 à K7.6 : annulées le 18/11/2026 (le diagnostic n'est plus prévu).

K7.C. Contrôle visuel en 2 minutes (ce que seul Kosmo peut vérifier : le JavaScript ne tourne pas dans le bac à sable).
Lancez l'outil (K7.3), ouvrez une session dont des fléchettes ont une image vidéo et des segments, puis vérifiez :

- Pointes : après la dernière pointe saisie, le message « Toutes les pointes sont saisies. Précédente (P) ou la liste
  pour corriger. » apparaît.
- Touche `V` sur une fléchette sans image vidéo : rien ne change, le message « Pas d'image vidéo pour cette fléchette. »
  apparaît, et le bouton « Image vidéo (V) » est grisé.
- Touche `V` sur une fléchette avec image vidéo : l'image s'affiche, avec le libellé « Image vidéo (lecture seule) » au-dessus de la photo ; `V` revient à la photo.
- Légende sous les boutons : « + pointe », « □ 1 à 4 : points du segment (lecture seule) », puis « ○ C : contrôle
  (lecture seule) » ou « ○ C : contrôle — aucun pour cette fléchette ».
- Image vidéo affichée : un clic sur l'image ne pose aucune pointe et ne change rien.
- Photo d'une fléchette couverte par un segment : des carrés numérotés 1 à 4 marquent les points du segment ; un contrôle
  s'affiche en cercle marqué C. Ces repères ne bougent pas au clic.
- Touches `R`, `K` et `1` à `4` : elles ne font rien, aucun écran de recalage ou de calibration ne s'ouvre.
- Aucun texte « recalé » ni « non recalé » nulle part sur la page.
- Un clic sur la photo pose la pointe, et la barre d'état affiche « Enregistré à HH:MM:SS » (comme en K2).

## Confirmation campaign protocol

Ce protocole ne s'applique que si le rapport préliminaire demande une campagne de confirmation (CV17). Aucune prise de
vue n'est faite avant ce rapport.

- Les sessions vont dans `~/kosmo-data/captures-eval/<session>/`.
- Une fléchette est saisie juste après chaque lancer.
- Après la dernière fléchette, retirez les fléchettes de la cible, quittez le champ de vision de la caméra, puis
  appuyez sur « Volée suivante » dans les 3 secondes (CV11).
- Aucune autre personne ne se trouve devant la cible pendant la prise de vue.
- Le nombre de sessions, les positions et les éclairages sont ceux demandés par le rapport.

## Groups

Les groupes servent aux tableaux de résultats (CV14). Ils sont fixés ici, avant toute mesure.

- Éclairage, selon la luminance médiane (0 à 255) de la zone de la cible : `dark` < 70, `medium` de 70 à 140,
  `bright` > 140.
- Angle du trépied, selon l'angle de vue : `front` < 15 degrés, `oblique` de 15 à 35, `steep` > 35.
- Côté, selon la direction dominante de la déformation : `left`, `right`, `above`, `below`. `front` n'a pas de côté.
- Distance, selon le diamètre apparent de la cible divisé par le facteur de zoom : `far` < 0,35, `mid` de 0,35 à 0,6,
  `near` > 0,6.
- Zoom, selon le facteur de zoom : `1x` < 1,5, `2x` de 1,5 à 3, `zoom` > 3.
