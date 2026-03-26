# 🚀 Spin-cEV Calculator

[English version](#-spin-cev-calculator-en) | [Version Française](#-spin-cev-calculator-fr)

---

## 🚀 Spin-cEV Calculator (EN)

Compute your **cEV (Chip Expected Value)** for Poker Spins, filtered by buy-in and position. An easy-to-use tool designed for the poker community.

### 🌟 Why this project?
The main goal is to provide a **simple, one-click solution** for players who want to track their actual performance (cEV) and monetary results without the complexity of a full commercial tracker. I focused on getting you your results as fast as possible with minimal configuration.

### Supported Rooms:
* **Betclic**: ✅ Stable.
* **Winamax**: ✅ Stable (Supports both Nitro and Classic formats).
* **iPoker Network**: ✅ Stable (iPoker.fr, etc.).
* **Unibet**: Added recently, should work but not thoroughly tested yet

> **Don't see your room?**
> If you would like to have another room supported, feel free to **submit a Pull Request** or **open an issue** with the specific Hand History (HH) format specifications.

---

### 💾 Installation & Download

Download the version corresponding to your system from the [Releases](https://github.com/m-anthony/cEV/releases) page.

#### Windows
* **Installer (.msi)**: Recommended for a standard installation with a desktop shortcut.
* **Portable Version (.zip)**: If you prefer not to install anything, simply unzip and run `Spin-cEV-calculator.exe`.

#### macOS
* **Disk Image (.dmg)**: Recommended. Open the image and drag the app to your Applications folder.
* **Portable Version (.zip)**: Unzip and run the application directly.

> **Note:** The application includes its own runtime environment. You do not need to install Java or any other software to run it.

---

### 🛡️ Security and Trust

#### A Note on Skepticism
In the poker world, being skeptical about the software you install is a vital habit for your security. Protecting your accounts and data is a priority. This is why this project is fully open-source and follows a transparent publication process.

#### Unsigned Binaries & Antivirus
Because this is an independent open-source project, the binaries are not "signed" with expensive certificates from Microsoft or Apple. This may trigger a warning on the first launch:

* **Windows SmartScreen**: Click *"More info"* then *"Run anyway"*.
* **macOS Gatekeeper**: If the app is blocked, go to `System Settings` > `Privacy & Security` and click *"Open Anyway"* at the bottom of the page.

#### Transparency & Verification
To ensure total integrity, the installation files are not created on a personal computer. They are **automatically built by GitHub's secure servers** directly from the public source code.

For advanced users, you can verify this by checking the **SHA-256 checksums** provided in the release notes. You can compare these hashes with the logs in the [GitHub Artifact Attestations](https://github.com/m-anthony/cEV/attestations) tab to guarantee that the binary you downloaded is exactly the one produced by the code you see here, without any human interference.

---

### ⚠️ Warning: Data Retention (Winamax)

**Important note for Winamax players:**
Winamax automatically deletes your hand history files from your computer after **60 days**.
* Once deleted, this tool can no longer process those tournaments.
* **Advice**: To keep a long-term history of your results, I strongly recommend making **manual backups** of your Winamax hand history folders to another location.

---

### ⚙️ How It Works

The tool analyzes your files to extract **Hero's** chip results. In the event of an "All-in" before the river, the actual result is replaced by the theoretical equity (**Expected Value**).

#### Performance
The engine is highly optimized, using a mix of pre-calculated equity tables and parallel processing. It can process **20,000+ Spins** in less than 5 seconds on a standard modern computer with SSD storage.

---

### 📊 Why does my cEV differ from other tools?

If you notice discrepancies between this tool and others (like PokerTracker 4 or Hand2Note), keep in mind:

- **3-Way All-ins**: This tool uses Monte Carlo simulations for 3-way pots. Slight variations are normal compared to tools using different algorithms.
- **Side Pot Logic**: I follow the same logic as PokerTracker 4: equity-based cEV is only calculated if every active player is all-in on the same street. If action continues in a side pot, the actual chip result is used. Other tools may have different rules.
- **Specific Hand Issues**: If you find a massive discrepancy for a specific hand, please open an issue and attach the hand history file so I can investigate.

---

### 🛠️ Development (How to Build)

If you are a developer and want to compile the project yourself:

1.  **Clone**: `git clone --recursive https://github.com/m-anthony/cEV` (required for the equity calculation submodule).
2.  **Build**: `./gradlew build`.
3.  **Package for your OS**:
    * Run `./gradlew :cev-ui:packageDistributionForCurrentOS` to create the installer/package for your current platform.
    * Or use `./gradlew :cev-ui:createDistributable` for a local runnable version.

---
---

## 🚀 Spin-cEV Calculator (FR)

Calculez votre **cEV (Chip Expected Value)** pour les Spins, filtré par buy-in et par position. Un outil simple d'utilisation conçu pour la communauté poker.

### 🌟 Pourquoi ce projet ?
L'objectif principal est de fournir une **solution simple en un clic** pour les joueurs qui souhaitent suivre leurs performances réelles (cEV) et leurs résultats financiers sans la complexité d'un tracker commercial complet. Je me suis concentré sur l'obtention de vos résultats le plus rapidement possible avec une configuration minimale.

### Rooms supportées :
* **Betclic** : ✅ Stable.
* **Winamax** : ✅ Stable (Supporte les formats Nitro et Classique).
* **iPoker Network** : ✅ Stable (iPoker.fr, etc.).
* **Unibet**: Ajouté récemment, cela devrait fonctionner, mais pas encore testé en détail


> **Votre room n'est pas listée ?**
> Si vous souhaitez qu'une autre room soit supportée, n'hésitez pas à **soumettre une Pull Request** ou à **ouvrir une issue** avec les spécifications du format de Hand History (HH).

---

### 💾 Installation & Téléchargement

Téléchargez la version correspondant à votre système sur la page des [Releases](https://github.com/m-anthony/cEV/releases).

#### Windows
* **Installateur (.msi)** : Recommandé pour une installation standard avec un raccourci bureau.
* **Version Portable (.zip)** : Si vous préférez ne rien installer, décompressez simplement et lancez `Spin-cEV-calculator.exe`.

#### macOS
* **Image Disque (.dmg)** : Recommandé. Ouvrez l'image et faites glisser l'application dans votre dossier Applications.
* **Version Portable (.zip)** : Décompressez et lancez l'application directement.

> **Note :** L'application inclut son propre environnement d'exécution. Vous n'avez pas besoin d'installer Java ou tout autre logiciel pour la lancer.

---

### 🛡️ Sécurité et Confiance

#### Un mot sur le scepticisme
Dans le monde du poker, être sceptique vis-à-vis des logiciels que vous installez est une habitude vitale pour votre sécurité. La protection de vos comptes et de vos données est une priorité. C'est pourquoi ce projet est entièrement open-source et suit un processus de publication transparent.

#### Binaires non signés & Antivirus
Comme il s'agit d'un projet open-source indépendant, les binaires ne sont pas "signés" avec des certificats coûteux de Microsoft ou Apple. Cela peut déclencher un avertissement lors du premier lancement :

* **Windows SmartScreen** : Cliquez sur *"Informations complémentaires"* puis sur *"Exécuter quand même"*.
* **macOS Gatekeeper** : Si l'application est bloquée, allez dans `Réglages Système` > `Confidentialité et sécurité` et cliquez sur *"Ouvrir quand même"* en bas de la page.

#### Transparence & Vérification
Pour garantir une intégrité totale, les fichiers d'installation ne sont pas créés sur un ordinateur personnel. Ils sont **automatiquement compilés par les serveurs sécurisés de GitHub** directement à partir du code source public.

Pour les utilisateurs avancés, vous pouvez vérifier cela en consultant les **checksums SHA-256** fournis dans les notes de mise à jour. Vous pouvez comparer ces empreintes avec les journaux de l'onglet [GitHub Artifact Attestations](https://github.com/m-anthony/cEV/attestations) pour garantir que le binaire que vous avez téléchargé est exactement celui produit par le code que vous voyez ici, sans aucune intervention humaine.

---

### ⚠️ Attention : Conservation des données (Winamax)

**Note importante pour les joueurs Winamax :**
Winamax supprime automatiquement vos fichiers d'historique de mains de votre ordinateur après **60 jours**.
* Une fois supprimées, cet outil ne peut plus traiter ces tournois.
* **Conseil** : Pour conserver un historique de vos résultats à long terme, je vous recommande fortement de faire des **sauvegardes manuelles** de vos dossiers d'historique de mains Winamax vers un autre emplacement.

---

### ⚙️ Fonctionnement

L'outil analyse vos fichiers pour extraire les résultats en jetons de **Hero**. En cas de "All-in" avant la river, le résultat réel en jetons est remplacé par l'équité théorique (**Expected Value**).

#### Performance
Le moteur est hautement optimisé, utilisant un mélange de tables d'équité pré-calculées et de traitement parallèle. Il peut traiter plus de **20 000 Spins** en moins de 5 secondes sur un ordinateur moderne standard équipé d'un stockage SSD.

---

### 📊 Pourquoi mon cEV diffère-t-il d'autres outils ?

Si vous remarquez des écarts entre cet outil et d'autres (comme PokerTracker 4 ou Hand2Note), gardez à l'esprit que :

- **All-ins en 3-Way** : Cet outil utilise des simulations Monte Carlo pour les pots en 3-way. De légères variations sont normales par rapport aux outils utilisant des algorithmes différents.
- **Logique de Side Pot** : Je suis la même logique que PokerTracker 4 : le cEV basé sur l'équité n'est calculé que si tous les joueurs actifs sont à tapis sur la même street. Si l'action continue dans un side pot, le résultat réel en jetons est utilisé. D'autres outils peuvent avoir des règles différentes.
- **Problèmes sur des mains spécifiques** : Si vous constatez un écart massif sur une main spécifique, merci d'ouvrir une "issue" et de joindre le fichier d'historique de la main pour que je puisse enquêter.

---

### 🛠️ Développement (Comment compiler)

Si vous êtes un développeur et souhaitez compiler le projet vous-même :

1.  **Cloner** : `git clone --recursive https://github.com/m-anthony/cEV` (requis pour le sous-module de calcul d'équité).
2.  **Compiler** : `./gradlew build`.
3.  **Packager pour votre OS** :
    * Lancez `./gradlew :cev-ui:packageDistributionForCurrentOS` pour créer l'installateur/paquet pour votre plateforme actuelle.
    * Ou utilisez `./gradlew :cev-ui:createDistributable` pour une version exécutable localement.