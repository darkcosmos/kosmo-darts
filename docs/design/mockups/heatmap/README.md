# Maquettes de l'échelle de couleurs de la heatmap (#52, #53)

Question pour Kosmo (décision P9 C, gate G1) : quelle échelle de couleurs pour la heatmap ?

- `heatmap-relative.svg` : échelle relative à la zone la plus touchée. La zone la plus touchée de chaque plateau prend
  la couleur la plus claire. Légende : de 0 à la valeur max du plateau.
- `heatmap-absolute.svg` : échelle absolue de 0 à 100 %. Une même couleur veut toujours dire le même pourcentage, d'un
  plateau à l'autre. Légende : de 0 à 100 %.

Les deux fichiers montrent les mêmes données et la même rampe. Seule la correspondance valeur / couleur change.

## Données d'exemple (150 fléchettes par plateau, 12 ratées = 8 %)

- Répartie : 138 fléchettes sur 48 zones (simples, doubles, triples, 25 et Bull), au plus 7 par zone (4,7 %). Les
  14 autres zones restent à 0 (couleur neutre).
- Concentrée : T20 45 (30 %), S20 30 (20 %), S5 20 (13,3 %), S1 17 (11,3 %), puis 14 zones à 1 ou 2 fléchettes. Les
  44 autres zones sont à 0.

## Rampe (luminance relative WCAG, strictement croissante)

| Étape | Couleur | Luminance |
|---|---|---|
| 0 fléchette (neutre) | `#252B3F` (cadre `#5C6580` dans la légende) | 0,025 |
| 0 % de l'échelle (plus bas) | `#414487` | 0,070 |
| 25 % | `#2A788E` | 0,159 |
| 50 % | `#22A884` | 0,300 |
| 75 % | `#7AD151` | 0,503 |
| 100 % (plus haut) | `#FDE725` | 0,782 |

Les couleurs intermédiaires sont interpolées entre deux étapes. Fond `#0A0F1E`. Le neutre est plus sombre et sans teinte
vive, donc distinct de l'étape la plus basse. Le sens ne repose pas sur la couleur seule : la bande de la zone
touchée et le top 5 donnent les valeurs (HM5, HM6).
