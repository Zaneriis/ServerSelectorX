# ItemsAdder Selector Item Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Forcer l'item du server selector à être l'item ItemsAdder `mcicons:icon_ender_chest`, en dur dans le code.

**Architecture:** Une constante d'id ItemsAdder + un helper centralisé dans `Main` construisent l'item via l'API `CustomStack`. `OnJoinListener` donne cet item au join ; `SelectorOpenListener` détecte l'item en main via `CustomStack.byItemStack`. Le champ `item:` du yml devient ignoré pour le selector.

**Tech Stack:** Java 11, Spigot API 1.13.2, Maven (shade plugin), API ItemsAdder (`com.github.LoneDev6:api-itemsadder` via JitPack, scope `provided`).

## Global Constraints

- Java source/target : **11** — pas de syntaxe plus récente.
- La dépendance `api-itemsadder` est **`provided`** : ni bundlée ni relocalisée par le shade plugin (comme `spigot-api` / `placeholderapi`).
- Id ItemsAdder hardcodé : **`mcicons:icon_ender_chest`** (verbatim).
- Pas de nouveau champ de configuration.
- Build de vérification : `mvn clean package` (aucune suite de tests dans le projet — la vérification se fait au build + manuellement).
- Commits fréquents, un par tâche.

---

### Task 1: Ajouter la dépendance ItemsAdder au build

**Files:**
- Modify: `pom.xml` (bloc `<repositories>` ~124-136, bloc `<dependencies>` ~87-122)

**Interfaces:**
- Consumes: rien.
- Produces: rend les classes `dev.lone.itemsadder.api.CustomStack` disponibles à la compilation (scope `provided`) pour les tâches 2 et 4.

> **Note (résolu) :** la dépendance ItemsAdder est publiée via **JitPack** (`com.github.LoneDev6:api-itemsadder`). Le repo historique `repo.devs.beer` est injoignable (DNS HS) ; JitPack est le canal officiel. La version **`3.6.1`** est déjà présente dans le cache `~/.m2` local (utilisée par le projet celestia), donc le build fonctionne hors-ligne. Le package Java reste `dev.lone.itemsadder.api`.

- [ ] **Step 1: Ajouter le repository**

Dans `pom.xml`, à l'intérieur de `<repositories>`, ajouter :

```xml
		<repository>
			<id>jitpack.io</id>
			<url>https://jitpack.io</url>
		</repository>
```

- [ ] **Step 2: Ajouter la dépendance**

Dans `pom.xml`, à l'intérieur de `<dependencies>`, ajouter :

```xml
		<dependency>
			<groupId>com.github.LoneDev6</groupId>
			<artifactId>api-itemsadder</artifactId>
			<version>3.6.1</version>
			<scope>provided</scope>
		</dependency>
```

- [ ] **Step 3: Vérifier que la dépendance se résout**

Run: `mvn -q -o dependency:resolve` (mode hors-ligne, la 3.6.1 est en cache local)
Expected: BUILD SUCCESS, pas d'erreur "Could not resolve dependencies" pour `com.github.LoneDev6:api-itemsadder`.
Si le cache est absent, retirer `-o` pour laisser Maven télécharger depuis JitPack.

- [ ] **Step 5: Commit**

```bash
git add pom.xml
git commit -m "build: add ItemsAdder API (JitPack) as provided dependency"
```

---

### Task 2: Ajouter la constante et le helper dans Main

**Files:**
- Modify: `src/xyz/derkades/serverselectorx/Main.java` (imports en tête ; ajouter membres dans la classe, ex. après `getItemFromMaterialString` ~148)

**Interfaces:**
- Consumes: `dev.lone.itemsadder.api.CustomStack` (Task 1).
- Produces:
  - `public static final String SELECTOR_IA_ID` — vaut `"mcicons:icon_ender_chest"`. Utilisé par Task 4.
  - `static ItemBuilder getSelectorItem(Player player)` — renvoie un `ItemBuilder` de l'item ItemsAdder, ou un `ItemBuilder(Material.COMPASS)` en fallback. Utilisé par Task 3.

- [ ] **Step 1: Ajouter l'import**

Dans `Main.java`, ajouter avec les autres imports :

```java
import dev.lone.itemsadder.api.CustomStack;
```

- [ ] **Step 2: Ajouter la constante**

Dans la classe `Main`, près des autres champs statiques (après la ligne `public static PingManager getPingManager() { ... }` ~29) :

```java
	public static final String SELECTOR_IA_ID = "mcicons:icon_ender_chest";
```

- [ ] **Step 3: Ajouter le helper**

Dans la classe `Main`, après la méthode `getItemFromMaterialString` (~148) :

```java
	/**
	 * Construit l'item ItemsAdder du selector. Renvoie un fallback COMPASS (avec un warning
	 * dans les logs) si ItemsAdder est absent ou si l'item n'est pas encore chargé.
	 */
	static ItemBuilder getSelectorItem(final Player player) {
		final CustomStack stack = CustomStack.getInstance(SELECTOR_IA_ID);
		if (stack == null) {
			Main.getPlugin().getLogger().warning(
					"ItemsAdder item '" + SELECTOR_IA_ID + "' introuvable (ItemsAdder absent ou pas encore charge). Fallback sur COMPASS.");
			return new ItemBuilder(Material.COMPASS);
		}
		return new ItemBuilder(stack.getItemStack());
	}
```

- [ ] **Step 4: Compiler pour vérifier**

Run: `mvn -q compile`
Expected: BUILD SUCCESS (le paramètre `player` est volontairement non utilisé pour le moment ; ça compile sans erreur).

- [ ] **Step 5: Commit**

```bash
git add src/xyz/derkades/serverselectorx/Main.java
git commit -m "feat: add hardcoded ItemsAdder selector item helper"
```

---

### Task 3: Donner l'item ItemsAdder au join

**Files:**
- Modify: `src/xyz/derkades/serverselectorx/OnJoinListener.java:30-33`

**Interfaces:**
- Consumes: `Main.getSelectorItem(Player)` (Task 2).
- Produces: rien.

- [ ] **Step 1: Remplacer la construction de l'item**

Dans `OnJoinListener.onJoin`, remplacer :

```java
			final String materialString = config.getString("item");

			final ItemBuilder builder = Main.getItemFromMaterialString(player, materialString)
					.coloredName(config.getString("item-name", "error"));
```

par :

```java
			final ItemBuilder builder = Main.getSelectorItem(player)
					.coloredName(config.getString("item-name", "error"));
```

Le reste de la méthode (`coloredLore`, `inv-slot`, slot `-1` auto, `only-in-worlds`) est inchangé.

- [ ] **Step 2: Compiler pour vérifier**

Run: `mvn -q compile`
Expected: BUILD SUCCESS. Plus aucune référence à la variable supprimée `materialString` dans le fichier.

- [ ] **Step 3: Commit**

```bash
git add src/xyz/derkades/serverselectorx/OnJoinListener.java
git commit -m "feat: give hardcoded ItemsAdder item on join"
```

---

### Task 4: Détecter l'item ItemsAdder pour ouvrir le menu

**Files:**
- Modify: `src/xyz/derkades/serverselectorx/SelectorOpenListener.java` (imports ; bloc de matching ~40-57)

**Interfaces:**
- Consumes: `Main.SELECTOR_IA_ID` (Task 2), `dev.lone.itemsadder.api.CustomStack` (Task 1).
- Produces: rien.

- [ ] **Step 1: Ajouter l'import**

Dans `SelectorOpenListener.java`, ajouter avec les autres imports :

```java
import dev.lone.itemsadder.api.CustomStack;
```

- [ ] **Step 2: Remplacer la logique de matching par item**

Dans `onInteract`, remplacer le bloc actuel :

```java
			if (!config.isString("item")) {
				continue;
			}

			if (config.getString("item").equalsIgnoreCase("NONE")){
				continue;
			}

			final String string = config.getString("item");
			Material material = Material.getMaterial(string);

			if (material == null){
				material = Material.STONE;
			}

			if (player.getInventory().getItemInMainHand().getType() != material){
				continue;
			}
```

par :

```java
			if (config.isString("item") && config.getString("item").equalsIgnoreCase("NONE")) {
				// Opt-out : ce menu ne s'ouvre pas via l'item du selector
				continue;
			}

			final CustomStack held = CustomStack.byItemStack(player.getInventory().getItemInMainHand());
			if (held == null || !held.getNamespacedID().equals(Main.SELECTOR_IA_ID)) {
				continue;
			}
```

- [ ] **Step 3: Retirer l'import devenu inutile**

Si `org.bukkit.Material` n'est plus référencé dans le fichier après le Step 2, supprimer la ligne `import org.bukkit.Material;`.

Run: `grep -n "Material" src/xyz/derkades/serverselectorx/SelectorOpenListener.java`
Expected: aucune occurrence restante → supprimer l'import. S'il en reste, laisser l'import.

- [ ] **Step 4: Compiler pour vérifier**

Run: `mvn -q compile`
Expected: BUILD SUCCESS, aucun warning d'import inutilisé.

- [ ] **Step 5: Commit**

```bash
git add src/xyz/derkades/serverselectorx/SelectorOpenListener.java
git commit -m "feat: detect ItemsAdder item in hand to open selector menu"
```

---

### Task 5: Déclarer ItemsAdder en soft-dépendance

**Files:**
- Modify: `resources/plugin.yml:7`

**Interfaces:**
- Consumes: rien.
- Produces: rien.

- [ ] **Step 1: Ajouter ItemsAdder au softdepend**

Dans `resources/plugin.yml`, remplacer :

```yaml
softdepend: [PlaceholderAPI]
```

par :

```yaml
softdepend: [PlaceholderAPI, ItemsAdder]
```

- [ ] **Step 2: Commit**

```bash
git add resources/plugin.yml
git commit -m "build: add ItemsAdder as soft dependency in plugin.yml"
```

---

### Task 6: Documenter le champ item ignoré dans default.yml

**Files:**
- Modify: `resources/default.yml:14-15`

**Interfaces:**
- Consumes: rien.
- Produces: rien.

- [ ] **Step 1: Mettre à jour le commentaire**

Dans `resources/default.yml`, remplacer :

```yaml
# The name of the server selector item.
item: COMPASS
```

par :

```yaml
# IGNORE pour l'item du selector : l'item est force en dur a l'item ItemsAdder
# "mcicons:icon_ender_chest" (voir Main.SELECTOR_IA_ID). Ce champ est conserve
# uniquement pour l'opt-out : mettre "NONE" pour empecher ce menu de s'ouvrir via l'item.
item: COMPASS
```

- [ ] **Step 2: Commit**

```bash
git add resources/default.yml
git commit -m "docs: note that item field is ignored for the selector item"
```

---

### Task 7: Build final et vérification

**Files:** aucun (vérification).

- [ ] **Step 1: Build complet**

Run: `mvn clean package`
Expected: BUILD SUCCESS, un jar produit dans `target/`.

- [ ] **Step 2: Vérification manuelle (hors CI — à faire par l'utilisateur)**

Sur un serveur Spigot 1.13+ avec ItemsAdder + le pack `mcicons` chargé :
1. Au join : le joueur reçoit l'item `mcicons:icon_ender_chest` avec le nom/lore configurés dans `default.yml`.
2. Clic droit en tenant cet item → le menu s'ouvre.
3. Tenir un autre item → le menu ne s'ouvre pas.
4. Serveur sans ItemsAdder : warning `ItemsAdder item ... introuvable` dans les logs, fallback COMPASS, pas de crash.

---

## Notes hors périmètre (rappel du spec)

- `Main.getItemFromMaterialString` : inchangé (items affichés *dans* les menus).
- `Stats.java` : inchangé (déjà null-safe ligne 28).
- Aucun support d'item IA configurable : id en dur, par décision.
