# Multi Blindtest

Application Android de blindtest multijoueur, alimentée par les extraits musicaux de Deezer. Elle se joue sur un seul téléphone, ou en **mode buzzers** : chaque joueur buzze depuis son propre téléphone.

## Principe

1. Ajouter les joueurs (2 à 10) et régler les options :
   - nombre de chansons par manche (3 à 10, 6 par défaut) ;
   - nombre de tours par joueur (1 à 5) : chacun sera maître du jeu (MJ) autant de fois ;
   - ordre des MJ aléatoire : tiré au sort au lancement, puis identique à chaque tour.
2. En cachette, le MJ choisit ses chansons sur Deezer (recherche par titre / artiste, titres du moment, ou lien de playlist collé).
3. Les extraits sont joués **en même temps**, en boucle.
4. Quand un joueur reconnaît une chanson, le MJ la touche et indique qui l'a trouvée (+1 point). La musique s'arrête ; le MJ relance les chansons restantes.
5. Quand il n'en reste que 2, les joueurs doivent donner les deux : le MJ désigne qui les a données (+2 points) ou « personne ».
6. Les réponses sont révélées, puis le tour suivant commence avec le MJ suivant. Classement final à la fin de la partie.

## Mode buzzers

Activé sur l'écran des joueurs. Le téléphone hôte (celui qui joue la musique) lance un petit serveur web sur le réseau local, sans aucun serveur externe :

1. Les joueurs scannent le QR code affiché sur l'hôte : une page buzzer s'ouvre dans leur navigateur (Android ou iPhone, rien à installer). Ils entrent leur prénom.
2. Les joueurs sans téléphone, et le propriétaire de l'hôte, s'ajoutent à la main sur l'hôte comme avant.
3. Pendant une manche, le premier qui buzze prend la main : la musique s'arrête et les autres buzzers sont bloqués. Le MJ touche la chanson trouvée (+1), ou « Mauvaise réponse » : la musique reprend et ce joueur est bloqué jusqu'à la prochaine chanson trouvée. En finale, il faut donner les deux chansons.
4. Le MJ peut toujours attribuer un point à la main, sans buzz.

Tous les téléphones doivent être sur le même wifi. Certains réseaux (hôtels, wifi invités) bloquent les échanges entre appareils : dans ce cas, activer le partage de connexion de l'hôte et y connecter les joueurs. Un téléphone qui se met en veille se reconnecte tout seul et retrouve sa place. L'écran de l'hôte reste allumé pendant la partie.

Pour l'instant, quand un autre joueur est MJ, il prend le téléphone hôte pour choisir ses chansons et valider les réponses.

## Stack

- Kotlin, Jetpack Compose (Material 3)
- Retrofit + kotlinx.serialization pour l'[API publique Deezer](https://developers.deezer.com/api) (sans authentification)
- Media3 ExoPlayer pour la lecture des extraits
- Coil pour les pochettes
- Ktor (serveur CIO + WebSocket) pour le mode buzzers, ZXing pour le QR code

## Structure

```
app/src/main/java/com/panzane/multiblindtest/
├── data/deezer/   API Deezer, modèles et repository
├── audio/         Lecture simultanée des extraits (ExoPlayer)
├── game/          État de la partie et ViewModel
├── net/           Serveur du mode buzzers et protocole échangé avec la page
└── ui/            Écrans Compose et thème
app/src/main/assets/buzzer.html   Page buzzer servie aux téléphones des joueurs
```

## Build

Prérequis : JDK 17 et SDK Android (platform 35, build-tools 35). Le plus simple est d'ouvrir le projet dans Android Studio.

```bash
./gradlew assembleDebug        # APK dans app/build/outputs/apk/debug/
./gradlew installDebug         # installe sur un appareil connecté (débogage USB)
```
