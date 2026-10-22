# Système de design Kosmo Darts

Référence visuelle et règles de l'interface. Direction « Retransmission TV », variante « Plein écran » (B3), décidée le 2026-10-21. Les maquettes de `docs/design/mockups/` montrent l'intention ; **ce fichier fait foi pour les valeurs**. En cas d'écart entre une image et ce texte, le texte l'emporte.

Les dimensions sont en dp (les maquettes sont dessinées à 412 × 915 dp, la taille du Pixel 7 en portrait).

## 1. Principes

1. **Lisible de loin.** Le téléphone est posé sur un trépied près de la cible : le score restant du joueur courant doit se lire depuis la ligne de tir (environ 2,5 m).
2. **Une zone fixe pour le joueur courant.** Elle reste au même endroit quel que soit le joueur ; seule sa couleur change.
3. **La couleur ne porte jamais seule l'information.** Nom toujours affiché, repère ▸ pour le joueur suivant, mot « BUST » écrit.
4. **Le rouge est réservé à BUST.** Aucune autre utilisation, ni pour un joueur, ni pour la décoration.
5. **Pas de pop-up.** Les confirmations se font dans l'écran (exemple : checkout, section 6.3).
6. **Thème toujours sombre**, palette fixe (pas de couleur dynamique Android).

## 2. Couleurs

| Jeton | Valeur | Usage |
|---|---|---|
| `background` | `#0A0F1E` | Fond de tous les écrans |
| `surface` | `#121A33` | Tuiles, cartes, lignes de tableau, champs |
| `surfaceHigh` | `#18213F` | Touches du pavé, boutons secondaires |
| `surfaceHigher` | `#1B2546` | Boutons − et + des compteurs, cellule de rang secondaire |
| `outline` | `#2E3A63` | Bordure de la touche « Raté » |
| `onBackground` | `#FFFFFF` | Texte principal |
| `onBackgroundMuted` | `#AEB8D6` | Texte secondaire, en-têtes de section (8,7:1 sur `surface`) |
| `accent` | `#FFD23F` | Action principale, sortie, repère « suivant », fléchette en cours, sélection, focus |
| `onAccent` | `#0A0F1E` | Texte sur `accent` (13,2:1) |
| `accentDim` | `#2A2410` | Fond de la route de sortie quand elle n'est pas sur la zone du joueur |
| `danger` | `#B3261E` | BUST uniquement |
| `onDanger` | `#FFFFFF` | Texte sur `danger` (6,5:1) |

### Couleurs des joueurs

| Jeton | Valeur | Texte (`onPlayerN`) | Contraste |
|---|---|---|---|
| `player1` bleu | `#1F63D6` | `#FFFFFF` | 5,5:1 |
| `player2` orange | `#E07020` | `#0A0F1E` | 5,9:1 |
| `player3` sarcelle | `#0C7C6A` | `#FFFFFF` | 5,1:1 |
| `player4` lilas | `#C9A7FF` | `#0A0F1E` | 9,5:1 |

Règles :
- La couleur est **attachée au joueur**, pas à sa position : elle le suit quand on mélange l'ordre et pendant tout le match.
- À l'ajout d'un joueur, il reçoit la première couleur libre dans l'ordre ci-dessus. Retirer un joueur libère sa couleur.
- Sur la zone du joueur courant, tout le texte utilise `onPlayerN`. Les pastilles « S » et « M » restent `background` avec texte `onBackground` quelle que soit la couleur du joueur.
- Ailleurs (tuiles, lignes de tableau, configuration), la couleur du joueur n'apparaît que comme liseré ou marqueur, jamais comme fond de texte.

## 3. Typographie

**Police d'affichage : Saira, fichier variable** (axes `wght` 100–900 et `wdth` 50–125), copie du dépôt `google/fonts` (dossier `ofl/saira`) fournie dans `docs/design/font/` avec sa licence. À copier dans `res/font/` sous un nom valide pour Android (`saira_variable.ttf`). Licence SIL OFL 1.1 : livrer `OFL.txt` avec l'appli et ajouter une ligne de crédit dans les réglages. Ne pas sous-ensembler ni modifier le fichier. Les axes variables exigent Android 8.0 (API 26) au minimum.

- Rendu condensé : `wdth` = 75 pour tous les styles d'affichage.
- **Chiffres à largeur fixe** sur tout nombre affiché : `fontFeatureSettings = "tnum"`.
- Les libellés d'affichage sont en capitales, avec un espacement de lettres de 0,04 à 0,12 em.

**Texte courant : police système** (noms saisis, descriptions, sous-libellés des réglages).

| Style | Taille | Graisse | Mise à l'échelle | Usage |
|---|---|---|---|---|
| `scoreHuge` | 220 (204 à 4 joueurs) | 800 | **fixe** | Score restant du joueur courant (portrait) |
| `scoreHugeLandscape` | 160 | 800 | **fixe** | Idem en paysage |
| `keypad` | 28 (25 : 26 ; Bull : 22 ; libellés : 20) | 700–800 | **fixe** | Touches du pavé |
| `dart` | 26 | 700 | **fixe** | Cases des fléchettes |
| `visit` | 28 | 800 | **fixe** | Encadré de volée |
| `tileScore` | 32 (28 à 4 joueurs) | 800 | **fixe** | Score des tuiles |
| `route` | 28 | 700 | suit le réglage | Route de sortie |
| `playerName` | 28 (zone), 18 (tuile) | 800 / 700 | suit le réglage | Noms en capitales |
| `label` | 13–18 | 700 | suit le réglage | En-têtes, « SORTIE », « SET 2 · MANCHE 3 » |
| `button` | 20–23 | 700–800 | suit le réglage | Boutons d'action |
| `title` | 30–34 | 800 | suit le réglage | Titres d'écran |

« Fixe » signifie que la taille ne suit pas le réglage de taille de police d'Android (ces éléments sont déjà au-delà de ce que le réglage apporterait). Tout le reste suit le réglage et doit rester utilisable à 200 % : troncature par points de suspension pour les noms, retour à la ligne pour les libellés.

## 4. Formes, espacements, cibles

- Rayons : **4** pour les contrôles, touches, tuiles et lignes ; **8** pour la zone du joueur courant et les cartes. Aucune forme en pilule.
- Espacements : 4, 6, 8, 10, 12, 14, 16. Marges d'écran : 12 horizontal, 16 vertical.
- Cibles tactiles : au moins **48** ; touches du pavé **54** de haut ; boutons d'action **58–60**.
- États : désactivé = opacité 40 % (pavé entier à 35 % quand il est verrouillé) ; focus et sélection = contour `accent` de 2.

## 5. Composants

| Composant | Contenu et règles |
|---|---|
| `MatchHeader` | Ligne du haut : mode et fin (« 501 · SORTIE DOUBLE ») à gauche, position dans le match (« SET 2 · MANCHE 3 ») à droite, `label` en `onBackgroundMuted`. |
| `CurrentPlayerZone` | Fond `playerN`. En haut : nom, moyenne (« MOY 62,40 »), pastilles « S n » et « M n ». Au centre : `scoreHuge`. En bas : `CheckoutRouteBar`, puis `DartSlots` et `VisitBox`. États : normal, BUST (6.2), checkout (6.3). |
| `CheckoutRouteBar` | Étiquette « SORTIE » (`accent` / `onAccent`) suivie des segments de la route en `accent` sur `background`. Absente quand aucune route n'existe. |
| `DartSlots` | Trois cases `background`. Case de la prochaine fléchette : soulignée de 4 en `accent`. Fléchette finale d'un checkout : texte `accent`. |
| `VisitBox` | Score de la volée, sans libellé. **Plein** (fond blanc, texte `background`) pour la volée en cours ; **contour** (bordure `onBackgroundMuted`) pour la dernière volée des autres joueurs. |
| `PlayerTile` | Autres joueurs, dans l'ordre de jeu à partir du suivant. Liseré haut de 4 en `playerN`, nom (▸ en `accent` devant le suivant), `tileScore`, `VisitBox` contour, ligne « S n · M n ». 2 tuiles par ligne jusqu'à 3 joueurs, 3 tuiles à 4 joueurs. |
| `Keypad` | Grille de 5 colonnes : 1 à 20, puis Double, Triple, 25, Bull, Raté. Double et Triple sont des **bascules** (contour `accent`, texte `accent` ; état actif à définir sur le même principe, fond `accent`). Raté : contour `outline`, texte secondaire. |
| `ActionBar` | Deux boutons : secondaire à gauche (`surfaceHigh`), principal à droite (`accent`). |
| `Scoreboard` | Tableau façon retransmission : liseré `playerN`, nom, colonnes (sets, manches, moyenne du match), en-tête en `label`. Gagnant mis en avant (rang sur fond `accent`). |
| `ModeCard` | Carte de mode de l'accueil : vignette 64 × 64, nom du mode, description, chevron. |
| `ResumeCard` | Carte « PARTIE EN COURS » : mini-tuiles des joueurs (suivant entouré d'`accent`) et bouton « REPRENDRE · À X DE LANCER ». |
| `SegmentedChoice` | Choix exclusif (501/301, sortie double/simple) : option active en `accent`. |
| `Stepper` | Libellé, boutons − et + de 44 sur `surfaceHigher`, valeur au centre. |
| `PlayerNameRow` | Liseré `playerN`, numéro d'ordre, champ du nom. |
| `SettingsSwitch` | Interrupteur rectangulaire : actif = piste `accent`, curseur `background`. |

## 6. Écrans et comportements

Référence : images de `docs/design/mockups/`.

### 6.1 Partie
Portrait, de haut en bas : `MatchHeader`, `CurrentPlayerZone` (prend toute la hauteur restante), `PlayerTile`s, `Keypad`, `ActionBar` (« ANNULER » / « JOUEUR SUIVANT »). Paysage : zone du joueur et tuiles à gauche (480 de large), pavé et barre d'action à droite ; la route de sortie se place à droite du score.

### 6.2 BUST
Toute la `CurrentPlayerZone` passe en `danger`. Un carré de la couleur du joueur, bordé de blanc, reste devant son nom. La barre de sortie est remplacée par un bandeau blanc « BUST » en `danger`. Le score revient à sa valeur du début de volée, `VisitBox` affiche 0. Le pavé est verrouillé ; seul « JOUEUR SUIVANT » est actif.

### 6.3 Checkout (sans pop-up)
La zone garde la couleur du joueur. Le score est remplacé par un bandeau `accent` « CHECKOUT » et la ligne « 141 EN 3 FLÉCHETTES ». La fléchette finale est en `accent`. Le pavé est verrouillé et l'`ActionBar` devient « ANNULER » (annule la dernière fléchette) / « VALIDER LA MANCHE » (ou le libellé de fin de partie pour la dernière manche).

### 6.4 Autres écrans
Accueil (`ResumeCard`, puis les `ModeCard`s ; seule la carte X01 existe pour l'instant), configuration X01, manche suivante, fin de partie, réglages : voir les maquettes.

## 7. Mouvement

Seul mouvement : fondu de la couleur de la zone au changement de joueur, 200 ms. Désactivé si l'utilisateur a demandé à Android de supprimer les animations.

## 8. Accessibilité

- Tout élément interactif a un libellé lisible par TalkBack (le ▸ « suivant », les boutons − et +, l'icône des réglages).
- Contrastes indiqués en sections 2 ; toute nouvelle couleur doit atteindre 4,5:1 pour le texte courant et 3:1 pour le texte d'au moins 24 en gras.
- Les captures Roborazzi couvrent le portrait, le paysage, 4 joueurs aux noms longs et la police à 200 %.

## 9. Maquettes de référence

| Fichier | Contenu |
|---|---|
| `mockups/01-game.png` | Partie, 3 joueurs, portrait |
| `mockups/02-game-4-players.png` | Partie, 4 joueurs, noms longs |
| `mockups/03-bust.png` | BUST |
| `mockups/04-checkout.png` | Checkout |
| `mockups/05-game-landscape.png` | Partie en paysage |
| `mockups/06-home.png` | Accueil |
| `mockups/07-setup-x01.png` | Configuration X01 |
| `mockups/08-next-leg.png` | Manche suivante |
| `mockups/09-match-end.png` | Fin de partie |
| `mockups/10-settings.png` | Réglages |

Les indicateurs de manches et de sets (« S n · M n ») de ces maquettes remplacent la forme livrée par la mission « Manches et sets ».
