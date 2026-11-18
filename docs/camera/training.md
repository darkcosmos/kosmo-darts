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

Étape K1, dans le dossier `tools/camera/` du dépôt, depuis WSL :

```bash
python3 -m kosmo_camera.split --captures ~/kosmo-data/captures --lock ~/kosmo-runs/split-lock.json
```

- La commande affiche le mode (session ou blocs, « plus optimiste »), les unités et les fléchettes jugées par
  ensemble, et le SHA-256 du découpage (`split sha256`).
- Le fichier de découpage est écrit dans `~/kosmo-runs/split.json`. La commande refuse de l'écraser : elle ne se lance
  qu'une fois. Le découpage ne change plus une fois verrouillé.
- Le fichier de verrouillage est écrit dans `~/kosmo-runs/split-lock.json` (modes, décomptes et empreinte, aucun nom
  de session).
- Collez dans le chat la sortie complète de la commande, puis le contenu du fichier `~/kosmo-runs/split-lock.json`
  (par exemple avec `cat ~/kosmo-runs/split-lock.json`). L'équipe le commit tel quel, octet pour octet, dans
  `tools/camera/config/split-lock.json`. Aucun des deux ne contient de nom de session.
- Pour recommencer après une erreur, et seulement tant que le verrouillage n'est pas commité : supprimez
  `~/kosmo-runs/split.json` et `~/kosmo-runs/split-lock.json`, puis relancez la commande.
- L'ensemble « eval » n'est jamais utilisé pour entraîner, régler ou choisir un seuil (CV4).
- Le mode blocs n'est verrouillé qu'après votre accord.

## Annotate

Étape K2 faite ; elle ne s'applique à aucune nouvelle session : leur vérité terrain est produite automatiquement (ADR 0018).

Lancer l'outil dans WSL, depuis le dossier `tools/camera/` du dépôt (comme pour K1) :

```bash
python3 -m kosmo_camera.annotate --captures ~/kosmo-data/captures --annotations ~/kosmo-data/annotations
```

Le terminal affiche `Outil d'annotation prêt : http://127.0.0.1:8765/`. Ouvrez cette adresse dans le navigateur de
Windows. L'outil n'écoute que sur votre machine (127.0.0.1). Si le port 8765 est déjà pris, l'outil le dit et s'arrête ; ajoutez
`--port <numéro>` à la commande pour en choisir un autre. Si Windows n'arrive pas à ouvrir cette adresse depuis WSL,
signalez-le dans le chat avec le message affiché : ne cherchez pas de contournement.

Ce que vous devez voir :

- La liste des sessions, avec l'avancement et l'état de calibration (« Non calibrée » ou calibrée). Une session sans
  fléchette jugée est marquée « Ignorée : aucune fléchette jugée » et ne s'ouvre pas.
- La calibration demande les 4 points dans l'ordre (« Calibration : point 1 sur 4 », puis 2, 3, 4). Un schéma met en
  évidence le prochain point à cliquer : le coin extérieur du double, entre les fils indiqués.
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
| `U` | Effacer (la pointe de la fléchette courante, ou le dernier point en calibration) |
| `N` | Suivante |
| `P` | Précédente |
| `+` ou `=` / `-` | Zoom avant / arrière (sur un clavier AZERTY, `+` et `0` demandent Maj ; `=` évite Maj) |
| `0` | Vue entière |
| Flèches | Déplacer la vue (la molette zoome à l'endroit du curseur : déplacer la vue est rarement utile) |
| `Échap` | Retour à la liste des sessions |

Les quatre points de calibration ne sont enregistrés qu'après le quatrième clic : si vous quittez avant (`Ctrl+C`
ou relance), ils sont perdus et la calibration est à refaire.

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

## Contrôle de calibration

Étape K5 suspendue le 15/11/2026 : la mesure de dérive n'est pas fiable (presque toutes les photos sortent au-delà du seuil, et le déplacement vu à l'œil à partir de la fléchette 57 de la session d'éval de 18h24 n'apparaît pas). Ne recalibrez plus d'après le rapport de dérive. Les recalibrations déjà faites sont gardées. La suite est l'étape K7 (contrôle de calibration).

K7.0. Dans votre clone : `git fetch origin`, puis `git switch feat/camera-feasibility-0b-1-detection` et `git pull`. (1 min)

```bash
git fetch origin
git switch feat/camera-feasibility-0b-1-detection
git pull
```

K7.1. Reconstruire l'image depuis la racine du dépôt : `docker build -f tools/camera/Dockerfile -t kosmo-camera:gpu tools/camera`. (2 à 5 min)

```bash
docker build -f tools/camera/Dockerfile -t kosmo-camera:gpu tools/camera
```

K7.1b. Une seule fois : préparer l'environnement du recalage dans WSL (il ne contient que numpy et Pillow, aux versions déjà utilisées dans l'image, vérifiées par empreinte), en collant cette seule commande depuis la racine du dépôt :

`python3 -m venv ~/kosmo-tool-venv && ~/kosmo-tool-venv/bin/pip install --require-hashes --only-binary=:all: -r tools/camera/requirements-tool.txt && ~/kosmo-tool-venv/bin/python -c "import numpy, PIL; print('numpy', numpy.__version__, '- Pillow', PIL.__version__)"`

Elle se termine par la ligne `numpy 2.5.4 - Pillow 13.0.0`. Si WSL répond que `venv` manque : `sudo apt install python3-venv`, puis recoller la commande. L'outil se lance ensuite toujours avec `~/kosmo-tool-venv/bin/python` (K7.3). (2 à 5 min)

K7.2. Extraire les images vidéo prises environ 1 s avant chaque saisie (pas de carte graphique nécessaire) :

```bash
docker run --rm --network none --cap-drop ALL --security-opt no-new-privileges --read-only --tmpfs /tmp:rw,size=2g -v ~/kosmo-data:/data:ro -v ~/kosmo-runs:/runs:rw kosmo-camera:gpu python -m kosmo_camera.video_frames --captures /data/captures --annotations /data/annotations --split /runs/split.json --out /runs/frames
```

Elle affiche le nombre d'images extraites, la ligne « Images vidéo absentes : N » et les manquantes par raison ; une
vidéo illisible ne l'arrête pas. Rien à coller. Le fichier `frames.json` et le dossier `~/kosmo-runs/frames` restent sur votre ordinateur : ne les collez jamais. (2 à 5 min)

K7.3. Contrôle de calibration à la main dans l'outil : fait, environ 1 h (104 photos d'évaluation à 4 points, recalage presque jamais actif). Ne pas refaire : aucune nouvelle saisie manuelle ne sera demandée (ADR 0018, D48). Vos clics sont gardés tels quels et servent une seule fois, à valider la mesure automatique.

Lot 3b (D48 à D54 ; tout se fait dans WSL ou le conteneur, aucun clic ; temps d'attention : 8 min, plus 2 min si K7.6 est relancée une fois, soit 10 min au pire ; K7.8 hors budget tant que Q15 n'est pas rouvert ; les durées de calcul sont estimées d'après les nombres réels : environ 190 photos et 45 images vidéo, environ 940 points, au plus 140 ms par recalage mesurés en T15m, puis recalculées d'après les durées réelles affichées en K7.4).

K7.4. Diagnostic du recalage (pas de carte graphique, pas de reconstruction de l'image). Dans votre clone : `git fetch origin`, puis `git switch docs/camera-feasibility-0b-1-lot-3b`, puis `git pull`.

```bash
git fetch origin
git switch docs/camera-feasibility-0b-1-lot-3b
git pull
```

Puis, depuis `tools/camera/` :

```bash
~/kosmo-tool-venv/bin/python -m kosmo_camera.snap_report --captures ~/kosmo-data/captures --annotations ~/kosmo-data/annotations --split ~/kosmo-runs/split.json --frames ~/kosmo-runs/frames --out ~/kosmo-runs/snap
```

Elle lit vos fichiers sans les modifier et affiche le chemin du rapport, sa durée et la ligne « Durée prévue de K7.6 : environ N min » (nombre de points x départs par point x durée médiane d'un recalage, D54). Coller dans le chat le contenu de `~/kosmo-runs/snap/snap-report.md` (des chiffres et des rôles, aucun nom de session, aucune image) ; le rapport peut tenir sur plusieurs écrans : tout coller, rien d'autre n'est demandé. (attention : 3 min ; calcul : quelques minutes, sans surveillance)

K7.5 et la suite arrivent dans la prochaine livraison.

K7.C. Faite au lot 3a ; constats repris en D49 ; ne pas refaire. Checklist (ce que seul Kosmo peut vérifier, le JavaScript ne tourne pas dans le bac à sable). Les contrôles de clics ci-dessous ne sont pas refaits. Le contrôle visuel (alternance, touches 1, 2, 3, N, Espace, flèches, « Photo i / n ») n'existe pas encore : il ne sera à vérifier que si un verdict le demande.

- Liste des sessions :
  - une seule consigne affichée à la fois, jamais une consigne déjà faite ;
  - chaque carte de session dit ce qui est fait et ce qui reste ; aucun « Dérive au-delà du seuil » ni « À recalibrer » ;
  - la carte « Prochaine étape » est celle de la session annoncée, la consigne ne cite aucune touche et le bouton
    « Continuer » ouvre l'étape annoncée ;
  - Entrée lance « Continuer » seulement quand aucun autre bouton n'a le focus (après Tab sur un autre bouton, Entrée
    active ce bouton-là).
- Contrôle de calibration (K) :
  - les 4 points proposés apparaissent déjà recalés, chacun dans sa vignette agrandie avec son état en toutes lettres ;
  - un clic à quelques pixels du bon croisement déplace le point le plus proche et le recale exactement sur le
    croisement (vignette à l'appui) ; un clic loin de tout croisement net donne « non recalé » ;
  - Entrée enregistre seulement quand les 4 points sont prêts, même quand un bouton a le focus (après un clic sur
    « Point caché (H) », Entrée enregistre) ; avec moins de 4 points elle affiche « Points prêts : n / 4 : placer les
    points manquants » ; gardée enfoncée ou pressée deux fois vite, elle n'enregistre pas la photo suivante sans la voir ;
  - en 1080p, les 4 vignettes, « Points prêts », « Enregistrer (Entrée) » et les boutons se voient ensemble sans
    faire défiler, le schéma du cadran étant placé après les boutons ;
  - sur le schéma, un point posé ou caché est vert, plein et marqué d'un « ✓ » (pas seulement la couleur) et le
    prochain point est le jaune agrandi ;
  - H sans point choisi ni point « à cliquer » affiche « Choisir le point à cacher : touche 1 à 4, puis H » et ne
    cache rien ; les touches 1 à 4, H, U, Échap, la touche 0, la molette et K marchent, K aussi depuis « Terminé » ;
  - « Points prêts : n / 4 » suit les changements et « Enregistré à HH:MM:SS » apparaît après Entrée ; la progression
    « Photos contrôlées » augmente ;
  - après un clic puis Échap, puis K, la même photo revient avec les points proposés ; U sans changement revient à la
    photo précédente avec ses points ; un refus (en rouge, en haut) disparaît à l'action suivante.
- Recalibration (R) :
  - l'écran explique ce que fait une recalibration, sur le côté, et la photo reste entière à l'écran en 1080p sans
    faire défiler ;
  - elle propose les points en vigueur, recalés, et s'enregistre par Entrée.
- Messages d'indisponibilité :
  - lancé avec `python3` seul, l'outil s'ouvre et dit « Recalage indisponible : relancer l'outil avec
    ~/kosmo-tool-venv/bin/python (K7.1b) » ;
  - une session d'évaluation courte (moins de 55 fléchettes) n'a aucune image vidéo à contrôler : sa carte dit
    « Contrôle vidéo : aucune image prévue pour cette session », jamais « Images vidéo absentes », et elle peut
    être « Terminée » sans extraction ni image vidéo ;
  - sans `--split`, la ligne en haut dit « Contrôle indisponible : relancer l'outil avec --split (K7.3) » et la carte
    « Contrôle : indisponible (option --split absente) » ; sans `--frames`, la ligne dit « Images vidéo absentes :
    lancer l'extraction (K7.2) » ; jamais « Contrôle terminé ».

## Confirmation campaign protocol

Ce protocole ne s'applique que si le rapport préliminaire demande une campagne de confirmation (CV17). Aucune prise de
vue n'est faite avant ce rapport.

Aucune saisie manuelle : la vérité terrain d'une campagne est produite automatiquement ; vous ne validez que les cas signalés par l'outil, 10 min au plus par campagne (ADR 0018). Décisions : [ADR 0018](../adr/0018-automatic-ground-truth.md) (vérité terrain automatique) et [ADR 0019](../adr/0019-capture-framing.md) (cadrage de la prévisualisation).

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
