# 🌾 FarmHelper

Un mod Minecraft Fabric qui affiche un overlay HUD en temps réel avec les statistiques de farming, pêche et minage — directement parsé depuis le chat du serveur.

---

## ✨ Fonctionnalités

- 📊 **Overlay HUD** affiché en jeu avec les stats de ta session
- 🐟 **Lac** — suivi des poissons, perles, XP
- 🌱 **Ferme** — suivi des cultures, orbes, XP
- 💎 **Mine** — suivi des blocs, gemmes, XP
- 💰 Suivi des ventes et de l'argent gagné
- 🎟️ Suivi des tickets (Stat, Trait, Unique)
- 🗝️ Suivi des clés d'événement par monde
- ⏱️ Durée de session par monde (pause automatique quand tu changes de monde)
- 📈 Affichage du taux par seconde pour chaque ressource
- 🖱️ Overlay **déplaçable** et **redimensionnable** via une interface de config
- 💾 Configuration sauvegardée automatiquement

---

## 🎮 Contrôles

| Touche | Action |
|--------|--------|
| `B` | Ouvrir la configuration de l'overlay |
| `N` | Afficher / masquer l'overlay |

---

## 📦 Installation

1. Installe [Fabric Loader](https://fabricmc.net/use/installer/)
2. Installe [Fabric API](https://modrinth.com/mod/fabric-api)
3. Télécharge le `.jar` de FarmHelper depuis les [Releases](../../releases)
4. Place le fichier dans ton dossier `.minecraft/mods/`
5. Lance Minecraft

---

## 🔧 Compilation depuis les sources

```bash
git clone https://github.com/lulunoel/FarmHelper.git
cd FarmHelper
./gradlew build
```

Le `.jar` compilé sera dans `build/libs/`.

---

## 🤝 Contribuer

Les contributions sont les bienvenues ! Tu peux :

- 🐛 **Signaler un bug** → [Ouvrir une Issue](../../issues/new?template=bug_report.md)
- 💡 **Proposer une fonctionnalité** → [Ouvrir une Issue](../../issues/new?template=feature_request.md)
- 🔧 **Soumettre une modification** → Fork le projet, fais tes changements, ouvre une Pull Request

### Règles de contribution

- Respecte le style de code existant
- Décris clairement ce que fait ta PR
- Une PR = une fonctionnalité ou un fix

---

## 📋 Compatibilité

| Minecraft | Fabric Loader | Fabric API |
|-----------|--------------|------------|
| 1.21.x    | ≥ 0.15       | ✅ Requis  |

---

## 📄 Licence

Ce projet est sous licence **FarmHelper Community License** — voir le fichier [LICENSE](LICENSE) pour les détails.

En résumé : tu peux utiliser, modifier et partager ce projet librement, mais tu ne peux pas te l'approprier ni le redistribuer comme étant ton propre travail.

---

## 👤 Auteur

Créé et maintenu par **lulunoel** — toute réutilisation doit créditer l'auteur original.
