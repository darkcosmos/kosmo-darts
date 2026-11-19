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

Étape K2 : annoter toutes les sessions existantes avec l'outil. Compter environ une heure.

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

Étape K5 suspendue le 15/11/2026 : la mesure de dérive n'est pas fiable (presque toutes les photos sortent au-delà du seuil, et le déplacement vu à l'œil à partir de la fléchette 57 de la session d'éval de 18h24 n'apparaît pas). Ne recalibrez plus d'après le rapport de dérive. Les recalibrations déjà faites sont gardées. K7 est arrêtée à son tour, voir ci-dessous.

Étapes K7 arrêtées le 18/11/2026 (nouvelle stratégie de l'issue #42) : vos clics de contrôle sont terminés et gardés comme vérité de référence ; aucun nouveau clic de calibration ou de contrôle ne vous sera demandé. Le recalage des points (« recalé », « non recalé ») et la mesure de dérive ne seront plus développés. Les clics de K7.1b et K7.3 (recalage, calibration, contrôle) sont arrêtés. Selon votre réponse B à la question Q1 de #42, la tâche C1 retire de l'outil la saisie des calibrations et des contrôles, le recalage et la dérive ; l'outil garde l'affichage de vos annotations et la saisie des pointes, pour de futures campagnes courtes (autres types de fléchettes, 10 min au plus). La suite (phase 1) sera une seule commande, après que vous aurez confirmé sur #42 que le dossier d'images réelles est monté.

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

K7.3. Relancer l'outil d'annotation dans WSL, depuis `tools/camera/`, avec l'environnement de K7.1b :

```bash
~/kosmo-tool-venv/bin/python -m kosmo_camera.annotate --captures ~/kosmo-data/captures --annotations ~/kosmo-data/annotations --split ~/kosmo-runs/split.json --frames ~/kosmo-runs/frames
```

puis ouvrir `http://127.0.0.1:8765`. La ligne en haut donne la seule consigne en cours. Sur la liste, la carte
« Prochaine étape » a un bouton « Continuer » (ou Entrée) ; dans une session, appuyer sur K (« Contrôle de
calibration »), depuis n'importe quel écran de la session, même « Terminé ». Vos clics servent à désigner le
bon croisement de fils, pas à le viser au pixel : l'outil recale chaque point sur le croisement exact. Pour
chaque photo, les 4 points de la photo précédente sont déjà proposés et recalés ; les 4 vignettes agrandies à
droite montrent chaque point avec son état (« recalé » veut dire : le point est placé sur le croisement exact ; « non recalé », « caché », « à cliquer »). Si les 4
sont sur le bon croisement : Entrée. Si un point est sur le mauvais croisement ou « à cliquer » : cliquer près
du bon croisement (le point le plus proche est remplacé et recalé ; pour un point loin de votre clic,
choisir d'abord le point avec les touches 1 à 4), puis Entrée. H seulement si une fléchette cache le point
(touche 1 à 4 puis H ; une photo inutilisable : 1 H 2 H 3 H 4 H puis Entrée, elle compte comme cachée) ; U
annule le dernier changement, ou revient à la photo précédente (son enregistrement est retiré jusqu'à
Entrée) ; Échap pour quitter. Entrée enregistre toujours, même après un clic sur un bouton. Rien n'est enregistré avant Entrée
(« Points prêts : n / 4 », puis « Enregistré à HH:MM:SS »). La première photo de chaque session part de la
calibration de la session ; la première image vidéo de chaque session n'a pas de proposition : 4 clics. Après
un déplacement de la cible, les points proposés partent sur un mauvais croisement ou restent « à cliquer » :
les recliquer. Même travail ensuite sur les images vidéo. Environ 190 photos et 45 images vidéo (les nombres
exacts s'affichent sur la carte de chaque session, « Contrôle photo : D / T ») : le plus souvent une seule
touche par photo (environ 5 s), 4 clics sur la première image vidéo de chaque session (4 images), quelques
clics après un déplacement : environ 20 à 30 min au total (au lieu de 1 h 15), en plusieurs fois si vous voulez (l'outil
reprend où vous étiez). Un point « non recalé » est gardé à votre clic et compté dans le diagnostic. Sans
`--split`, la ligne en haut dit « Contrôle indisponible : relancer l'outil avec --split (K7.3) » ; sans `--frames`,
« Images vidéo absentes : lancer l'extraction (K7.2) » ; lancé
avec `python3` au lieu de `~/kosmo-tool-venv/bin/python`, il affiche « Recalage indisponible » : relancer
comme indiqué. Rien à coller.

K7.4 à K7.6 : annulées le 18/11/2026 (le diagnostic n'est plus prévu).

K7.C. Checklist (ce que seul Kosmo peut vérifier, le JavaScript ne tourne pas dans le bac à sable). Le contrôle visuel
prévu avec K7.4 à K7.6 est annulé le 18/11/2026.

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
