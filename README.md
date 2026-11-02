# Multi Blindtest

Application Android de blindtest multijoueur **sur un seul téléphone**, alimentée par les extraits musicaux de Deezer.

## Principe

1. Ajouter les joueurs (2 à 10). Chacun sera maître du jeu (MJ) à tour de rôle : une partie compte autant de tours que de joueurs.
2. En cachette, le MJ choisit 6 chansons sur Deezer (recherche par titre / artiste, titres du moment, ou lien de playlist collé).
3. Les 6 extraits sont joués **en même temps**, en boucle.
4. Quand un joueur reconnaît une chanson, le MJ la touche et indique qui l'a trouvée (+1 point). La musique s'arrête ; le MJ relance les chansons restantes.
5. Quand il n'en reste que 2, les joueurs doivent donner les deux : le MJ désigne qui les a données (+2 points) ou « personne ».
6. Les 6 réponses sont révélées, puis le tour suivant commence avec le MJ suivant. Classement final à la fin de la partie.

## Stack

- Kotlin, Jetpack Compose (Material 3)
- Retrofit + kotlinx.serialization pour l'[API publique Deezer](https://developers.deezer.com/api) (sans authentification)
- Media3 ExoPlayer pour la lecture des extraits
- Coil pour les pochettes

## Structure

```
app/src/main/java/com/panzane/multiblindtest/
├── data/deezer/   API Deezer, modèles et repository
├── audio/         Lecture simultanée des extraits (ExoPlayer)
├── game/          État de la partie et ViewModel
└── ui/            Écrans Compose et thème
```

## Build

Prérequis : JDK 17 et SDK Android (platform 35, build-tools 35). Le plus simple est d'ouvrir le projet dans Android Studio.

```bash
./gradlew assembleDebug        # APK dans app/build/outputs/apk/debug/
./gradlew installDebug         # installe sur un appareil connecté (débogage USB)
```
