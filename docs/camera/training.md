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

## Dérive et recalibration

Étape K5 suspendue le 15/11/2026 : la mesure de dérive n'est pas fiable (presque toutes les photos sortent au-delà du seuil, et le déplacement vu à l'œil à partir de la fléchette 57 de la session d'éval de 18h24 n'apparaît pas). Ne recalibrez plus d'après le rapport de dérive. Les recalibrations déjà faites sont gardées. La suite est l'étape K7 (contrôle de calibration).

Étape K5 : le trépied bouge un peu entre les photos, donc la calibration cliquée une fois ne vaut plus pour toute la
session. La mesure de dérive dit à partir de quelle fléchette recalibrer ; vous recalibrez dans l'outil, puis vous
mesurez de nouveau, jusqu'à ce que tout soit sous le seuil (1,0 mm). Les commandes ci-dessous se copient telles quelles.

K5.0. Dans votre clone, récupérez la branche :

```bash
git fetch origin
git switch feat/camera-feasibility-0b-1-detection
```

K5.1. Reconstruisez l'image depuis la racine du dépôt (avec réseau, elle contient maintenant le code de dérive), puis
relancez la commande de la section « GPU check » sans la changer :

```bash
docker build -f tools/camera/Dockerfile -t kosmo-camera:gpu tools/camera
```

K5.2. Mesurez la dérive de toutes les sessions (pas besoin du GPU : pas de réseau, aucune capacité système, système
de fichiers en lecture seule, `~/kosmo-data` monté en lecture seule) :

```bash
docker run --rm --network none --cap-drop ALL --security-opt no-new-privileges --read-only --tmpfs /tmp:rw,size=2g -v ~/kosmo-data:/data:ro -v ~/kosmo-runs:/runs:rw kosmo-camera:gpu python -m kosmo_camera.drift --captures /data/captures --annotations /data/annotations --runs /runs
```

Ce qu'il faut voir : le chemin du rapport, puis la ligne « Tout sous le seuil : oui » ou « Tout sous le seuil : non ».
La commande écrit `~/kosmo-runs/drift/drift-report.md` (rapport chiffré, sessions nommées S01, S02...) et
`~/kosmo-runs/drift/drift.json` (fichier local lu par l'outil, il contient les noms de sessions : ne le collez pas).

K5.3. Relancez l'outil d'annotation dans WSL, depuis `tools/camera/`, avec le fichier de dérive :

```bash
python3 -m kosmo_camera.annotate --captures ~/kosmo-data/captures --annotations ~/kosmo-data/annotations --drift ~/kosmo-runs/drift/drift.json
```

Ce qu'il faut voir :

- Dans la liste, chaque session porte une ligne de dérive : « À recalibrer à partir de la fléchette 59 » (ou « des
  fléchettes ... »), « Dérive OK (max 0,4 mm) », « Dérive non mesurée » ou « photo non mesurable ». La session
  d'évaluation de 18 h 24 doit demander la fléchette 59.
- Dans une session, la fléchette à recalibrer est marquée d'un point d'exclamation dans la rangée des numéros
  (« 59 ! ») et son écran affiche « Dérive au-delà du seuil : recalibrer à partir d'ici (R) ».
- Sur chaque fléchette à recalibrer, appuyez sur `R` ou sur le bouton « Recalibrer à partir de cette fléchette (R) »
  (large bouton sous les autres). L'écran « Recalibration à partir de la fléchette N : point k sur 4 » montre les
  anciens points en croix creuses étiquetées « ancien 1 » à « ancien 4 » ; cliquez les 4 nouveaux points dans l'ordre.
  La barre d'état affiche « Enregistré à HH:MM:SS ».
- `Effacer` (`U`) retire le dernier point ; sur une recalibration déjà enregistrée, il la retire du fichier tout de
  suite. Sans point, il ramène à la fléchette.
- Après une recalibration, les indications de la session restent affichées avec « (mesure périmée) » tant que la
  mesure n'est pas relancée : les autres fléchettes à recalibrer ne sont pas perdues.

K5.4. Relancez la commande K5.2. Répétez K5.3 et K5.4 jusqu'à « Tout sous le seuil : oui », puis collez dans le chat
le dernier rapport, `~/kosmo-runs/drift/drift-report.md`.

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
