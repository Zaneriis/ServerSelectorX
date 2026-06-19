# ItemsAdder Selector Item — Plan d'implémentation

> **Statut : implémenté.** Ce plan reflète l'approche **configurable** (`ia-item` + fallback `item:`),
> qui a remplacé la première version *hardcodée* (id en dur, `item:` ignoré).

**Goal:** Permettre que l'item du server selector soit un item ItemsAdder configurable par menu
via le champ `ia-item`, avec fallback automatique sur le champ vanilla `item:`.

**Architecture:** Deux helpers centralisés dans `Main` (`getSelectorItem` pour construire,
`isSelectorItem` pour détecter) lisent `ia-item` puis retombent sur `item:`. `OnJoinListener`
donne l'item au join via `getSelectorItem` ; `SelectorOpenListener` détecte l'item en main via
`isSelectorItem`.

**Tech Stack:** Java 11, Spigot API 1.13.2, Maven (shade plugin), API ItemsAdder
(`com.github.LoneDev6:api-itemsadder` via JitPack, scope `provided`).

## Global Constraints

- Java source/target : **11** — pas de syntaxe plus récente.
- La dépendance `api-itemsadder` est **`provided`** : ni bundlée ni relocalisée par le shade plugin
  (comme `spigot-api` / `placeholderapi`).
- Le champ `ia-item` est **optionnel** ; absence ou item non résolu ⇒ fallback sur `item:`.
- Build de vérification : `mvn clean package` (aucune suite de tests — vérif au build + manuelle).

---

### Task 1: Ajouter la dépendance ItemsAdder au build ✅

**Files:** `pom.xml` (`<repositories>`, `<dependencies>`)

- [x] Ajouter le repository JitPack (`https://jitpack.io`).
- [x] Ajouter la dépendance `com.github.LoneDev6:api-itemsadder:3.6.1` en scope `provided`.
- [x] Commit : `build: add ItemsAdder API (JitPack) as provided dependency`.

> Note : `repo.devs.beer` est injoignable (DNS HS) ; JitPack est le canal officiel.
> La `3.6.1` est en cache `~/.m2`, le build fonctionne hors-ligne. Package : `dev.lone.itemsadder.api`.

---

### Task 2: Ajouter les helpers `getSelectorItem` / `isSelectorItem` dans Main ✅

**Files:** `src/xyz/derkades/serverselectorx/Main.java`

- [x] Imports : `dev.lone.itemsadder.api.CustomStack`, `org.bukkit.inventory.ItemStack`.
- [x] `static ItemBuilder getSelectorItem(Player, FileConfiguration)` — `ia-item` via
  `CustomStack.getInstance` si résolvable, sinon warning + fallback `getItemFromMaterialString(player, item)`.
- [x] `static boolean isSelectorItem(ItemStack, FileConfiguration)` — miroir : match par
  `getNamespacedID()` si `ia-item` résout, sinon comparaison Material vanilla (avec opt-out `NONE`).
- [x] `mvn compile` → BUILD SUCCESS.

---

### Task 3: Donner l'item au join ✅

**Files:** `src/xyz/derkades/serverselectorx/OnJoinListener.java`

- [x] Remplacer `Main.getItemFromMaterialString(player, config.getString("item"))` par
  `Main.getSelectorItem(player, config)`. Le reste (`coloredName`, `coloredLore`, `inv-slot`,
  slot `-1` auto, `only-in-worlds`) inchangé.

---

### Task 4: Détecter l'item pour ouvrir le menu ✅

**Files:** `src/xyz/derkades/serverselectorx/SelectorOpenListener.java`

- [x] Remplacer tout le bloc de matching `Material` par :
  `if (!Main.isSelectorItem(player.getInventory().getItemInMainHand(), config)) continue;`
- [x] Supprimer l'import `org.bukkit.Material` devenu inutile.

---

### Task 5: Déclarer ItemsAdder en soft-dépendance ✅

**Files:** `resources/plugin.yml`

- [x] `softdepend: [PlaceholderAPI, ItemsAdder]`.

---

### Task 6: Documenter `item` (fallback) et `ia-item` dans default.yml ✅

**Files:** `resources/default.yml`

- [x] Commentaires décrivant `item` comme fallback vanilla et `ia-item` comme item ItemsAdder optionnel.

---

### Task 7: Build final et vérification ✅

- [x] `mvn clean package` → **BUILD SUCCESS**, jar produit dans `target/`.
- [x] Vérifié : aucune classe `dev/lone`/ItemsAdder dans le jar shadé (scope `provided` respecté).
- [ ] **Vérification manuelle (hors CI — à faire par l'utilisateur)** sur un serveur Spigot 1.13+ :
  1. Avec ItemsAdder + pack `mcicons` : au join, item `mcicons:icon_ender_chest` ; clic droit → menu s'ouvre.
  2. Autre item en main → le menu ne s'ouvre pas.
  3. Sans ItemsAdder (ou `ia-item` retiré) : fallback sur `item: COMPASS`, pas de crash.

---

## Notes hors périmètre

- `Main.getItemFromMaterialString` : inchangé (items affichés *dans* les menus).
- `Stats.java` : inchangé (déjà null-safe).
