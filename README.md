# ScannerPdfOcr

Projet Android (Kotlin) minimal: Scanner PDF + OCR + AdMob

Exécution rapide:

1. Ouvrir `c:\TEST\scannerPdfOcr` dans Android Studio.
2. Sync Gradle.
3. Lancer sur un appareil ou émulateur (assurez-vous d'ajouter les permissions camera/storage).

Construire depuis la ligne de commande (Windows PowerShell):

```powershell
cd "C:\TEST\scannerPdfOcr"
.\gradlew assembleDebug
```

AdMob test IDs utilisées (ne pas oublier de remplacer avant publication):
- App ID: `ca-app-pub-3940256099942544~3347511713`
- Banner: `ca-app-pub-3940256099942544/6300978111`
- Interstitial: `ca-app-pub-3940256099942544/1033173712`
- Rewarded: `ca-app-pub-3940256099942544/5224354917`

Remarques:
- Ce scaffold implémente CameraX, ML Kit OCR, PdfBox-Android, AdMob et une architecture MVVM basique.
- L'éditeur d'image (contraste / B/W / crop) est prévu comme extension — dis-moi si tu veux que je l'ajoute.
 - L'éditeur d'image (contraste / B/W / recadrage via uCrop) est inclus.

Remarques de build:
- Le projet utilise `kotlin-kapt` pour Glide. Gradle configuration se trouve dans `app/build.gradle`.
- `FileProvider` est configuré dans `AndroidManifest.xml` et `res/xml/file_paths.xml` pour partager les PDFs générés.

Pour builder depuis la ligne de commande:

```powershell
cd "C:\TEST\scannerPdfOcr"
.\gradlew clean assembleDebug
```

Avant publication, remplace les IDs de test AdMob par tes propres ad units et App ID dans `AndroidManifest.xml`.
