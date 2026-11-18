# Lecteur Taycan (Taycan-Couleur).

Application Android développée en Kotlin (`com.example.taycancolorproxy`) conçue pour agir comme un pont multimédia intelligent entre **Deezer** et **Android Auto**.

## 🎯 Problématique & Objectif

Les applications de streaming comme Deezer écrasent systématiquement le focus des métadonnées et forcent l'affichage de leurs pochettes sur l'écran d'Android Auto. Ce projet résout ce problème en transformant l'application en un **`MediaSessionService` maître** :
1. **Contrôle de la session :** L'application prend le relais auprès d'Android Auto pour diffuser un flux propre, tout en **neutralisant l'envoi de la pochette d'album** (évitant les flashs visuels indésirables sur l'écran du véhicule).
2. **Ambiance Lumineuse :** En arrière-plan, un `NotificationListenerService` intercepte discrètement le flux et l'artwork de Deezer pour extraire la palette de couleurs et piloter les LEDs d'ambiance du véhicule.
3. **Passerelle de Transport :** Les commandes de la voiture (Play, Pause, Suivant, Précédent) sont captées par notre session et réacheminées proprement vers le lecteur de Deezer.

---

## 🛠️ Architecture du Projet

* **`ColorProxyService.kt`** : Service central (`MediaSessionService`). Il gère la session prioritaire auprès du système, nettoie le `MediaMetadataCompat` pour la voiture (zéro pochette transmise) et transmet les commandes de transport via des `BroadcastIntent` vers Deezer.
* **`DeezerNotificationListener.kt`** : Service d'écoute des notifications (`NotificationListenerService`) qui capte en temps réel le titre, l'artiste et le bitmap de la notification Deezer pour alimenter le traitement interne des LEDS.
* **`AndroidManifest.xml`** : Déclaration des services requis et des permissions nécessaires (`MEDIA_CONTENT_CONTROL`).

---

## ⚙️ Prérequis & Configuration Android

Pour que le système intercepte correctement les données en arrière-plan :
1. L'application nécessite l'autorisation d'accès aux notifications (*Paramètres Android > Applications > Accès spécial > Accès aux notifications*).
2. Android Auto doit reconnaître le service comme une source multimédia active prioritaire.

## 🚀 Stack Technique
* **Langage :** Kotlin
* **Frameworks Android :** AndroidX Media (`MediaSessionCompat`, `MediaSessionService`), NotificationListenerService.
